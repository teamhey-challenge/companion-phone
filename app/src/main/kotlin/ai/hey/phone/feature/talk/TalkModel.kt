package ai.hey.phone.feature.talk

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/** One transcript bubble. [mine] = the user's words (typed or recognised). */
data class Bubble(val id: Int, val mine: Boolean, val text: String)

enum class TalkStatus { Idle, Listening, Thinking }

enum class TalkNotice { NoMic, NoSpeech, DidntHear, Failed, Busy, ConsentFailed }

/**
 * Sends text turns and streams events; implemented by [TalkClient] in the app and by a fake in tests. One transport lives
 * as long as the 대화 tab is open (the server keeps the recent context per socket); [release] ends it.
 */
fun interface TalkTransport {
    suspend fun send(text: String, onEvent: (TalkEvent) -> Unit)

    fun release() {}
}

/** Plays PCM chunks; implemented by [PcmPlayer]. */
interface Audio {
    fun start(sampleRate: Int)
    fun write(bytes: ByteArray)
    fun finish()
    fun stop()
}

/**
 * State of the 대화 tab. Text typed by the user and text recognised from speech go through the same [say]; the server's
 * reply text streams into one "her" bubble and its PCM is played as it arrives. Needs `record` + `external_ai` consent
 * (the server refuses a turn with `consent_required` otherwise): [needsConsent] drives the consent button.
 */
class TalkModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val transport: TalkTransport,
    private val audio: Audio,
    private val onUnauthorized: () -> Unit,
) {
    val bubbles = mutableStateListOf<Bubble>()
    var status by mutableStateOf(TalkStatus.Idle)
        private set
    var notice: TalkNotice? by mutableStateOf(null)
        private set
    /** null = not known yet (the consent read is running). */
    var needsConsent: Boolean? by mutableStateOf(null)
        private set
    var consenting by mutableStateOf(false)
        private set

    private var nextId = 1
    private var herBubble: Int? = null
    private var playing = false

    fun loadConsent() {
        scope.launch {
            try {
                val granted = api.consents().consents.filter { it.granted }.map { it.type }.toSet()
                needsConsent = !("record" in granted && "external_ai" in granted)
            } catch (e: ApiException) {
                if (e.unauthorized) onUnauthorized()
            } catch (_: IOException) {
            }
        }
    }

    /** Grants record + external_ai (both must succeed) and opens the talk. */
    fun grantConsent() {
        if (consenting) return
        consenting = true
        notice = null
        scope.launch {
            try {
                api.postConsent("record", true)
                api.postConsent("external_ai", true)
                needsConsent = false
            } catch (e: ApiException) {
                if (e.unauthorized) onUnauthorized() else notice = TalkNotice.ConsentFailed
            } catch (_: IOException) {
                notice = TalkNotice.ConsentFailed
            }
            consenting = false
        }
    }

    fun setListening(on: Boolean) {
        if (status == TalkStatus.Thinking) return
        if (on) audio.stop()
        status = if (on) TalkStatus.Listening else TalkStatus.Idle
    }

    fun report(n: TalkNotice) { notice = n; if (status == TalkStatus.Listening) status = TalkStatus.Idle }

    fun clearNotice() { notice = null }

    /** Sends [text] (trimmed, cut to [MAX_TEXT_CHARS], ignored when blank or while a reply is running). */
    fun say(text: String) {
        val t = text.trim().take(MAX_TEXT_CHARS)
        if (t.isEmpty() || status == TalkStatus.Thinking || needsConsent == true) return
        notice = null
        audio.stop()
        bubbles += Bubble(nextId++, mine = true, text = t)
        herBubble = null
        playing = false
        status = TalkStatus.Thinking
        scope.launch {
            try {
                transport.send(t, ::onEvent)
            } catch (e: ApiException) {
                if (e.unauthorized) onUnauthorized()
                fail(TalkNotice.Failed)
            } catch (_: IOException) {
                fail(TalkNotice.Failed)
            }
        }
    }

    private fun onEvent(e: TalkEvent) {
        when (e) {
            is TalkEvent.Start -> if (e.pcm) { audio.start(e.sampleRate); playing = true }
            is TalkEvent.Delta -> appendHer(e.text, replace = false)
            is TalkEvent.Audio -> if (playing) audio.write(e.bytes)
            is TalkEvent.End -> {
                if (e.text.isNotEmpty()) appendHer(e.text, replace = true)
                if (playing) audio.finish()
                playing = false
                status = TalkStatus.Idle
            }
            is TalkEvent.Error -> {
                audio.stop()
                when (e.code) {
                    "consent_required" -> { needsConsent = true; status = TalkStatus.Idle }
                    "rate_limited" -> fail(TalkNotice.Busy)
                    else -> fail(TalkNotice.Failed)
                }
            }
            is TalkEvent.Closed -> if (e.failure && status == TalkStatus.Thinking) fail(TalkNotice.Failed)
        }
    }

    private fun appendHer(text: String, replace: Boolean) {
        val id = herBubble
        if (id == null) {
            if (text.isEmpty()) return
            herBubble = nextId
            bubbles += Bubble(nextId++, mine = false, text = text)
        } else {
            val i = bubbles.indexOfFirst { it.id == id }
            if (i >= 0) bubbles[i] = bubbles[i].copy(text = if (replace) text else bubbles[i].text + text)
        }
    }

    private fun fail(n: TalkNotice) {
        audio.stop()
        playing = false
        notice = n
        status = TalkStatus.Idle
    }

    fun stopAudio() = audio.stop()

    /** The 대화 tab is closing: the talk socket (and the server's context for it) ends. */
    fun release() = transport.release()

    companion object {
        /** Protocol `MAX_TEXT_CHARS`: the server answers `text_too_long` above this. */
        const val MAX_TEXT_CHARS = 500
    }
}
