package ai.hey.phone.feature.talk

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.ChatBubble
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HStack
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyIconButton
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextField
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.OceanBackground
import ai.hey.phone.ui.TabHeader
import ai.hey.phone.ui.TabTitle
import ai.hey.phone.ui.VStack
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Tab 대화: transcript bubbles, a text field with send, and a mic button. Stateless. */
@Composable
fun TalkScreen(
    bubbles: List<Bubble>,
    status: TalkStatus,
    notice: TalkNotice?,
    needsConsent: Boolean?,
    consenting: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))) {
            TabTitle(stringResource(R.string.talk_title), Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp))
            val list = rememberLazyListState()
            LaunchedEffect(bubbles.size, bubbles.lastOrNull()?.text?.length) { if (bubbles.isNotEmpty()) list.animateScrollToItem(bubbles.lastIndex) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(HeyGap.G12.value)) {
                if (bubbles.isEmpty()) item {
                    VStack(HeyGap.G16, Modifier.fillMaxWidth()) {
                        CharacterHero()
                        HeyText(stringResource(R.string.talk_empty), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                    }
                }
                items(bubbles, key = { it.id }) { ChatBubble(it.text, it.mine) }
                if (status == TalkStatus.Thinking && bubbles.lastOrNull()?.mine == true) item { HeyText(stringResource(R.string.talk_thinking), tone = HeyTone.Muted) }
            }
            VStack(HeyGap.G8, Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                notice?.let {
                    HeyNotice(
                        stringResource(
                            when (it) {
                                TalkNotice.NoMic -> R.string.talk_no_mic
                                TalkNotice.NoSpeech -> R.string.talk_no_speech
                                TalkNotice.DidntHear -> R.string.talk_didnt_hear
                                TalkNotice.Failed -> R.string.talk_failed
                                TalkNotice.Busy -> R.string.talk_busy
                                TalkNotice.ConsentFailed -> R.string.talk_consent_failed
                            },
                        ),
                    )
                }
                if (needsConsent == true) {
                    HeyText(stringResource(R.string.talk_consent), tone = HeyTone.Muted)
                    GlassButton(stringResource(R.string.talk_consent_go), onConsent, Modifier.fillMaxWidth(), loading = consenting)
                } else {
                    if (status == TalkStatus.Listening) HeyText(stringResource(R.string.talk_listening), tone = HeyTone.Accent)
                    HStack(HeyGap.G8, Modifier.fillMaxWidth()) {
                        HeyTextField(
                            value = draft, onValueChange = onDraft, label = "", modifier = Modifier.weight(1f), capitalize = false,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send, onImeAction = onSend,
                        )
                        HeyIconButton(HeyIcons.Mic, stringResource(if (status == TalkStatus.Listening) R.string.talk_mic_stop else R.string.talk_mic), onMic, enabled = status != TalkStatus.Thinking && needsConsent != null)
                        HeyIconButton(HeyIcons.Send, stringResource(R.string.talk_send), onSend, enabled = draft.isNotBlank() && status != TalkStatus.Thinking && needsConsent != null)
                    }
                }
            }
        }
    }
}

/** Wires [TalkModel] to the server, [PcmPlayer] and Android's [SpeechRecognizer] (RECORD_AUDIO asked at the first mic tap). */
@Composable
fun TalkRoute(onUnauthorized: () -> Unit) {
    val context = LocalContext.current
    val app = HeyPhoneApp.from(context)
    val scope = rememberCoroutineScope()
    val player = remember { PcmPlayer() }
    val model = remember { TalkModel(app.api, scope, TalkClient(app.api), player, onUnauthorized) }
    var draft by remember { mutableStateOf("") }
    val recognizer = remember { Recognizer(context) }

    LaunchedEffect(Unit) { model.loadConsent() }
    DisposableEffect(Unit) { onDispose { recognizer.destroy(); model.stopAudio(); model.release() } }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { model.report(TalkNotice.NoSpeech); return }
        model.setListening(true)
        recognizer.start(
            onText = { text -> model.setListening(false); if (text.isBlank()) model.report(TalkNotice.DidntHear) else model.say(text) },
            onError = { model.setListening(false); model.report(TalkNotice.DidntHear) },
        )
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else model.report(TalkNotice.NoMic)
    }

    TalkScreen(
        bubbles = model.bubbles,
        status = model.status,
        notice = model.notice,
        needsConsent = model.needsConsent,
        consenting = model.consenting,
        draft = draft,
        onDraft = { draft = it.take(TalkModel.MAX_TEXT_CHARS); model.clearNotice() },
        onSend = { model.say(draft); draft = "" },
        onMic = {
            when {
                model.status == TalkStatus.Listening -> recognizer.finish()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onConsent = model::grantConsent,
    )
}

/** Thin wrapper over [SpeechRecognizer] (main thread only): Korean, one utterance, final result only. */
private class Recognizer(private val context: Context) {
    private var rec: SpeechRecognizer? = null

    fun start(onText: (String) -> Unit, onError: () -> Unit) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                onText(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }
            override fun onError(error: Int) = onError()
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
        )
        rec = r
    }

    /** Ends the utterance now; the final result still arrives through onText. */
    fun finish() { rec?.stopListening() }
    fun stop() { rec?.let { it.cancel(); it.destroy() }; rec = null }
    fun destroy() = stop()
}
