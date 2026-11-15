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

@Serializable data class Character(val name: String = "헤이리", val bodyColor: Int = 0)

@Serializable private data class ClaimBody(val record: Boolean, val memory: Boolean)

@Serializable data class ClaimResult(val phoneToken: String, val character: Character = Character())

@Serializable private data class ConsentBody(val type: String, val version: String, val granted: Boolean, val channel: String = "phone")

@Serializable data class Consent(val type: String, val version: String, val granted: Boolean, val at: String? = null)

@Serializable data class ConsentList(val consents: List<Consent> = emptyList()) {
    /** The newest answer for [type]: true granted, false withdrawn, null never answered. */
    fun answer(type: String): Boolean? = consents.lastOrNull { it.type == type }?.granted
}

/** Same rule as the server's `memoryAllowed`: memory consent granted, record consent not withdrawn, and the memory setting on. */
fun memoryAllowed(settings: Settings, consents: ConsentList): Boolean =
    consents.answer("memory") == true && consents.answer("record") != false && settings.memoryEnabled

/** True when memory is known to be off: the setting says off, or (consents readable) the server would not allow it. An unreadable side never switches the guidance on. */
fun memoryKnownOff(settings: Settings?, consents: ConsentList?): Boolean =
    settings != null && (!settings.memoryEnabled || (consents != null && !memoryAllowed(settings, consents)))

@Serializable data class MemoryItem(val id: String, val kind: String, val text: String, val createdAt: String)

@Serializable data class MemoryPage(val items: List<MemoryItem> = emptyList(), val nextCursor: String? = null)

@Serializable data class Settings(
    val proactive: String,
    val memoryEnabled: Boolean,
    val speech: String,
    val nickname: String? = null,
    /** Look picked in the car's 꾸미기 (D-41, Rive values): bodyColor 0 aqua / 1 peach / null = follows the theme; hat 0 none / 1 beanie / 2 bucket; neck 0 none / 1 scarf. */
    val bodyColor: Int? = null,
    val hat: Int = 1,
    val neck: Int = 0,
    /** Read-only alias of [nickname] (the one name); the phone reads [nickname]. */
    val displayName: String? = null,
)

@Serializable private data class GoogleBody(val idToken: String)

/** `POST /v1/auth/google`: [linkedCar] = the account already has a car; [isNew] = the account was just created. */
@Serializable data class GoogleLoginResult(
    val phoneToken: String,
    val accountId: String? = null,
    val displayName: String? = null,
    val linkedCar: Boolean = false,
    val isNew: Boolean = false,
)

/** One-time socket ticket from POST /v1/phone-sessions (60 s, one socket). */
@Serializable data class PhoneSession(val wsTicket: String, val sessionId: String? = null, val expiresInSec: Int = 60)

@Serializable data class Friend(val id: String, val name: String, val since: String? = null, val shareArrival: Boolean = false)

@Serializable data class Invite(val code: String, val url: String, val expiresAt: String? = null)

@Serializable data class AcceptResult(val friend: Friend)

@Serializable data class RequestPerson(val id: String? = null, val name: String)

/** A friend request (contract: `POST/GET /v1/friends/requests`). [from] is set on incoming, [to] on outgoing ones. */
@Serializable data class FriendRequest(val id: String, val from: RequestPerson? = null, val to: RequestPerson? = null, val createdAt: String? = null)

@Serializable data class FriendRequests(val incoming: List<FriendRequest> = emptyList(), val outgoing: List<FriendRequest> = emptyList())

@Serializable private data class RequestBody(val code: String)

@Serializable private data class SendBody(val toFriendId: String, val text: String)

@Serializable private data class ShareBody(val shareArrival: Boolean)

/** [direction] is "in" (a friend wrote to me) or "out" (I wrote). */
@Serializable data class FriendMessage(
    val id: String,
    val direction: String,
    val friendId: String? = null,
    val friendName: String = "",
    val text: String,
    val at: String = "",
)

@Serializable data class MessageList(val messages: List<FriendMessage> = emptyList())

@Serializable data class DiaryEntry(val at: String? = null, val text: String, val kind: String = "")

@Serializable data class Diary(val date: String = "", val entries: List<DiaryEntry> = emptyList(), val summary: String? = null)

@Serializable private data class SpeechPatch(val speech: String)

object Speech {
    const val BANMAL = "banmal"
    const val JONDAETMAL = "jondaetmal"
}

/** Server said no. [code] is the HTTP status; [reason] the server's `error` string when it sent one. */
class ApiException(val code: Int, val reason: String?) : IOException("HTTP $code${reason?.let { " $it" } ?: ""}") {
    /** The phone token is not valid (any more). A 403 `car_link_required` is not this: the token is fine, the car is missing. */
    val unauthorized get() = code == 401 || (code == 403 && reason != "car_link_required")

