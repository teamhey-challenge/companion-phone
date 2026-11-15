package ai.hey.phone.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Frames the server pushes on `/v1/inbox/stream` (contract D-47). Unknown types are ignored. */
sealed interface PushFrame {
    data class Message(val id: String, val fromName: String, val text: String) : PushFrame
    data class Arrived(val id: String?, val fromName: String) : PushFrame
    data class TripDone(val text: String, val date: String) : PushFrame
    data class DiaryReady(val date: String) : PushFrame

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PushFrame? {
            val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            return when (s("type")) {
                "friend_message" -> Message(s("id") ?: return null, s("fromName").orEmpty(), s("text") ?: return null)
                "friend_arrived" -> Arrived(s("id"), s("fromName").orEmpty())
                "trip_done" -> TripDone(s("text") ?: return null, s("date").orEmpty())
                "diary_ready" -> DiaryReady(s("date") ?: return null)
                else -> null
            }
        }
    }
}

/** Remembers what was already shown so the stream and the 15-minute poll never notify twice. */
interface SeenStore {
    /** True the first time [key] is seen (and remembers it). */
    fun firstTime(key: String): Boolean

    /** ISO time of the newest message already fetched; null before the first poll. */
    var since: String?
}

class PrefsSeenStore(context: Context) : SeenStore {
    private val prefs = context.getSharedPreferences("hey_phone_seen", Context.MODE_PRIVATE)

    @Synchronized
    override fun firstTime(key: String): Boolean {
        val keys = prefs.getString(KEYS, "").orEmpty().split('\n').filter { it.isNotEmpty() }
        if (key in keys) return false
        prefs.edit().putString(KEYS, (keys + key).takeLast(MAX).joinToString("\n")).apply()
        return true
    }

    override var since: String?
        get() = prefs.getString(SINCE, null)
        set(v) { prefs.edit().putString(SINCE, v).apply() }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEYS = "keys"
        const val SINCE = "since"
        const val MAX = 100
    }
}

class MemorySeenStore : SeenStore {
    private val keys = LinkedHashSet<String>()
    override var since: String? = null
    override fun firstTime(key: String) = keys.add(key)
}

/**
 * Decides whether a frame becomes a notification: once per event, and not while the app is on screen (the lists refresh
 * themselves). D-52: only friend messages and the trip-end diary (trip_done, diary_ready) notify; `friend_arrived` is shown inside the app only.
 */
class PushGate(private val seen: SeenStore, private val foreground: () -> Boolean) {
    fun shouldNotify(frame: PushFrame): Boolean {
        val key = when (frame) {
            is PushFrame.Message -> "msg:${frame.id}"
            is PushFrame.Arrived -> return false
            is PushFrame.TripDone -> "trip:${frame.date}:${frame.text}"
            is PushFrame.DiaryReady -> "diary:${frame.date}"
        }
        if (!seen.firstTime(key)) return false
        return !foreground()
    }
}

/**
 * Fallback for when the inbox socket is not up (app process dead): fetch what is newer than the last poll and today's
 * diary, and hand every event to [sink] (which dedupes through [PushGate]).
 */
class Poller(
    private val api: ApiClient,
    private val seen: SeenStore,
    private val sink: (PushFrame) -> Unit,
    private val today: () -> String,
    private val nowIso: () -> String,
) {
    suspend fun poll() {
        val since = seen.since ?: nowIso().also { seen.since = it }
        val list = api.messages(since)
        list.filter { it.direction == "in" }.sortedBy { it.at }.forEach { sink(PushFrame.Message(it.id, it.friendName, it.text)) }
        list.maxOfOrNull { it.at }?.takeIf { it.isNotEmpty() && it > since }?.let { seen.since = it }

        // The diary is best effort: a failed read must not hide messages that were already delivered above.
        val date = today()
        runCatching { api.diary(date) }.getOrNull()?.let { d ->
            d.entries.filter { it.kind == "trip_done" }.maxByOrNull { it.at.orEmpty() }?.let { sink(PushFrame.TripDone(it.text, date)) }
            if (!d.summary.isNullOrBlank()) sink(PushFrame.DiaryReady(date))
        }
    }
}

/** Runs [block]; a 401/403 calls [onUnauthorized] once, any other server/network failure calls [onFail]. */
suspend fun guardedCall(onUnauthorized: () -> Unit, onFail: (Exception) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: ApiException) {
        if (e.unauthorized) onUnauthorized() else onFail(e)
    } catch (e: java.io.IOException) {
        onFail(e)
    }
}
