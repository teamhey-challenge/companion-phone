package ai.hey.phone.feature.talk

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.MemoryTokenStore
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
        assertEquals(listOf("turn.start", "text", "turn.end"), f.map { Regex("\"type\":\"([^\"]+)\"").find(it)!!.groupValues[1] })
        assertTrue(f[0].contains("\"trigger\":\"ptt\""))
        assertTrue(f[1].contains("안녕"))
        assertEquals("hello", Regex("\"type\":\"([^\"]+)\"").find(helloFrame())!!.groupValues[1])
    }

    @Test fun textIsCutToTheProtocolLimit() {
        var sent = ""
        script = { t, on -> sent = t; on(TalkEvent.End("ok")) }
        val m = model()
        m.say("가".repeat(TalkModel.MAX_TEXT_CHARS + 40)); settle()
        assertEquals(TalkModel.MAX_TEXT_CHARS, sent.length)
    }

    @Test fun leavingTheTabReleasesTheTransport() {
        var released = 0
        val m = TalkModel(ApiClient(server.url("/api").toString(), MemoryTokenStore("t")), scope, object : TalkTransport {
            override suspend fun send(text: String, onEvent: (TalkEvent) -> Unit) {}
            override fun release() { released++ }
        }, audio, onUnauthorized = {})
        m.release()
        assertEquals(1, released)
    }

    // ---- one socket for the whole visit ----

    /** Server side of /v1/talk: records every text frame it gets and answers each turn.end with a reply. */
    private class FakeTalkServer {
        val opened = java.util.concurrent.atomic.AtomicInteger()
        val frames = java.util.concurrent.CopyOnWriteArrayList<String>()
        val closed = java.util.concurrent.CountDownLatch(1)
        val listener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) { opened.incrementAndGet() }
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                frames += text
                val type = Regex("\"type\":\"([^\"]+)\"").find(text)!!.groupValues[1]
                if (type == "turn.end") webSocket.send("""{"type":"reply.end","turnId":"x","text":"응답 ${frames.count { it.contains("turn.end") }}"}""")
            }
            override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) { closed.countDown() }
        }
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond()) { check(System.currentTimeMillis() < end) { "timeout waiting for $what" }; Thread.sleep(10) }
    }

    private fun talkClient(): TalkClient {
        val api = ApiClient(server.url("/api").toString(), MemoryTokenStore("t"))
        return TalkClient(api)
    }

    @Test fun twoTurnsReuseOneSocketWithOneHello() {
        val fake = FakeTalkServer()
        val tickets = java.util.concurrent.atomic.AtomicInteger()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when {
                request.path!!.endsWith("/v1/phone-sessions") -> { tickets.incrementAndGet(); MockResponse().setBody("""{"wsTicket":"tk"}""") }
                request.path!!.contains("/v1/talk") -> MockResponse().withWebSocketUpgrade(fake.listener)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = talkClient()
        val got = java.util.concurrent.CopyOnWriteArrayList<TalkEvent>()
        runBlocking { client.send("첫째", got::add) }
        waitFor("first reply") { got.any { it is TalkEvent.End } }
        runBlocking { client.send("둘째", got::add) }
        waitFor("second reply") { got.count { it is TalkEvent.End } == 2 }
        assertEquals(1, fake.opened.get())
        assertEquals("one ticket for the whole visit", 1, tickets.get())
        val types = fake.frames.map { Regex("\"type\":\"([^\"]+)\"").find(it)!!.groupValues[1] }
        assertEquals(listOf("hello", "turn.start", "text", "turn.end", "turn.start", "text", "turn.end"), types)
        assertEquals(listOf("응답 1", "응답 2"), got.filterIsInstance<TalkEvent.End>().map { it.text })
        client.release()
        assertTrue("leaving the tab closes the socket", fake.closed.await(5, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test fun afterTheServerClosesTheSocketTheNextTurnReconnects() {
        val fake = FakeTalkServer()
        val tickets = java.util.concurrent.atomic.AtomicInteger()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when {
                request.path!!.endsWith("/v1/phone-sessions") -> { tickets.incrementAndGet(); MockResponse().setBody("""{"wsTicket":"tk"}""") }
                request.path!!.contains("/v1/talk") -> MockResponse().withWebSocketUpgrade(object : okhttp3.WebSocketListener() {
                    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) { fake.listener.onOpen(webSocket, response) }
                    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                        fake.listener.onMessage(webSocket, text)
                        if (text.contains("turn.end") && fake.opened.get() == 1) webSocket.close(1001, "going away")
                    }
                    override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
                })
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = talkClient()
        val got = java.util.concurrent.CopyOnWriteArrayList<TalkEvent>()
        runBlocking { client.send("첫째", got::add) }
        waitFor("first reply") { got.any { it is TalkEvent.End } }
        Thread.sleep(300) // let the server's close arrive
        runBlocking { client.send("둘째", got::add) }
        waitFor("second reply") { got.count { it is TalkEvent.End } == 2 }
        assertEquals(2, fake.opened.get())
        assertEquals(2, tickets.get())
        client.release()
    }
}