    /** The same Google account signed in on another phone, which replaced this phone's token (D-57). */
    val signedInElsewhere get() = code == 401 && reason == "signed_in_elsewhere"

    /** D-58: this Google account has no linked car; the server refuses everything but login, consent and the car link. */
    val carLinkRequired get() = code == 403 && reason == "car_link_required"
}

/** `GET /v1/account`: whether this Google account has a linked car (D-58: without one the phone only shows the connect screen). */
@Serializable data class Account(
    val accountKind: String = "phone",
    val linkedCar: Boolean = false,
    val displayName: String? = null,
    val googleName: String? = null,
    val pairedAt: String? = null,
)

/** Why the server rejected the phone's token; the next start screen says it (6.6). */
enum class SignOutNotice { SignedInElsewhere, SessionExpired }

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
    /** Called when the server answers 401 to a request that carried the phone token (before the exception is thrown). */
    private val onSessionRejected: (SignOutNotice) -> Unit = {},
    /** Called when the server answers 403 `car_link_required`: the account has no linked car, so the app shows the connect screen. */
    private val onCarLinkRequired: () -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * Claims the code with the Google phone token (D-57: a car is only linked to a signed-in Google account; the server answers
     * 403 `google_login_required` otherwise) and stores the returned token. `record` must be true. Failures carry the server's
     * `error` in [ApiException.reason]: unknown, expired, already_used, car_linked_to_other_account, google_linked_to_other_car,
     * record_consent_required, google_login_required, rate_limited.
     */
    suspend fun claim(code: String, record: Boolean, memory: Boolean): ClaimResult {
        val body = json.encodeToString(ClaimBody.serializer(), ClaimBody(record, memory)).toRequestBody(jsonType)
        val r: ClaimResult = send(Request.Builder().url(url("v1/pairings/$code/claim")).post(body))
        tokens.save(r.phoneToken)
        return r
    }

    suspend fun postConsent(type: String, granted: Boolean, version: String = CONSENT_VERSION): ConsentList {
        val body = json.encodeToString(ConsentBody.serializer(), ConsentBody(type, version, granted)).toRequestBody(jsonType)
        return send(Request.Builder().url(url("v1/consents")).post(body))
    }

    suspend fun consents(): ConsentList = send(Request.Builder().url(url("v1/consents")).get())

    suspend fun account(): Account = send(Request.Builder().url(url("v1/account")).get())


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
     * Google sign-in: the ID token goes to the server, which verifies it and answers a phone token (new phone-only account, the
     * account of an earlier login, or the linked car's account). The token is stored like a pairing claim does. 503
     * `google_login_unconfigured` = the server has no Google client id.
     */
    suspend fun googleLogin(idToken: String): GoogleLoginResult {
        val body = json.encodeToString(GoogleBody.serializer(), GoogleBody(idToken)).toRequestBody(jsonType)
        val r: GoogleLoginResult = send(Request.Builder().url(url("v1/auth/google")).post(body), auth = false)
        tokens.save(r.phoneToken)
        return r
    }

    /** The phone token may change the user's name (`nickname`, the one name heyri calls and friends see). A null field is not sent. */
    suspend fun patchSettings(name: String? = null): Settings {
        val obj = kotlinx.serialization.json.buildJsonObject {
            name?.let { put("nickname", kotlinx.serialization.json.JsonPrimitive(it)) }
        }
        return send(Request.Builder().url(url("v1/settings")).patch(obj.toString().toRequestBody(jsonType)))
    }

    /** One-time ticket for a `/v1/talk` or `/v1/inbox/stream` socket; ask again for every socket. */
    suspend fun phoneSession(): PhoneSession = send(Request.Builder().url(url("v1/phone-sessions")).post("".toRequestBody(jsonType)))

    suspend fun friends(): List<Friend> = send(Request.Builder().url(url("v1/friends")).get())

    suspend fun createInvite(): Invite = send(Request.Builder().url(url("v1/friends/invites")).post("".toRequestBody(jsonType)))

    suspend fun acceptInvite(code: String): Friend {
        val r: AcceptResult = send(Request.Builder().url(url("v1/friends/invites/${java.net.URLEncoder.encode(code, "UTF-8")}/accept")).post("".toRequestBody(jsonType)))
        return r.friend
    }

    /** Sends a friend request with the code of an invite; 404 unknown_code, 410 expired_code, 409 self/already_friends/friend_limit. */
    suspend fun sendFriendRequest(code: String): FriendRequest {
        val body = json.encodeToString(RequestBody.serializer(), RequestBody(code)).toRequestBody(jsonType)
        return send(Request.Builder().url(url("v1/friends/requests")).post(body))
    }

    suspend fun friendRequests(): FriendRequests = send(Request.Builder().url(url("v1/friends/requests")).get())

    suspend fun acceptFriendRequest(id: String): Friend {
        val r: AcceptResult = send(Request.Builder().url(url("v1/friends/requests/${java.net.URLEncoder.encode(id, "UTF-8")}/accept")).post("".toRequestBody(jsonType)))
        return r.friend
    }

    suspend fun declineFriendRequest(id: String) =
        sendNoBody(Request.Builder().url(url("v1/friends/requests/${java.net.URLEncoder.encode(id, "UTF-8")}/decline")).post("".toRequestBody(jsonType)))

    suspend fun unfriend(id: String) = sendNoBody(Request.Builder().url(url("v1/friends/${java.net.URLEncoder.encode(id, "UTF-8")}")).delete())

    suspend fun setShareArrival(id: String, share: Boolean) {
        val body = json.encodeToString(ShareBody.serializer(), ShareBody(share)).toRequestBody(jsonType)
        sendNoBody(Request.Builder().url(url("v1/friends/${java.net.URLEncoder.encode(id, "UTF-8")}")).patch(body))
    }

    /** 422 `message_blocked` surfaces as [ApiException] with `reason == "message_blocked"`. */
    suspend fun sendMessage(toFriendId: String, text: String) {
        val body = json.encodeToString(SendBody.serializer(), SendBody(toFriendId, text)).toRequestBody(jsonType)
        sendNoBody(Request.Builder().url(url("v1/messages")).post(body))
    }

    /** Inbox + sent, newest first; [since] is an ISO time to fetch only newer ones. */
    suspend fun messages(since: String? = null): List<FriendMessage> {
        val u = url("v1/messages").newBuilder()
        if (since != null) u.addQueryParameter("since", since)
        return send<MessageList>(Request.Builder().url(u.build()).get()).messages
    }

    suspend fun reportMessage(id: String) =
        sendNoBody(Request.Builder().url(url("v1/messages/${java.net.URLEncoder.encode(id, "UTF-8")}/report")).post("".toRequestBody(jsonType)))

    suspend fun diary(date: String): Diary =
        send(Request.Builder().url(url("v1/diary").newBuilder().addQueryParameter("date", date).build()).get())

    /** Builds a websocket request for [path] ("v1/talk" / "v1/inbox/stream") with a fresh [ticket]; the host is the API host with ws(s). */
    fun wsRequest(path: String, ticket: String): Request {
        val u = url(path).newBuilder().addQueryParameter("ticket", ticket).build()
        return Request.Builder().url(u).build()
    }

    /** Shared client for websockets (OkHttp upgrades http(s) URLs itself). Long read timeout: the inbox socket idles. */
    fun wsClient(): OkHttpClient = http.newBuilder().readTimeout(0, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).pingInterval(30, TimeUnit.SECONDS).build()

    /**
     * Ends this pairing: `DELETE /v1/pairings/current` with the phone token (the server lets a phone end only its own pairing;
     * the car becomes unpaired, memory stays, this token is revoked). The local token is deleted only after the server
     * agreed, or answered 401/403 because the token was already dead; any other failure throws and keeps the token.
     */
    suspend fun unpair() {
        try {
            sendNoBody(Request.Builder().url(url("v1/pairings/current")).delete())
        } catch (e: ApiException) {
            if (!e.unauthorized) throw e
        }
        tokens.clear()
    }

    /**
     * Best-effort server logout: `DELETE /v1/phone-sessions/current` with [token] (3 s, every failure ignored). It never reports a 401 as a
     * rejected session, because the caller is already signing out. Memory, diary, friends and settings stay on the server.
     */
    suspend fun endSession(token: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url("v1/phone-sessions/current")).delete().header("Authorization", "Bearer $token").build()
                http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build().newCall(req).execute().close()
            }
        }
    }

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
                if (auth && res.code == 401) onSessionRejected(if (reason == "signed_in_elsewhere") SignOutNotice.SignedInElsewhere else SignOutNotice.SessionExpired)
                if (auth && res.code == 403 && reason == "car_link_required") onCarLinkRequired()
                throw ApiException(res.code, reason)
            }
            return text
        }
    }

    private fun Json.parseToJsonElementOrNull(text: String): String? {
        if (text.isBlank()) return null
        val obj = parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: return null
        return (obj["error"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (obj["code"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
