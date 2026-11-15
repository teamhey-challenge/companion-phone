package ai.hey.phone.feature.talk

import ai.hey.phone.data.ApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException

/** What one talk turn reports back. */
sealed interface TalkEvent {
    /** [pcm] true when the server will send PCM s16le [sampleRate] mono; false = text only (no server voice). */
    data class Start(val pcm: Boolean, val sampleRate: Int) : TalkEvent
    data class Delta(val text: String) : TalkEvent
    class Audio(val bytes: ByteArray) : TalkEvent
    data class End(val text: String) : TalkEvent
    data class Error(val code: String) : TalkEvent
    data class Closed(val failure: Boolean) : TalkEvent
}

private val json = Json { ignoreUnknownKeys = true }

/** Parses one server text frame of the talk protocol; null for frames the phone does not use (settings, pairing, proactive...). */
fun parseTalkFrame(text: String): TalkEvent? {
    val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    return when (s("type")) {
        "reply.start" -> {
            val audio = o["audio"]
            val spec = (audio as? kotlinx.serialization.json.JsonObject)
            TalkEvent.Start(spec != null, (spec?.get("sampleRate") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 24_000)
        }
        "reply.delta" -> TalkEvent.Delta(s("text").orEmpty())
        "reply.end" -> TalkEvent.End(s("text").orEmpty())
        "error" -> TalkEvent.Error(s("code") ?: "unknown")
        else -> null
    }
}

/** The first frame on a talk socket: sent once when the socket opens (the server refuses anything before it). */
fun helloFrame(): String = buildJsonObject { put("type", "hello"); put("v", 1); put("appVersion", "phone") }.toString()

/** Frames for one text turn (ptt): turn.start, text, turn.end. */
fun turnFrames(turnId: String, text: String): List<String> = listOf(
    buildJsonObject { put("type", "turn.start"); put("turnId", turnId); put("trigger", "ptt") }.toString(),
    buildJsonObject { put("type", "text"); put("turnId", turnId); put("text", text) }.toString(),
    buildJsonObject { put("type", "turn.end"); put("turnId", turnId) }.toString(),
)

/**
 * One socket for as long as the 대화 tab is open (④: the server keeps the recent context per socket): the ticket is
 * spent once, `hello` goes out once, then every turn is turn.start/text/turn.end on the same socket. The socket is closed
 * by [release] (leaving the tab); after a failure or a server close the next [send] opens a fresh one.
 */
class TalkClient(private val api: ApiClient) : TalkTransport {
    private val lock = Any()
    private var socket: WebSocket? = null
    private var opened = false
    private var pending = ArrayList<String>()
    private var listener: ((TalkEvent) -> Unit)? = null
    private var turns = 0

    /** Throws [ai.hey.phone.data.ApiException]/[IOException] when the ticket cannot be had; socket failures arrive as [TalkEvent.Closed]. */
    override suspend fun send(text: String, onEvent: (TalkEvent) -> Unit) {
        val frames = turnFrames("t${System.currentTimeMillis()}-${synchronized(lock) { ++turns }}", text)
        var ticket: String? = null
        while (true) {
            // A ticket is needed only when there is no socket; it is fetched outside the lock.
            if (ticket == null && synchronized(lock) { socket == null }) ticket = api.phoneSession().wsTicket
            synchronized(lock) {
                val ws = socket
                when {
                    ws != null && opened -> {
                        listener = onEvent
                        if (frames.all { ws.send(it) }) return
                        // The socket died between turns without a callback yet: drop it; the next turn reconnects.
                        ws.cancel()
                        dropLocked()
                        throw IOException("talk socket closed")
                    }
                    ws != null -> {
                        listener = onEvent
                        pending.addAll(frames)
                        return
                    }
                    ticket != null -> {
                        listener = onEvent
                        pending = ArrayList(frames)
                        opened = false
                        socket = api.wsClient().newWebSocket(api.wsRequest("v1/talk", ticket!!), Listener())
                        return
                    }
                }
            }
        }
    }

    /** Leaving the tab: the socket goes away and so does the server's context for it. */
    override fun release() {
        synchronized(lock) {
            socket?.close(1000, null)
            dropLocked()
        }
    }

    private fun dropLocked() {
        socket = null
        opened = false
        pending = ArrayList()
        listener = null
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (webSocket !== socket) return
                webSocket.send(helloFrame())
                pending.forEach { webSocket.send(it) }
                pending = ArrayList()
                opened = true
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val e = parseTalkFrame(text) ?: return
            val l = synchronized(lock) {
                if (webSocket !== socket) return
                listener.also { if (e is TalkEvent.End || e is TalkEvent.Error) listener = null }
            }
            l?.invoke(e)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val l = synchronized(lock) { if (webSocket !== socket) return else listener }
            l?.invoke(TalkEvent.Audio(bytes.toByteArray()))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            gone(webSocket)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone(webSocket)
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = gone(webSocket)

        /** The socket is over: a turn still waiting for its reply failed; an idle socket is just forgotten (the next turn reconnects). */
        private fun gone(webSocket: WebSocket) {
            val l = synchronized(lock) {
                if (webSocket !== socket) return
                listener.also { dropLocked() }
            }
            l?.invoke(TalkEvent.Closed(failure = true))
        }
    }
}

/** Plays streamed PCM s16le mono. Odd-sized chunks are stitched so no sample is split. */
class PcmPlayer : Audio {
    private var track: android.media.AudioTrack? = null
    private var carry: Byte? = null

    @Synchronized
    override fun start(sampleRate: Int) {
        stop()
        val min = android.media.AudioTrack.getMinBufferSize(sampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        track = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).build(),
            )
            .setBufferSizeInBytes(maxOf(min, sampleRate))
            .setTransferMode(android.media.AudioTrack.MODE_STREAM)
            .build().also { it.play() }
        carry = null
    }

    @Synchronized
    override fun write(bytes: ByteArray) {
        val t = track ?: return
        val data = carry?.let { byteArrayOf(it) + bytes } ?: bytes
        val even = data.size and 1.inv()
        carry = if (data.size != even) data[data.size - 1] else null
        if (even > 0) t.write(data, 0, even)
    }

    /** Lets the queued audio finish, then releases. */
    @Synchronized
    override fun finish() {
        val t = track ?: return
        track = null
        Thread {
            runCatching {
                t.stop() // MODE_STREAM: stop() drains what was written
                t.release()
            }
        }.start()
    }

    @Synchronized
    override fun stop() {
        track?.let { runCatching { it.pause(); it.flush(); it.release() } }
        track = null
        carry = null
    }
}
