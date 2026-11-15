package ai.hey.phone.feature.friends

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

class FriendsModelTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokenStore
    private lateinit var scope: CoroutineScope
    private var unauthorized = 0
    private val handlers = mutableMapOf<String, (RecordedRequest) -> MockResponse>()
    private val requests = mutableListOf<String>()

    private val messagesJson = """{"messages":[
        {"id":"m1","direction":"in","friendId":"f1","friendName":"민수","text":"어디야?","at":"2026-11-15T10:00:00Z"},
        {"id":"m2","direction":"out","friendId":"f1","friendName":"민수","text":"곧 가","at":"2026-11-15T10:01:00Z"},
        {"id":"m3","direction":"in","friendId":"f2","friendName":"지은","text":"밥 먹자","at":"2026-11-15T11:00:00Z"}]}"""
    private val friendsJson = """[{"id":"f1","name":"민수","since":"2026-11-10","shareArrival":false},{"id":"f2","name":"지은","shareArrival":true}]"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.requestUrl!!.encodedPath}"
                return handlers["${request.method} ${request.requestUrl!!.encodedPath}"]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        tokens = MemoryTokenStore().also { it.save("t") }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        handlers["GET /api/v1/friends"] = { MockResponse().setBody(friendsJson) }
        handlers["GET /api/v1/messages"] = { MockResponse().setBody(messagesJson) }
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun settle() { runBlocking { scope.coroutineContext[Job]!!.children.toList().joinAll() } }

    private fun model() = FriendsModel(ApiClient(server.url("/api").toString(), tokens), scope, onUnauthorized = { unauthorized++ })

    @Test fun inboxKeepsOnlyReceivedNewestFirst() {
        val m = model(); m.load(); settle()
        assertEquals(listOf("m3", "m1"), m.inbox!!.map { it.id })
        assertEquals(listOf("f1", "f2"), (m.friends as FriendsUi.Items).friends.map { it.id })
    }

    @Test fun reportHidesAtOnceAndKeepsItHiddenWhenTheServerAgrees() {
        handlers["POST /api/v1/messages/m3/report"] = { MockResponse().setResponseCode(204) }
        val m = model(); m.load(); settle()
        m.report("m3"); settle()
        assertEquals(listOf("m1"), m.inbox!!.map { it.id })
        assertEquals(FriendsNotice.Reported, m.notice)
        assertTrue("POST /api/v1/messages/m3/report" in requests)
    }

    @Test fun failedReportBringsTheMessageBack() {
        handlers["POST /api/v1/messages/m3/report"] = { MockResponse().setResponseCode(500) }
        val m = model(); m.load(); settle()
        m.report("m3"); settle()
        assertEquals(listOf("m3", "m1"), m.inbox!!.map { it.id })
        assertEquals(FriendsNotice.Generic, m.notice)
    }

    @Test fun blockedMessageShowsTheFilterNotice() {
        handlers["POST /api/v1/messages"] = { MockResponse().setResponseCode(422).setBody("""{"error":"message_blocked","code":"message_blocked"}""") }
        val m = model(); m.load(); settle()
        m.send("f1", "나쁜 말"); settle()
        assertEquals(FriendsNotice.Blocked, m.notice)
        assertEquals(false, m.busy)
    }

    @Test fun sendPostsRecipientAndText() {
        var body = ""
        handlers["POST /api/v1/messages"] = { body = it.body.readUtf8(); MockResponse().setResponseCode(201).setBody("""{"id":"x","at":"t"}""") }
        val m = model(); m.load(); settle()
        m.send("f1", "  안녕  "); settle()
        assertEquals("""{"toFriendId":"f1","text":"안녕"}""", body)
        assertEquals(FriendsNotice.Sent, m.notice)
    }

    @Test fun tooLongOrBlankMessagesAreNotSent() {
        val m = model(); m.load(); settle()
        requests.clear()
        m.send("f1", "   "); m.send("f1", "가".repeat(121)); settle()
        assertTrue(requests.none { it.startsWith("POST") })
    }

    @Test fun shareArrivalToggleIsOptimisticAndRollsBackOnFailure() {
        handlers["PATCH /api/v1/friends/f1"] = { MockResponse().setResponseCode(500) }
        val m = model(); m.load(); settle()
        m.setShareArrival("f1", true); settle()
        assertEquals(false, (m.friends as FriendsUi.Items).friends.first { it.id == "f1" }.shareArrival)
    }

    @Test fun shareArrivalSendsTheFlag() {
        var body = ""
        handlers["PATCH /api/v1/friends/f1"] = { body = it.body.readUtf8(); MockResponse().setResponseCode(200).setBody("{}") }
        val m = model(); m.load(); settle()
        m.setShareArrival("f1", true); settle()
        assertEquals("""{"shareArrival":true}""", body)
        assertEquals(true, (m.friends as FriendsUi.Items).friends.first { it.id == "f1" }.shareArrival)
    }

    @Test fun addByLinkSendsAFriendRequest() {
        var body = ""
        handlers["POST /api/v1/friends/requests"] = { r -> body = r.body.readUtf8(); MockResponse().setResponseCode(201).setBody("""{"id":"r1","to":{"name":"새친구"},"createdAt":"2026-11-15T13:00:00Z"}""") }
        val m = model(); m.load(); settle()
        m.add("https://hey.lastmile.ltd/f/k7m2qx"); settle()
        assertEquals("""{"code":"K7M2QX"}""".lowercase(), body.lowercase())
        assertEquals(FriendsNotice.RequestSent, m.notice)
    }

    @Test fun invitedCodeIsSettledAfterASentRequest() {
        handlers["POST /api/v1/friends/requests"] = { MockResponse().setResponseCode(201).setBody("""{"id":"r1","to":{"name":"새친구"},"createdAt":"2026-11-15T13:00:00Z"}""") }
        var settled = 0
        val m = model(); m.load(); settle()
        m.add("K7M2QX") { settled++ }; settle()
        assertEquals(1, settled)
    }

    @Test fun invitedCodeIsSettledOnATerminal4xx() {
        handlers["POST /api/v1/friends/requests"] = { MockResponse().setResponseCode(409).setBody("""{"error":"already_friends"}""") }
        var settled = 0
        val m = model(); m.load(); settle()
        m.add("K7M2QX") { settled++ }; settle()
        assertEquals(1, settled)
        assertEquals(FriendsNotice.AlreadyFriends, m.notice)
    }

    @Test fun invitedCodeStaysWhenTheServerIsDown() {
        handlers["POST /api/v1/friends/requests"] = { MockResponse().setResponseCode(503) }
        var settled = 0
        val m = model(); m.load(); settle()
        m.add("K7M2QX") { settled++ }; settle()
        assertEquals(0, settled)
    }

    @Test fun incomingRequestAcceptReloadsAndShowsTheName() {
        handlers["GET /api/v1/friends/requests"] = { MockResponse().setBody("""{"incoming":[{"id":"r2","from":{"id":"u2","name":"서연"},"createdAt":"2026-11-15T13:00:00Z"}],"outgoing":[]}""") }
        handlers["POST /api/v1/friends/requests/r2/accept"] = { MockResponse().setBody("""{"friend":{"id":"f7","name":"서연"}}""") }
        val m = model(); m.load(); settle()
        assertEquals(listOf("r2"), m.requests.map { it.id })
        m.acceptRequest("r2"); settle()
        assertEquals("서연", m.acceptedName)
        assertTrue(m.requests.isEmpty())
    }

    @Test fun addWithBadCodeNeverCallsTheServer() {
        val m = model(); m.load(); settle()
        requests.clear()
        m.add("https://hey.lastmile.ltd/p/ABCD2345"); settle()
        assertEquals(FriendsNotice.AddFailed, m.notice)
        assertTrue(requests.isEmpty())
    }

    @Test fun friendLimitIsReportedForTheRequest() {
        handlers["POST /api/v1/friends/requests"] = { MockResponse().setResponseCode(409).setBody("""{"error":"friend_limit"}""") }
        val m = model(); m.load(); settle()
        m.add("K7M2QX"); settle()
        assertEquals(FriendsNotice.LimitReached, m.notice)
    }

    @Test fun deadTokenCallsOnUnauthorizedOnce() {
        handlers["GET /api/v1/friends"] = { MockResponse().setResponseCode(401) }
        handlers["GET /api/v1/messages"] = { MockResponse().setResponseCode(401) }
        val m = model(); m.load(); settle()
        assertEquals(1, unauthorized)
    }

    @Test fun unreachableFriendsListShowsFailed() {
        handlers["GET /api/v1/friends"] = { MockResponse().setResponseCode(500) }
        val m = model(); m.load(); settle()
        assertEquals(FriendsUi.Failed, m.friends)
    }

    @Test fun friendCodeParsing() {
        assertEquals("K7M2QX", parseFriendCode(" k7m2qx "))
        assertEquals("K7M2QX", parseFriendCode("https://hey.lastmile.ltd/f/K7M2QX"))
        assertEquals("K7M2QX", parseFriendCode("https://x.example/f/K7M2QX?utm=1"))
        assertNull(parseFriendCode("https://hey.lastmile.ltd/p/ABCD2345"))
        assertNull(parseFriendCode(""))
        assertNull(parseFriendCode("두 단어 아님"))
    }
}
