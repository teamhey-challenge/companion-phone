package ai.hey.phone.feature.pair

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.MemoryTokenStore
import ai.hey.phone.data.SignOutNotice
import ai.hey.phone.feature.login.Entry
import ai.hey.phone.feature.login.decideEntry
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

class ConnectModelTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokenStore
    private lateinit var scope: CoroutineScope
    private val handlers = mutableMapOf<String, (RecordedRequest) -> MockResponse>()
    private val requests = mutableListOf<String>()
    private var claimed = 0
    private var carLinkLost = 0
    private val rejected = mutableListOf<SignOutNotice>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.requestUrl!!.encodedPath}"
                requests += key
                return handlers[key]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        tokens = MemoryTokenStore().also { it.save("google-tok") }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun settle() { val job = scope.coroutineContext[Job]!!; runBlocking { job.children.toList().joinAll() } }

    private fun api() = ApiClient(server.url("/api").toString(), tokens, onSessionRejected = { rejected += it }, onCarLinkRequired = { carLinkLost++ })

    private fun model() = ConnectModel(api(), scope) { claimed++ }

    private fun account() {
        handlers["GET /api/v1/consents"] = { MockResponse().setBody("""{"consents":[{"type":"memory","version":"v","granted":true}]}""") }
    }

    private fun claimOk() {
        handlers["POST /api/v1/pairings/ABCD2345/claim"] = { MockResponse().setBody("""{"phoneToken":"car-tok"}""") }
    }

    // ---- entry state machine: login -> consent -> branch ----

    @Test fun accountWithACarGoesToTodayAndDropsTheRememberedCode() =
        assertEquals(Entry.Today, decideEntry(linked = true, pendingCode = "ABCD2345"))

    @Test fun rememberedCodeIsClaimedWhenTheAccountHasNoCar() =
        assertEquals(Entry.Connect("ABCD2345"), decideEntry(linked = false, pendingCode = "ABCD2345"))

    @Test fun noCarAndNoCodeLandsOnTheConnectScreenNeverOnToday() =
        assertEquals(Entry.Scan, decideEntry(linked = false, pendingCode = null))

    @Test fun claimUsesTheGoogleTokenAndTheConsentChoice() {
        account(); claimOk()
        val m = model(); m.start("ABCD2345"); settle()
        assertEquals(1, claimed)
        assertEquals("car-tok", tokens.load())
    }

    // ---- car_link_required: the account has no car, any other call is refused ----

    @Test fun carLinkRequiredIsNotASignOut() = runBlocking {
        handlers["GET /api/v1/memory"] = { MockResponse().setResponseCode(403).setBody("""{"error":"car_link_required","code":"car_link_required"}""") }
        try { api().memory(); org.junit.Assert.fail() } catch (e: ApiException) {
            assertTrue(e.carLinkRequired); assertTrue(!e.unauthorized)
        }
        assertEquals(1, carLinkLost)
        assertTrue(rejected.isEmpty())
    }

    // ---- failure table: one test per server reason ----

    private fun failure(status: Int, reason: String): JoinError {
        account()
        handlers["POST /api/v1/pairings/ABCD2345/claim"] = { MockResponse().setResponseCode(status).setBody("""{"error":"$reason","code":"$reason"}""") }
        val m = model(); m.start("ABCD2345"); settle()
        return (m.ui as JoinUi.Failed).error
    }

    @Test fun expired() = assertEquals(JoinError.Expired, failure(410, "expired"))
    @Test fun unknown() = assertEquals(JoinError.Unknown, failure(404, "unknown"))
    @Test fun alreadyUsed() = assertEquals(JoinError.AlreadyUsed, failure(409, "already_used"))
    @Test fun carLinkedToOther() = assertEquals(JoinError.CarLinkedToOther, failure(409, "car_linked_to_other_account"))
    @Test fun googleLinkedToOtherCar() = assertEquals(JoinError.GoogleLinkedToOtherCar, failure(409, "google_linked_to_other_car"))
    @Test fun recordConsentRequired() = assertEquals(JoinError.RecordConsent, failure(400, "record_consent_required"))
    @Test fun googleLoginRequired() = assertEquals(JoinError.Verify, failure(403, "google_login_required"))
    @Test fun rateLimited() = assertEquals(JoinError.Rate, failure(429, "rate_limited"))

    @Test fun networkDown() {
        account(); claimOk()
        val m = model(); server.shutdown()
        m.start("ABCD2345"); settle()
        assertEquals(JoinUi.Failed(JoinError.Network), m.ui)
    }

    @Test fun errorsMapToTheRightNextAction() {
        assertEquals(JoinAction.Rescan, JoinError.Expired.action())
        assertEquals(JoinAction.Rescan, JoinError.Unknown.action())
        assertEquals(JoinAction.Rescan, JoinError.AlreadyUsed.action())
        assertEquals(JoinAction.Retry, JoinError.Network.action())
        assertEquals(JoinAction.Close, JoinError.CarLinkedToOther.action())
    }

    // ---- signed in elsewhere / expired session ----

    @Test fun signedInElsewhereIsReportedAsSuch() = runBlocking {
        handlers["GET /api/v1/account"] = { MockResponse().setResponseCode(401).setBody("""{"error":"signed_in_elsewhere","code":"signed_in_elsewhere"}""") }
        try { api().account(); org.junit.Assert.fail() } catch (e: ApiException) { assertTrue(e.signedInElsewhere) }
        assertEquals(listOf(SignOutNotice.SignedInElsewhere), rejected)
    }

    @Test fun anyOther401IsASessionExpiredNotice() = runBlocking {
        handlers["GET /api/v1/account"] = { MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized","code":"unauthorized"}""") }
        try { api().account(); org.junit.Assert.fail() } catch (e: ApiException) { assertTrue(!e.signedInElsewhere) }
        assertEquals(listOf(SignOutNotice.SessionExpired), rejected)
    }

    @Test fun aFailedClaimIsNotAnExpiredSession() {
        account()
        handlers["POST /api/v1/pairings/ABCD2345/claim"] = { MockResponse().setResponseCode(409).setBody("""{"error":"already_used"}""") }
        val m = model(); m.start("ABCD2345"); settle()
        assertTrue(rejected.isEmpty())
    }
}
