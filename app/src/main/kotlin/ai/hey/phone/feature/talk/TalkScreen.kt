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
import ai.hey.phone.ui.CharacterStill
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.style
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (bubbles.isEmpty()) item {
                    VStack(HeyGap.G16, Modifier.fillMaxWidth()) {
                        CharacterHero()
                        HeyText(stringResource(R.string.talk_empty), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                    }
                }
                items(bubbles, key = { it.id }) { TalkBubble(it.text, it.mine) }
                if (status == TalkStatus.Thinking && bubbles.lastOrNull()?.mine == true) item { HeyText(stringResource(R.string.talk_thinking), tone = HeyTone.Muted) }
            }
            VStack(HeyGap.G8, Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)) {
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
                    val c = HeyTheme.colors
                    val enabledSend = draft.isNotBlank() && status != TalkStatus.Thinking && needsConsent != null
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.bgRaised, RoundedCornerShape(28.dp)).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            BasicTextField(
                                value = draft, onValueChange = onDraft, singleLine = true,
                                textStyle = HeyTextVariant.Note.style(c.fgDefault), cursorBrush = SolidColor(c.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onAny = { onSend() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (draft.isEmpty()) HeyText(stringResource(R.string.talk_hint), tone = HeyTone.Muted, variant = HeyTextVariant.Note, maxLines = 1)
                        }
                        HeyIconButton(HeyIcons.Mic, stringResource(if (status == TalkStatus.Listening) R.string.talk_mic_stop else R.string.talk_mic), onMic, enabled = status != TalkStatus.Thinking && needsConsent != null, size = 24.dp)
                        HeyIconButton(HeyIcons.Send, stringResource(R.string.talk_send), onSend, enabled = enabledSend, size = 24.dp)
                    }
                }
            }
        }
    }
}

/** Studio phone-talk bubbles: hers = 40 dp face + white bubble, mine = light-accent bubble filling the rest of the row. */
@Composable
private fun TalkBubble(text: String, mine: Boolean) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(20.dp)
    if (mine) {
        Box(Modifier.fillMaxWidth().padding(start = 50.dp).clip(shape).background(c.accentContainer, shape).padding(horizontal = 16.dp, vertical = 12.dp)) {
            HeyText(text)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentContainer, CircleShape)) { CharacterStill("", size = 40.dp) }
            Box(Modifier.weight(1f, fill = false).clip(shape).background(c.bgRaised, shape).padding(horizontal = 16.dp, vertical = 12.dp)) {
                HeyText(text)
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
