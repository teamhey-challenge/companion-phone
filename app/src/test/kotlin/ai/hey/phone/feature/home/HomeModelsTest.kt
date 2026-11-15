package ai.hey.phone.feature.home

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.Diary
import ai.hey.phone.data.DiaryEntry
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
import org.junit.Before
import org.junit.Test

class HomeModelsTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private var diaryCalls = 0
    private val handlers = mutableMapOf<String, (RecordedRequest) -> MockResponse>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                handlers["${request.method} ${request.requestUrl!!.encodedPath}"]?.invoke(request) ?: MockResponse().setResponseCode(404)
        }
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        handlers["GET /api/v1/diary"] = { diaryCalls++; MockResponse().setBody("""{"date":"d","entries":[],"summary":"일기"}""") }
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun settle() { runBlocking { scope.coroutineContext[Job]!!.children.toList().joinAll() } }

    private fun today(settings: String, consents: String?): TodayModel {
        handlers["GET /api/v1/settings"] = { MockResponse().setBody(settings) }
        if (consents != null) handlers["GET /api/v1/consents"] = { MockResponse().setBody(consents) }
        return TodayModel(ApiClient(server.url("/api").toString(), MemoryTokenStore("t")), scope, { "2026-11-15" }, {}).also { it.load(); settle() }
    }

    private val on = """{"proactive":"normal","memoryEnabled":true,"speech":"banmal","displayName":"민수"}"""
    private val off = """{"proactive":"normal","memoryEnabled":false,"speech":"banmal","displayName":"민수"}"""
    private val memoryGranted = """{"consents":[{"type":"record","version":"v","granted":true},{"type":"memory","version":"v","granted":true}]}"""
    private val memoryNever = """{"consents":[{"type":"record","version":"v","granted":true}]}"""
    private val memoryWithdrawn = """{"consents":[{"type":"memory","version":"v","granted":true},{"type":"memory","version":"v","granted":false}]}"""
    private val recordWithdrawn = """{"consents":[{"type":"memory","version":"v","granted":true},{"type":"record","version":"v","granted":false}]}"""

    @Test fun memorySettingOffShowsGuidanceInsteadOfTheDiary() {
        val t = today(off, memoryGranted)
        assertEquals(TodayUi.MemoryOff, t.ui)
        assertEquals("민수", t.name)
        assertEquals(0, diaryCalls)
    }

    @Test fun memoryConsentNotGrantedShowsGuidanceEvenWithTheSettingOn() {
        assertEquals(TodayUi.MemoryOff, today(on, memoryNever).ui)
        assertEquals(TodayUi.MemoryOff, today(on, memoryWithdrawn).ui)
        assertEquals(TodayUi.MemoryOff, today(on, recordWithdrawn).ui)
        assertEquals(0, diaryCalls)
    }

    @Test fun grantedMemoryShowsTheDiary() {
        assertEquals(TodayUi.Day(null, emptyList(), "일기"), today(on, memoryGranted).ui)
    }

    @Test fun aFailedConsentReadNeverHidesTheDiary() {
        assertEquals(TodayUi.Day(null, emptyList(), "일기"), today(on, null).ui)
    }

    @Test fun todayPicksNewestTripLineAndHidesItFromTheList() {
        val d = Diary(
            "2026-11-15",
            listOf(
                DiaryEntry("2026-11-15T01:00:00Z", "오전에 나갔어", "trip_start"),
                DiaryEntry("2026-11-15T03:00:00Z", "첫 도착!", "trip_done"),
                DiaryEntry("2026-11-15T09:00:00Z", "집 도착 축하해!", "trip_done"),
            ),
            null,
        )
        val ui = d.toToday() as TodayUi.Day
        assertEquals("집 도착 축하해!", ui.tripLine)
        assertEquals(listOf("오전에 나갔어"), ui.entries.map { it.text })
        assertNull(ui.summary)
    }

    @Test fun emptyDiaryIsQuietAndBlankSummaryCounts() {
        assertEquals(TodayUi.Quiet, Diary("d", emptyList(), "  ").toToday())
        assertEquals(TodayUi.Day(null, emptyList(), "일기"), Diary("d", emptyList(), "일기").toToday())
    }
}
