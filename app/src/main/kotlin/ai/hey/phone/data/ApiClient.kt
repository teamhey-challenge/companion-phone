package ai.hey.phone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// ---- wire types (docs/status/.../contract.md; same API as companion-web) ----

@Serializable data class Character(val name: String = "헤이파리", val bodyColor: Int = 0)

@Serializable data class PairingInfo(val status: String, val character: Character = Character())

@Serializable private data class ClaimBody(val record: Boolean, val memory: Boolean)

@Serializable data class ClaimResult(val phoneToken: String, val character: Character = Character())

@Serializable private data class ConsentBody(val type: String, val version: String, val granted: Boolean, val channel: String = "phone")

@Serializable data class Consent(val type: String, val version: String, val granted: Boolean, val at: String? = null)

@Serializable data class ConsentList(val consents: List<Consent> = emptyList())

@Serializable data class MemoryItem(val id: String, val kind: String, val text: String, val createdAt: String)

@Serializable data class MemoryPage(val items: List<MemoryItem> = emptyList(), val nextCursor: String? = null)

@Serializable data class Settings(val proactive: String, val memoryEnabled: Boolean, val speech: String, val nickname: String? = null)

@Serializable private data class SpeechPatch(val speech: String)

object Speech {
    const val BANMAL = "banmal"
    const val JONDAETMAL = "jondaetmal"
}

/** Server said no. [code] is the HTTP status; [reason] the server's `error` string when it sent one. */
class ApiException(val code: Int, val reason: String?) : IOException("HTTP $code${reason?.let { " $it" } ?: ""}") {
    /** The phone token is not valid (any more): the pairing was removed on the car. */
    val unauthorized get() = code == 401 || code == 403
}

/** Pairing code states from GET /v1/pairings/{code}. */
object PairStatus {
    const val PENDING = "pending"
    const val CLAIMED = "claimed"
    const val EXPIRED = "expired"
    const val UNKNOWN = "unknown"
}

/** Version of the consent texts the phone shows; bump with the car app's CONSENT_VERSION when the texts change. */
const val CONSENT_VERSION = "2026-11-15"

/**
 * HTTP client for the companion server (base URL ends with `/api`). Every call is a suspend function that throws
 * [ApiException] (server answered with an error) or [IOException] (network). The phone token comes from [tokens] at
 * call time so a pairing removed locally takes effect at once.
 */
class ApiClient(
    private val baseUrl: String,
    private val tokens: TokenStore,
    private val http: OkHttpClient = defaultHttp(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Public (no token): state of a pairing code. Unknown codes answer 200 + `unknown`. */
    suspend fun pairing(code: String): PairingInfo = send(Request.Builder().url(url("v1/pairings/$code")).get(), auth = false)

    /** Claims the code and stores the returned phone token. `record` must be true (server answers 400 otherwise). */
    suspend fun claim(code: String, record: Boolean, memory: Boolean): ClaimResult {
        val body = json.encodeToString(ClaimBody.serializer(), ClaimBody(record, memory)).toRequestBody(jsonType)
        val r: ClaimResult = send(Request.Builder().url(url("v1/pairings/$code/claim")).post(body), auth = false)
        tokens.save(r.phoneToken)
        return r
    }

    suspend fun postConsent(type: String, granted: Boolean, version: String = CONSENT_VERSION): ConsentList {
        val body = json.encodeToString(ConsentBody.serializer(), ConsentBody(type, version, granted)).toRequestBody(jsonType)
        return send(Request.Builder().url(url("v1/consents")).post(body))
    }

    suspend fun consents(): ConsentList = send(Request.Builder().url(url("v1/consents")).get())

    suspend fun withdrawConsent(type: String) = sendNoBody(Request.Builder().url(url("v1/consents/$type")).delete())

    suspend fun memory(cursor: String? = null, limit: Int = 50): MemoryPage {
        val u = url("v1/memory").newBuilder().addQueryParameter("limit", limit.toString())
        if (cursor != null) u.addQueryParameter("cursor", cursor)
        return send(Request.Builder().url(u.build()).get())
    }

    suspend fun deleteMemory(id: String) = sendNoBody(Request.Builder().url(url("v1/memory/${java.net.URLEncoder.encode(id, "UTF-8")}")).delete())

    suspend fun deleteAllMemory() = sendNoBody(Request.Builder().url(url("v1/memory")).delete())

    suspend fun settings(): Settings = send(Request.Builder().url(url("v1/settings")).get())

    /** The phone token may change only `speech` (server answers 403 for the rest). */
    suspend fun setSpeech(speech: String): Settings {
        val body = json.encodeToString(SpeechPatch.serializer(), SpeechPatch(speech)).toRequestBody(jsonType)
        return send(Request.Builder().url(url("v1/settings")).patch(body))
    }

    /**
     * Forgets this pairing on the phone: the token is deleted locally. The server has no phone-side unpair (only the car
     * ends a pairing, `DELETE /v1/pairings/current` is car-only), so the car's token for this phone stays valid until the
     * car unpairs; the phone simply stops using it.
     */
    fun unpair() = tokens.clear()

    private fun url(path: String) = (baseUrl.trimEnd('/') + "/" + path).toHttpUrl()

    private suspend inline fun <reified T> send(builder: Request.Builder, auth: Boolean = true): T =
        withContext(Dispatchers.IO) { json.decodeFromString(execute(builder, auth)) }

    private suspend fun sendNoBody(builder: Request.Builder) {
        withContext(Dispatchers.IO) { execute(builder, true) }
    }

    private fun execute(builder: Request.Builder, auth: Boolean): String {
        if (auth) {
            val token = tokens.load() ?: throw ApiException(401, "no_token")
            builder.header("Authorization", "Bearer $token")
        }
        http.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val reason = runCatching { json.parseToJsonElementOrNull(text) }.getOrNull()
                throw ApiException(res.code, reason)
            }
            return text
        }
    }

    private fun Json.parseToJsonElementOrNull(text: String): String? {
        if (text.isBlank()) return null
        val obj = parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: return null
        return (obj["error"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
