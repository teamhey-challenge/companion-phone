package ai.hey.phone.feature.login

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.GoogleIdTokenSource
import ai.hey.phone.data.GoogleSignInResult
import ai.hey.phone.data.MemoryTokenStore
import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginModelTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokenStore
    private lateinit var scope: CoroutineScope
    private val handlers = mutableMapOf<String, (RecordedRequest) -> MockResponse>()
    private val done = mutableListOf<LoginOutcome>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                handlers["${request.method} ${request.requestUrl!!.encodedPath}"]?.invoke(request) ?: MockResponse().setResponseCode(404)
        }
        server.start()
        tokens = MemoryTokenStore()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun settle() { val job = scope.coroutineContext[Job]!!; runBlocking { job.children.toList().joinAll() } }

    private var noAccount = 0

    private fun model(configured: Boolean = true, picked: GoogleSignInResult = GoogleSignInResult.Token("idt")) =
        LoginModel(ApiClient(server.url("/api").toString(), tokens), scope, configured, { picked }, { noAccount++ }) { done += it }

    private fun login(body: String, consents: String) {
        handlers["POST /api/v1/auth/google"] = { MockResponse().setBody(body) }
        handlers["GET /api/v1/consents"] = { MockResponse().setBody(consents) }
    }

    private val noConsents = """{"consents":[]}"""
    private val recordConsent = """{"consents":[{"type":"record","version":"v","granted":true}]}"""

    @Test fun unconfiguredBuildShowsTheNoticeAndNeverCallsTheServer() {
        val m = model(configured = false)
        m.signIn(); settle()
        assertEquals(LoginError.Unconfigured, m.error)
        assertEquals(0, server.requestCount)
        assertTrue(done.isEmpty())
    }

    @Test fun newAccountStoresTheTokenAndAsksForConsent() {
        login("""{"phoneToken":"p1","accountId":"a","displayName":"민수","linkedCar":false,"isNew":true}""", noConsents)
        val m = model(); m.signIn(); settle()
        assertEquals("p1", tokens.load())
        assertEquals(listOf(LoginOutcome(needsConsent = true, linkedCar = false)), done)
        assertNull(m.error); assertFalse(m.busy)
    }

    @Test fun returningCarAccountSkipsConsent() {
        login("""{"phoneToken":"p2","accountId":"a","displayName":"민수","linkedCar":true,"isNew":false}""", recordConsent)
        val m = model(); m.signIn(); settle()
        assertEquals("p2", tokens.load())
        assertEquals(listOf(LoginOutcome(needsConsent = false, linkedCar = true)), done)
    }

    @Test fun serverWithoutGoogleClientIdMapsTo503Notice() {
        handlers["POST /api/v1/auth/google"] = { MockResponse().setResponseCode(503).setBody("""{"code":"google_login_unconfigured"}""") }
        val m = model(); m.signIn(); settle()
        assertEquals(LoginError.Unconfigured, m.error)
        assertNull(tokens.load()); assertTrue(done.isEmpty())
    }

    @Test fun rateLimitAndNetworkErrorsAreDistinct() {
        handlers["POST /api/v1/auth/google"] = { MockResponse().setResponseCode(429) }
        val m = model(); m.signIn(); settle()
        assertEquals(LoginError.Rate, m.error)
        server.shutdown()
        val n = model(); n.signIn(); settle()
        assertEquals(LoginError.Network, n.error)
    }

    @Test fun cancelledPickerIsNotAnError() {
        val m = model(picked = GoogleSignInResult.Cancelled); m.signIn(); settle()
        assertNull(m.error); assertFalse(m.busy); assertEquals(0, server.requestCount)
    }

    @Test fun noGoogleAccountOnThePhoneOpensTheSystemAddAccountScreen() {
        val m = model(picked = GoogleSignInResult.NoAccount); m.signIn(); settle()
        assertEquals(1, noAccount); assertNull(m.error); assertFalse(m.busy)
    }

    @Test fun googleVerifyFailureAndOutageShareOneNotice() {
        handlers["POST /api/v1/auth/google"] = { MockResponse().setResponseCode(401).setBody("""{"error":"google_verify_failed","code":"google_verify_failed"}""") }
        val a = model(); a.signIn(); settle()
        assertEquals(LoginError.Verify, a.error)
        handlers["POST /api/v1/auth/google"] = { MockResponse().setResponseCode(503).setBody("""{"error":"google_verify_failed","code":"google_verify_failed"}""") }
        val b = model(); b.signIn(); settle()
        assertEquals(LoginError.Verify, b.error)
    }

    @Test fun rateLimitedCodeMapsToTheWaitNotice() {
        handlers["POST /api/v1/auth/google"] = { MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited","code":"rate_limited"}""") }
        val m = model(); m.signIn(); settle()
        assertEquals(LoginError.Rate, m.error)
    }

    @Test fun consentModelPostsRecordExternalAiAndMemoryOnlyWhenTicked() {
        tokens.save("p")
        val posted = mutableListOf<String>()
        handlers["POST /api/v1/consents"] = { r -> posted += r.body.readUtf8(); MockResponse().setBody(noConsents) }
        var finished = 0
        val c = ConsentModel(ApiClient(server.url("/api").toString(), tokens), scope) { finished++ }
        c.start(); settle()
        assertEquals(ConsentError.Required, c.error); assertTrue(posted.isEmpty())
        c.consent = true; c.start(); settle()
        assertEquals(1, finished)
        assertEquals(listOf("record", "external_ai"), posted.map { Regex("\"type\":\"(\\w+)\"").find(it)!!.groupValues[1] })
    }

    @Test fun consentDeclinedThenSignInAgainEndsOnTheConnectScreenNotToday() {
        // 1st sign-in: new account, no consent yet -> consent screen; the user declines (nav: signedOut -> start).
        login("""{"phoneToken":"p1","accountId":"ph_a","displayName":"민수","linkedCar":false,"isNew":true}""", noConsents)
        val first = model(); first.signIn(); settle()
        assertEquals(LoginOutcome(needsConsent = true, linkedCar = false), done.last())
        tokens.clear() // decline = app.signedOut(): the token is gone, the start screen shows again
        assertNull(tokens.load())

        // 2nd sign-in, still no car and no remembered code: consent again, then (after accepting) the entry branch is the connect screen.
        login("""{"phoneToken":"p2","accountId":"ph_a","displayName":"민수","linkedCar":false,"isNew":false}""", noConsents)
        val second = model(); second.signIn(); settle()
        assertEquals(LoginOutcome(needsConsent = true, linkedCar = false), done.last())
        val consent = ConsentModel(ApiClient(server.url("/api").toString(), tokens), scope) { }
        handlers["POST /api/v1/consents"] = { MockResponse().setBody(recordConsent) }
        consent.consent = true; consent.start(); settle()
        assertEquals(ai.hey.phone.feature.login.Entry.Scan, decideEntry(linked = false, pendingCode = null))
        assertEquals("p2", tokens.load())
    }

    @Test fun logoutClearsTheTokenAndTheGoogleState() {
        tokens.save("p")
        var cleared = 0
        val google = object : GoogleIdTokenSource {
            override val configured = true
            override suspend fun fetch(context: Context) = GoogleSignInResult.Cancelled
            override suspend fun clear() { cleared++ }
        }
        tokens.clear()
        runBlocking { google.clear() }
        assertNull(tokens.load()); assertEquals(1, cleared)
    }
}
