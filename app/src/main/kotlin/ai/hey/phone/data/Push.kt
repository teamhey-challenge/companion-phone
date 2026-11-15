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
    /** [at] is the diary entry's time when the frame comes from the poll; null on the stream (the server's frame carries no id or time). */
    data class TripDone(val text: String, val date: String, val at: String? = null) : PushFrame
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

    /** ISO time of the newest trip end already handled (shown from the stream or polled); null before the first poll. */
    var tripSince: String?
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

    override var tripSince: String?
        get() = prefs.getString(TRIP_SINCE, null)
        set(v) { prefs.edit().putString(TRIP_SINCE, v).apply() }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEYS = "keys"
        const val SINCE = "since"
        const val TRIP_SINCE = "trip_since"
        const val MAX = 100
    }
}

class MemorySeenStore : SeenStore {
    private val keys = LinkedHashSet<String>()
    override var since: String? = null
    override var tripSince: String? = null
    override fun firstTime(key: String) = keys.add(key)
}

/**
 * Decides whether a frame becomes a notification: once per event, and not while the app is on screen (the lists refresh
 * themselves). D-52: only friend messages and the trip-end diary notify, and one trip end is ONE notification: `trip_done`
 * notifies, `diary_ready` only refreshes the Today tab; `friend_arrived` is shown inside the app only.
 *
 * A trip end from the stream has no id or time (the server's frame is `{type,text,date}`), so every such frame is its own
 * event: two trips with the same line on one day both notify. It also moves [SeenStore.tripSince] to now, so the poll that
 * follows does not announce the same trip again from the diary.
 */
class PushGate(
    private val seen: SeenStore,
    private val nowIso: () -> String = { java.time.Instant.now().toString() },
    private val foreground: () -> Boolean,
) {
    fun shouldNotify(frame: PushFrame): Boolean {
        val key = when (frame) {
            is PushFrame.Message -> "msg:${frame.id}"
            is PushFrame.Arrived -> return false
            is PushFrame.TripDone -> {
                val at = frame.at
                if (at == null) seen.tripSince = nowIso()
                if (at != null) "trip:$at" else "trip:stream:${java.util.UUID.randomUUID()}"
            }
            is PushFrame.DiaryReady -> return false
        }
        if (!seen.firstTime(key)) return false
        return !foreground()
    }
}

/**
 * Fallback for when the inbox socket is not up (app process dead): fetch what is newer than the last poll and today's
 * diary, and hand every event to [sink] (which dedupes through [PushGate]). Every `trip_done` entry newer than
 * [SeenStore.tripSince] is one event keyed by its own `at`, so two trips with the same line the same day both notify.
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
        val tripSince = seen.tripSince ?: nowIso().also { seen.tripSince = it }
        val list = api.messages(since)
        list.filter { it.direction == "in" }.sortedBy { it.at }.forEach { sink(PushFrame.Message(it.id, it.friendName, it.text)) }
        list.maxOfOrNull { it.at }?.takeIf { it.isNotEmpty() && it > since }?.let { seen.since = it }

        // The diary is best effort: a failed read must not hide messages that were already delivered above.
        val date = today()
        runCatching { api.diary(date) }.getOrNull()?.let { d ->
            // The stream stamps tripSince with the phone's clock, the entries carry the server's: a minute of slack keeps a trip the stream already showed from coming back.
            val floor = instantOf(tripSince)?.plusSeconds(CLOCK_SLACK_SEC)
            val fresh = d.entries.filter { it.kind == "trip_done" && it.at != null }
                .mapNotNull { e -> instantOf(e.at)?.let { it to e } }
                .filter { (t, _) -> floor == null || t.isAfter(floor) }
                .sortedBy { it.first }
            fresh.forEach { (_, e) -> sink(PushFrame.TripDone(e.text, date, e.at)) }
            fresh.lastOrNull()?.let { seen.tripSince = it.second.at }
            if (!d.summary.isNullOrBlank()) sink(PushFrame.DiaryReady(date))
        }
    }

    private fun instantOf(iso: String?): java.time.Instant? =
        runCatching { java.time.OffsetDateTime.parse(iso!!).toInstant() }.getOrNull()

    private companion object { const val CLOCK_SLACK_SEC = 60L }
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
