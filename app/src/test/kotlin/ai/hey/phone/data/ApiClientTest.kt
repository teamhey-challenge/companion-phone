package ai.hey.phone.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokenStore
    private lateinit var api: ApiClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = MemoryTokenStore()
        api = ApiClient(server.url("/api").toString(), tokens)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun claimStoresTokenAndSendsConsentFlags() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"phoneToken":"tok123","character":{"name":"헤이리","bodyColor":1},"extra":1}"""))
        val r = api.claim("ABCD2345", record = true, memory = false)
        assertEquals("tok123", r.phoneToken)
        assertEquals("tok123", tokens.load())
        val req = server.takeRequest()
        assertEquals("/api/v1/pairings/ABCD2345/claim", req.path)
        assertNull(req.getHeader("Authorization"))
        assertEquals("""{"record":true,"memory":false}""", req.body.readUtf8())
    }

    @Test fun authenticatedCallsCarryBearerToken() = runBlocking {
        tokens.save("tok123")
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"a","kind":"fact","text":"t","createdAt":"2026-11-13T00:00:00Z"}],"nextCursor":"7"}"""))
        val page = api.memory(limit = 20)
        assertEquals("7", page.nextCursor)
        val req = server.takeRequest()
        assertEquals("Bearer tok123", req.getHeader("Authorization"))
        assertEquals("/api/v1/memory?limit=20", req.path)
    }

    @Test fun missingTokenFailsWithoutNetwork() = runBlocking {
        try { api.settings(); fail() } catch (e: ApiException) { assertTrue(e.unauthorized) }
        assertEquals(0, server.requestCount)
    }

    @Test fun serverErrorKeepsStatusAndReason() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        try { api.consents(); fail() } catch (e: ApiException) {
            assertEquals(401, e.code); assertEquals("unauthorized", e.reason); assertTrue(e.unauthorized)
        }
    }

    @Test fun setSpeechPatchesOnlySpeech() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setBody("""{"proactive":"normal","memoryEnabled":true,"speech":"banmal","nickname":null}"""))
        assertEquals("banmal", api.setSpeech(Speech.BANMAL).speech)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("""{"speech":"banmal"}""", req.body.readUtf8())
    }

    @Test fun settingsCarryTheLookFromTheCar() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setBody("""{"proactive":"normal","memoryEnabled":true,"speech":"banmal","nickname":null,"bodyColor":1,"hat":2,"neck":1}"""))
        val s = api.settings()
        assertEquals(Triple(1, 2, 1), Triple(s.bodyColor, s.hat, s.neck))
        // an older server without the fields: default look (beanie, no scarf, body follows the theme)
        server.enqueue(MockResponse().setBody("""{"proactive":"normal","memoryEnabled":true,"speech":"banmal","nickname":null,"bodyColor":null}"""))
        val d = api.settings()
        assertEquals(Triple(null, 1, 0), Triple(d.bodyColor, d.hat, d.neck))
    }

    @Test fun deleteCallsUseNoBodyAndAcceptEmptyReply() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteMemory("a/b")
        api.deleteAllMemory()
        assertEquals("/api/v1/memory/a%2Fb", server.takeRequest().path)
        assertEquals("/api/v1/memory", server.takeRequest().path)
    }

    @Test fun consentPostsPhoneChannel() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setBody("""{"consents":[{"type":"record","version":"v","granted":true,"at":"x"}]}"""))
        api.postConsent("record", true, "v")
        assertEquals("""{"type":"record","version":"v","granted":true,"channel":"phone"}""", server.takeRequest().body.readUtf8())
    }

    @Test fun unpairDeletesOnServerThenClearsTheToken() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setResponseCode(204))
        api.unpair()
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/v1/pairings/current", req.path)
        assertEquals("Bearer t", req.getHeader("Authorization"))
        assertNull(tokens.load())
    }

    @Test fun unpairKeepsTheTokenWhenTheServerFails() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setResponseCode(500))
        try { api.unpair(); fail() } catch (e: ApiException) { assertEquals(500, e.code) }
        assertEquals("t", tokens.load())
    }

    @Test fun unpairWithAnAlreadyRevokedTokenStillClears() = runBlocking {
        tokens.save("t")
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        api.unpair()
        assertNull(tokens.load())
    }
}
