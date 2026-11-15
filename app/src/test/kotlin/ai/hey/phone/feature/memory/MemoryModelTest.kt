package ai.hey.phone.feature.memory

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.MemoryTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class MemoryModelTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokenStore
    private lateinit var scope: CoroutineScope
    private var unauthorized = 0
    private var unpaired = 0
    private val handlers = mutableMapOf<String, (RecordedRequest) -> MockResponse>()

    private val memoryJson = """{"items":[
        {"id":"a","kind":"fact","text":"비 오는 날","createdAt":"2026-11-13T00:00:00Z"},
        {"id":"b","kind":"summary","text":"퇴근길","createdAt":"2026-11-12T00:00:00Z"}],"nextCursor":null}"""
    private val settingsJson = """{"proactive":"normal","memoryEnabled":true,"speech":"banmal","nickname":null}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                handlers["${request.method} ${request.requestUrl!!.encodedPath}"]?.invoke(request) ?: MockResponse().setResponseCode(404)
        }
        server.start()
        tokens = MemoryTokenStore().also { it.save("t") }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        handlers["GET /api/v1/memory"] = { MockResponse().setBody(memoryJson) }
        handlers["GET /api/v1/settings"] = { MockResponse().setBody(settingsJson) }
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    /** Waits for every launched model call (they run on the IO dispatcher inside ApiClient). */
    private fun CoroutineScope.advanceUntilIdle() { val job = this.coroutineContext[Job]!!; runBlocking { job.children.toList().joinAll() } }
    private fun settle() = scope.advanceUntilIdle()

    private fun model() = MemoryModel(
        ApiClient(server.url("/api").toString(), tokens),
        scope,
        onUnauthorized = { unauthorized++; tokens.clear() },
        onUnpaired = { unpaired++ },
    )

    private fun rowIds(m: MemoryModel) = (m.list as MemoryListUi.Items).rows.map { it.id }

    @Test fun loadShowsRowsWithKindsAndSpeech() {
        val m = model(); m.load(); settle()
        assertEquals(listOf("a", "b"), rowIds(m))
        assertEquals(listOf(MemoryKindUi.Fact, MemoryKindUi.Summary), (m.list as MemoryListUi.Items).rows.map { it.kind })
        assertEquals(SpeechUi.Banmal, m.speech)
    }

    @Test fun memorySavingOffShowsGuidanceAndNeverReadsTheList() {
        var listCalls = 0
        handlers["GET /api/v1/settings"] = { MockResponse().setBody("""{"proactive":"normal","memoryEnabled":false,"speech":"jondaetmal","nickname":null}""") }
        handlers["GET /api/v1/memory"] = { listCalls++; MockResponse().setBody(memoryJson) }
        val m = model(); m.load(); settle()
        assertEquals(MemoryListUi.Off, m.list)
        assertEquals(SpeechUi.Jondaetmal, m.speech)
        assertEquals(0, listCalls)
    }

    @Test fun todayCardShowsGuidanceInsteadOfTheDiaryWhenMemoryIsOff() {
        var diaryCalls = 0
        handlers["GET /api/v1/settings"] = { MockResponse().setBody("""{"proactive":"normal","memoryEnabled":false,"speech":"banmal","displayName":"민수"}""") }
        handlers["GET /api/v1/diary"] = { diaryCalls++; MockResponse().setBody("""{"date":"d","entries":[],"summary":"일기"}""") }
        val t = ai.hey.phone.feature.home.TodayModel(ApiClient(server.url("/api").toString(), tokens), scope, { "2026-11-15" }, { unauthorized++ })
        t.load(); settle()
        assertEquals(ai.hey.phone.feature.home.TodayUi.MemoryOff, t.ui)
        assertEquals("민수", t.name)
        assertEquals(0, diaryCalls)
    }

    @Test fun emptyServerListIsEmptyState() {
        handlers["GET /api/v1/memory"] = { MockResponse().setBody("""{"items":[],"nextCursor":null}""") }
        val m = model(); m.load(); settle()
        assertEquals(MemoryListUi.Empty, m.list)
    }

    @Test fun loadFailureShowsErrorAndRetryRecovers() {
        handlers["GET /api/v1/memory"] = { MockResponse().setResponseCode(500) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        assertEquals(MemoryListUi.Failed, m.list)
        handlers["GET /api/v1/memory"] = { MockResponse().setBody(memoryJson) }
        m.load(); scope.advanceUntilIdle()
        assertEquals(listOf("a", "b"), rowIds(m))
    }

    @Test fun deleteRemovesTheRowAfterTheServerAgreed() {
        handlers["DELETE /api/v1/memory/a"] = { MockResponse().setResponseCode(204) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        m.delete("a"); scope.advanceUntilIdle()
        assertEquals(listOf("b"), rowIds(m))
        assertNull(m.notice)
    }

    @Test fun failedDeleteKeepsTheRowAndShowsNotice() {
        handlers["DELETE /api/v1/memory/a"] = { MockResponse().setResponseCode(500) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        m.delete("a"); scope.advanceUntilIdle()
        assertEquals(listOf("a", "b"), rowIds(m))
        assertEquals(NoticeUi.DeleteFailed, m.notice)
        assertTrue((m.list as MemoryListUi.Items).busyIds.isEmpty())
    }

    @Test fun deletingTheLastRowShowsEmpty() {
        handlers["DELETE /api/v1/memory/a"] = { MockResponse().setResponseCode(204) }
        handlers["DELETE /api/v1/memory/b"] = { MockResponse().setResponseCode(204) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        m.delete("a"); scope.advanceUntilIdle(); m.delete("b"); scope.advanceUntilIdle()
        assertEquals(MemoryListUi.Empty, m.list)
    }

    @Test fun deleteAllEmptiesTheList() {
        handlers["DELETE /api/v1/memory"] = { MockResponse().setResponseCode(204) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        m.deleteAll(); scope.advanceUntilIdle()
        assertEquals(MemoryListUi.Empty, m.list)
    }

    @Test fun speechChangeIsSentAndFailureRestoresThePreviousValue() {
        var body = ""
        handlers["PATCH /api/v1/settings"] = { body = it.body.readUtf8(); MockResponse().setBody(settingsJson.replace("banmal", "jondaetmal")) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        m.chooseSpeech(SpeechUi.Jondaetmal); scope.advanceUntilIdle()
        assertEquals("""{"speech":"jondaetmal"}""", body)
        assertEquals(SpeechUi.Jondaetmal, m.speech)

        handlers["PATCH /api/v1/settings"] = { MockResponse().setResponseCode(500) }
        m.chooseSpeech(SpeechUi.Banmal); scope.advanceUntilIdle()
        assertEquals(SpeechUi.Jondaetmal, m.speech)
        assertEquals(NoticeUi.SpeechFailed, m.notice)
    }

    @Test fun unauthorizedLeavesOnceAndClearsTheToken() {
        handlers["GET /api/v1/memory"] = { MockResponse().setResponseCode(401) }
        handlers["GET /api/v1/settings"] = { MockResponse().setResponseCode(401) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        assertEquals(1, unauthorized)
        assertNull(tokens.load())
    }

    @Test fun unpairSuccessLeaves() {
        handlers["DELETE /api/v1/pairings/current"] = { MockResponse().setResponseCode(204) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        m.unpair(); scope.advanceUntilIdle()
        assertEquals(1, unpaired)
        assertNull(tokens.load())
    }

    @Test fun unpairFailureKeepsTheTokenAndStays() {
        handlers["DELETE /api/v1/pairings/current"] = { MockResponse().setResponseCode(500) }
        val m = model(); m.load(); scope.advanceUntilIdle()
        m.unpair(); scope.advanceUntilIdle()
        assertEquals(0, unpaired)
        assertEquals("t", tokens.load())
        assertEquals(NoticeUi.UnpairFailed, m.notice)
    }
}
