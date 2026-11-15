package ai.hey.phone.feature.talk

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.Diary
import ai.hey.phone.data.DiaryEntry
import ai.hey.phone.data.MemoryTokenStore
import ai.hey.phone.feature.home.TodayUi
import ai.hey.phone.feature.home.toToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private class FakeAudio : Audio {
    val log = mutableListOf<String>()
    override fun start(sampleRate: Int) { log += "start:$sampleRate" }
    override fun write(bytes: ByteArray) { log += "write:${bytes.size}" }
    override fun finish() { log += "finish" }
    override fun stop() { log += "stop" }
}

class TalkModelTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val audio = FakeAudio()
    private var script: (String, (TalkEvent) -> Unit) -> Unit = { _, _ -> }
    private var sendError: Exception? = null
    private var unauthorized = 0

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun settle() { runBlocking { scope.coroutineContext[Job]!!.children.toList().joinAll() } }

    private fun model(): TalkModel {
        val tokens = MemoryTokenStore("t")
        return TalkModel(
            ApiClient(server.url("/api").toString(), tokens), scope,
            { text, on -> sendError?.let { throw it }; script(text, on) }, audio, onUnauthorized = { unauthorized++ },
        ).also { it.needsConsentForTest(false) }
    }

    private fun TalkModel.needsConsentForTest(v: Boolean) {
        server.enqueue(MockResponse().setBody(if (v) """{"consents":[]}""" else """{"consents":[{"type":"record","version":"v","granted":true},{"type":"external_ai","version":"v","granted":true}]}"""))
        loadConsent(); settle()
    }

    @Test fun replyStreamsIntoOneBubbleAndPlaysThePcm() {
        script = { _, on ->
            on(TalkEvent.Start(true, 24_000)); on(TalkEvent.Delta("안녕")); on(TalkEvent.Audio(ByteArray(10)))
            on(TalkEvent.Delta(" 반가워")); on(TalkEvent.End("안녕 반가워"))
        }
        val m = model()
        m.say("  안녕 헤이리 "); settle()
        assertEquals(listOf(true to "안녕 헤이리", false to "안녕 반가워"), m.bubbles.map { it.mine to it.text })
        assertEquals(TalkStatus.Idle, m.status)
        assertEquals(listOf("stop", "start:24000", "write:10", "finish"), audio.log)
    }

    @Test fun textOnlyReplyDoesNotTouchTheAudio() {
        script = { _, on -> on(TalkEvent.Start(false, 24_000)); on(TalkEvent.End("글로만")) }
        val m = model(); audio.log.clear()
        m.say("hi"); settle()
        assertEquals(listOf("stop"), audio.log)
        assertEquals("글로만", m.bubbles.last().text)
    }

    @Test fun blankAndWhileThinkingAreIgnored() {
        var calls = 0
        script = { _, _ -> calls++ } // never answers: stays Thinking
        val m = model()
        m.say("   "); assertEquals(0, calls)
        m.say("하나"); m.say("둘")
        assertEquals(1, calls)
        assertEquals(TalkStatus.Thinking, m.status)
    }

    @Test fun networkFailureShowsTheNoticeAndFrees() {
        sendError = IOException("down")
        val m = model()
        m.say("hi"); settle()
        assertEquals(TalkNotice.Failed, m.notice)
        assertEquals(TalkStatus.Idle, m.status)
    }

    @Test fun serverRateLimitAndConsentErrors() {
        script = { _, on -> on(TalkEvent.Error("rate_limited")) }
        val m = model()
        m.say("hi"); settle()
        assertEquals(TalkNotice.Busy, m.notice)
        script = { _, on -> on(TalkEvent.Error("consent_required")) }
        m.say("again"); settle()
        assertEquals(true, m.needsConsent)
        assertEquals(TalkStatus.Idle, m.status)
    }

    @Test fun socketClosingBeforeTheEndIsAFailure() {
        script = { _, on -> on(TalkEvent.Closed(failure = true)) }
        val m = model()
        m.say("hi"); settle()
        assertEquals(TalkNotice.Failed, m.notice)
    }

    @Test fun missingConsentBlocksSayUntilBothAreGranted() {
        val m = model()
        m.needsConsentForTest(true)
        assertEquals(true, m.needsConsent)
        var sent = 0
        script = { _, _ -> sent++ }
        m.say("hi"); assertEquals(0, sent)
        server.enqueue(MockResponse().setBody("""{"consents":[]}"""))
        server.enqueue(MockResponse().setBody("""{"consents":[]}"""))
        m.grantConsent(); settle()
        assertEquals(false, m.needsConsent)
        val paths = generateSequence { server.takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS) }.map { it.path }.toList()
        assertTrue(paths.filter { it == "/api/v1/consents" }.size >= 3) // GET + 2 POST (+ earlier GETs)
    }

    @Test fun failedConsentKeepsTheGate() {
        val m = model()
        m.needsConsentForTest(true)
        server.enqueue(MockResponse().setResponseCode(500))
        m.grantConsent(); settle()
        assertEquals(true, m.needsConsent)
        assertEquals(TalkNotice.ConsentFailed, m.notice)
    }

    // ---- frames ----

    @Test fun parsesReplyFrames() {
        assertEquals(TalkEvent.Start(true, 24_000), parseTalkFrame("""{"type":"reply.start","turnId":"t","audio":{"format":"pcm_s16le","sampleRate":24000,"channels":1}}"""))
        assertEquals(TalkEvent.Start(false, 24_000), parseTalkFrame("""{"type":"reply.start","turnId":"t","audio":"none"}"""))
        assertEquals(TalkEvent.Delta("가"), parseTalkFrame("""{"type":"reply.delta","turnId":"t","text":"가"}"""))
        assertEquals(TalkEvent.End("끝"), parseTalkFrame("""{"type":"reply.end","turnId":"t","text":"끝"}"""))
        assertEquals(TalkEvent.Error("consent_required"), parseTalkFrame("""{"type":"error","code":"consent_required"}"""))
        assertNull(parseTalkFrame("""{"type":"settings"}"""))
    }

    @Test fun turnFramesOrder() {
        val f = turnFrames("t1", "안녕")
        assertEquals(listOf("hello", "turn.start", "text", "turn.end"), f.map { Regex("\"type\":\"([^\"]+)\"").find(it)!!.groupValues[1] })
        assertTrue(f[1].contains("\"trigger\":\"ptt\""))
        assertTrue(f[2].contains("안녕"))
    }

    // ---- today card ----

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
