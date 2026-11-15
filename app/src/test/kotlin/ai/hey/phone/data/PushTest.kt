package ai.hey.phone.data

import ai.hey.phone.feature.friends.arrivedText
import ai.hey.phone.notify.Notifier
import ai.hey.phone.notify.NoticeText
import ai.hey.phone.notify.noticeFor
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class PushTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokenStore
    private lateinit var api: ApiClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = MemoryTokenStore()
        api = ApiClient(server.url("/api").toString(), tokens)
    }

    @After fun tearDown() = server.shutdown()

    // ---- token storage ----

    @Test fun googleLoginStoresTheTokenAndSendsNoAuth() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"phoneToken":"ptok","accountId":"a1","displayName":"민수","linkedCar":true,"isNew":false,"x":1}"""))
        val r = api.googleLogin("id-token")
        assertEquals("ptok", r.phoneToken)
        assertTrue(r.linkedCar)
        assertFalse(r.isNew)
        assertEquals("ptok", tokens.load())
        val req = server.takeRequest()
        assertEquals("/api/v1/auth/google", req.path)
        assertNull(req.getHeader("Authorization"))
        assertEquals("""{"idToken":"id-token"}""", req.body.readUtf8())
    }

    @Test fun failedGoogleLoginKeepsNoTokenAndReadsTheCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"google_login_unconfigured"}"""))
        try { api.googleLogin("x"); org.junit.Assert.fail() } catch (e: ApiException) {
            assertEquals(503, e.code)
            assertEquals("google_login_unconfigured", e.reason)
        }
        assertNull(tokens.load())
    }

    @Test fun claimWithoutATokenNeverReachesTheNetwork() = runBlocking {
        try { api.claim("ABCD2345", record = true, memory = false); org.junit.Assert.fail() } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun claimOfAnotherAccountsCarSurfacesTheCodeAndKeepsTheToken() = runBlocking {
        tokens.save("google-tok")
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"car_linked_to_other_account"}"""))
        try { api.claim("ABCD2345", record = true, memory = false); org.junit.Assert.fail() } catch (e: ApiException) {
            assertEquals("car_linked_to_other_account", e.reason)
        }
        assertEquals("google-tok", tokens.load())
    }

    @Test fun tokenStoreSaveLoadClear() {
        val s = MemoryTokenStore()
        assertNull(s.load())
        s.save("a"); assertEquals("a", s.load())
        s.save("b"); assertEquals("b", s.load())
        s.clear(); assertNull(s.load())
    }

    @Test fun phoneSessionAndSettingsPatchCarryTheBearer() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setBody("""{"sessionId":"s","wsTicket":"tick","expiresInSec":60}"""))
        assertEquals("tick", api.phoneSession().wsTicket)
        assertEquals("Bearer t", server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("""{"proactive":"normal","memoryEnabled":true,"speech":"banmal","displayName":"민수"}"""))
        assertEquals("민수", api.patchSettings(displayName = "민수").displayName)
        assertEquals("""{"displayName":"민수"}""", server.takeRequest().body.readUtf8())
    }

    // ---- frames ----

    @Test fun parsesTheFourFrames() {
        assertEquals(PushFrame.Message("m1", "민수", "안녕"), PushFrame.parse("""{"type":"friend_message","id":"m1","fromName":"민수","text":"안녕"}"""))
        assertEquals(PushFrame.Arrived("m2", "민수"), PushFrame.parse("""{"type":"friend_arrived","id":"m2","fromName":"민수","place":"집"}"""))
        assertEquals(PushFrame.Arrived(null, "민수"), PushFrame.parse("""{"type":"friend_arrived","fromName":"민수"}"""))
        assertEquals(PushFrame.TripDone("도착 축하해!", "2026-11-15"), PushFrame.parse("""{"type":"trip_done","text":"도착 축하해!","date":"2026-11-15"}"""))
        assertEquals(PushFrame.DiaryReady("2026-11-15"), PushFrame.parse("""{"type":"diary_ready","date":"2026-11-15"}"""))
    }

    @Test fun ignoresUnknownAndBrokenFrames() {
        assertNull(PushFrame.parse("""{"type":"settings"}"""))
        assertNull(PushFrame.parse("not json"))
        assertNull(PushFrame.parse("""{"type":"friend_message","id":"m"}"""))
    }

    // ---- gate / poller ----

    @Test fun gateNotifiesOnceAndNeverWhileForeground() {
        var fg = false
        val gate = PushGate(MemorySeenStore()) { fg }
        assertFalse("arrivals never notify", gate.shouldNotify(PushFrame.Arrived("a", "민수")))
        assertTrue("trip end notifies", gate.shouldNotify(PushFrame.TripDone("집 도착 축하해!", "d")))
        assertFalse("diary_ready never notifies: the trip already did", gate.shouldNotify(PushFrame.DiaryReady("d")))
        val f = PushFrame.Message("m1", "민수", "안녕")
        assertTrue(gate.shouldNotify(f))
        assertFalse("same id again (stream then poll)", gate.shouldNotify(f))
        fg = true
        assertFalse("visible app does not notify", gate.shouldNotify(PushFrame.Message("m2", "민수", "또")))
        fg = false
        assertFalse("m2 was consumed while visible", gate.shouldNotify(PushFrame.Message("m2", "민수", "또")))
    }

    @Test fun pollerEmitsReceivedMessagesAndDiaryAndAdvancesSince() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setBody("""{"messages":[
            {"id":"b","direction":"in","friendName":"지은","text":"둘째","at":"2026-11-15T11:00:00Z"},
            {"id":"o","direction":"out","friendName":"지은","text":"내가 보냄","at":"2026-11-15T11:30:00Z"},
            {"id":"a","direction":"in","friendName":"민수","text":"첫째","at":"2026-11-15T10:00:00Z"}]}"""))
        server.enqueue(MockResponse().setBody("""{"date":"2026-11-15","entries":[{"at":"2026-11-15T09:00:00Z","text":"집 도착 축하해!","kind":"trip_done"}],"summary":"오늘은 비가 왔다."}"""))
        val seen = MemorySeenStore().also { it.since = "2026-11-15T00:00:00Z"; it.tripSince = "2026-11-15T00:00:00Z" }
        val got = mutableListOf<PushFrame>()
        Poller(api, seen, { got += it }, { "2026-11-15" }, { "now" }).poll()
        assertEquals(
            listOf(
                PushFrame.Message("a", "민수", "첫째"), PushFrame.Message("b", "지은", "둘째"),
                PushFrame.TripDone("집 도착 축하해!", "2026-11-15", "2026-11-15T09:00:00Z"), PushFrame.DiaryReady("2026-11-15"),
            ),
            got,
        )
        assertEquals("2026-11-15T11:30:00Z", seen.since)
        assertTrue(server.takeRequest().path!!.contains("since=2026-11-15T00%3A00%3A00Z"))
    }

    @Test fun pollerFirstRunStartsFromNowAndDiaryFailureKeepsMessages() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setBody("""{"messages":[]}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        val seen = MemorySeenStore()
        val got = mutableListOf<PushFrame>()
        Poller(api, seen, { got += it }, { "2026-11-15" }, { "2026-11-15T12:00:00Z" }).poll()
        assertEquals("2026-11-15T12:00:00Z", seen.since)
        assertTrue(got.isEmpty())
    }

    // ---- notification text (D-52: friend message + trip-end diary only) ----

    @Test fun noticeTexts() {
        assertEquals("민수의 메시지", noticeFor(PushFrame.Message("1", "민수", "안녕"))!!.title)
        assertEquals("안녕", noticeFor(PushFrame.Message("1", "민수", "안녕"))!!.body)
        assertNull("arrivals never notify", noticeFor(PushFrame.Arrived("1", "민수")))
        assertEquals("도착 축하해!", noticeFor(PushFrame.TripDone("도착 축하해!", "d"))!!.body)
        assertNull("diary_ready never notifies", noticeFor(PushFrame.DiaryReady("d")))
    }


    @Test fun arrivalTextHasTheNameAndNoPlace() {
        assertEquals("민수가 도착했대", arrivedText("민수"))
        assertEquals("지은이가 도착했대", arrivedText("지은"))
        assertEquals("친구가 도착했대", arrivedText(""))
    }

    // ---- one trip end = one notification, however many frames and polls report it ----

    /** Every frame the server and the poll produce for the given trip ends, routed like the app does (gate, then the notice text). */
    private fun notices(vararg frames: PushFrame, gate: PushGate = PushGate(MemorySeenStore()) { false }) =
        frames.filter { gate.shouldNotify(it) }.mapNotNull { noticeFor(it) }

    @Test fun oneTripEndGivesOneNotificationEvenWithTheDiaryFrame() {
        val shown = notices(PushFrame.TripDone("집 도착 축하해!", "2026-11-15"), PushFrame.DiaryReady("2026-11-15"))
        assertEquals(listOf("집 도착 축하해!"), shown.map { it.body })
    }

    @Test fun twoTripsWithTheSameLineTheSameDayBothNotify() {
        val shown = notices(PushFrame.TripDone("오늘도 같이 달렸어", "2026-11-15"), PushFrame.TripDone("오늘도 같이 달렸어", "2026-11-15"))
        assertEquals(2, shown.size)
    }

    @Test fun aTripSeenOnTheStreamIsNotAnnouncedAgainByThePoll() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setBody("""{"messages":[]}"""))
        server.enqueue(MockResponse().setBody("""{"date":"2026-11-15","entries":[{"at":"2026-11-15T09:00:00Z","text":"집 도착 축하해!","kind":"trip_done"}],"summary":"오늘은 비가 왔다."}"""))
        val seen = MemorySeenStore().also { it.since = "2026-11-15T00:00:00Z" }
        val gate = PushGate(seen, nowIso = { "2026-11-15T09:00:03Z" }) { false }
        val shown = mutableListOf<NoticeText>()
        fun handle(f: PushFrame) { if (gate.shouldNotify(f)) noticeFor(f)?.let { shown += it } }
        handle(PushFrame.TripDone("집 도착 축하해!", "2026-11-15")) // the stream got it first
        Poller(api, seen, ::handle, { "2026-11-15" }, { "2026-11-15T09:15:00Z" }).poll()
        assertEquals(1, shown.size)
    }

    @Test fun theNextPollAnnouncesOnlyTheTripsItHasNotShown() = runBlocking {
        tokens.save("t")
        val twoTrips = """{"date":"2026-11-15","entries":[
            {"at":"2026-11-15T09:00:00Z","text":"오늘도 같이 달렸어","kind":"trip_done"},
            {"at":"2026-11-15T12:00:00Z","text":"오늘도 같이 달렸어","kind":"trip_done"}],"summary":null}"""
        server.enqueue(MockResponse().setBody("""{"messages":[]}"""))
        server.enqueue(MockResponse().setBody(twoTrips))
        server.enqueue(MockResponse().setBody("""{"messages":[]}"""))
        server.enqueue(MockResponse().setBody(twoTrips))
        val seen = MemorySeenStore().also { it.since = "2026-11-15T00:00:00Z"; it.tripSince = "2026-11-15T00:00:00Z" }
        val gate = PushGate(seen) { false }
        val shown = mutableListOf<NoticeText>()
        val poller = Poller(api, seen, { f -> if (gate.shouldNotify(f)) noticeFor(f)?.let { shown += it } }, { "2026-11-15" }, { "x" })
        poller.poll()
        assertEquals("two trips with one line are two notifications", 2, shown.size)
        poller.poll()
        assertEquals("a second poll of the same diary adds nothing", 2, shown.size)
    }
}
