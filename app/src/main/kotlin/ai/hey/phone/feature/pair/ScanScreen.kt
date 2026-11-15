package ai.hey.phone.feature.pair

import ai.hey.phone.R
import ai.hey.phone.ui.EntryButton
import ai.hey.phone.ui.EntryCard
import ai.hey.phone.ui.EntryCodeField
import ai.hey.phone.ui.EntryFrame
import ai.hey.phone.ui.InkText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeaderBar
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyButtonVariant
import ai.hey.phone.ui.HeyDivider
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextField
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.PairMetrics
import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.ui.VStack
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-app QR scanner (CameraX preview + ZXing decode on the luminance plane). When the camera is not allowed or the scene
 * has no QR, the code can be typed instead: [onCode] gets the upper-case pair code in both cases.
 */
@Composable
fun ScanRoute(
    onCode: (String) -> Unit,
    onBack: () -> Unit,
    parse: (String?) -> String? = ::parsePairCode,
    title: String = stringResource(R.string.scan_title),
    hint: String = stringResource(R.string.scan_hint),
    wrongText: String = stringResource(R.string.scan_paste_wrong),
    pasteLabel: String = stringResource(R.string.scan_paste_label),
    email: String? = null,
    onOtherAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    BackHandler(onBack = onBack)
    var typed by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }

    ScanContent(
        granted = granted, typed = typed, wrong = wrong,
        onTyped = { typed = it; wrong = false },
        onAllow = { launcher.launch(Manifest.permission.CAMERA) },
        onSubmit = { parse(typed)?.let(onCode) ?: run { wrong = true } },
        onBack = onBack, title = title, hint = hint, wrongText = wrongText, pasteLabel = pasteLabel,
        camera = { m -> CameraFrame(onCode = onCode, parse = parse, modifier = m) },
        email = email, onOtherAccount = onOtherAccount,
    )
    // Ask once on entry; a refusal leaves the typed fallback (6.6: camera denied -> 8-digit code entry).
    DisposableEffect(Unit) {
        if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA)
        onDispose {}
    }
}

/**
 * Stateless scanner page: camera (when allowed) and the typed-code field under it. [camera] draws the preview into the given modifier.
 * With [email] it is the studio "연결" page (account card, 8-digit code field, connect / other-account buttons); without it
 * (friend QR scan) the plain scanner page.
 */
@Composable
fun ScanContent(
    granted: Boolean,
    typed: String,
    wrong: Boolean,
    onTyped: (String) -> Unit,
    onAllow: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    title: String,
    hint: String,
    wrongText: String,
    pasteLabel: String,
    camera: @Composable (Modifier) -> Unit,
    email: String? = null,
    onOtherAccount: () -> Unit = {},
) {
    if (email != null) {
        val c = HeyTheme.colors
        EntryFrame(title = stringResource(R.string.join_title), onBack = onBack, backLabel = stringResource(R.string.scan_back)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = HeyTokens.Space.s24)) {
                EntryCard(Modifier.padding(top = HeyTokens.Space.s12)) {
                    InkText(stringResource(R.string.join_card_title), c.fgDefault, variant = HeyTextVariant.EntryBodyMedium)
                    InkText(email, c.fgDefault, Modifier.padding(top = HeyTokens.Space.s8), variant = HeyTextVariant.EntryBody)
                }
                if (granted) camera(Modifier.padding(top = HeyTokens.Space.s16))
                EntryCodeField(typed, onTyped, stringResource(R.string.join_code_placeholder), Modifier.padding(top = HeyTokens.Space.s16), wrong = wrong, onDone = onSubmit)
                if (wrong) InkText(wrongText, c.danger, Modifier.padding(top = HeyTokens.Space.s8), variant = HeyTextVariant.EntryDesc)
                InkText(stringResource(R.string.join_code_note), c.fgDefault, Modifier.padding(top = HeyTokens.Space.s16), variant = HeyTextVariant.EntryBody)
                InkText(stringResource(R.string.join_one_car), c.fgMuted, Modifier.padding(top = HeyTokens.Space.s16), variant = HeyTextVariant.EntryDesc)
                if (!granted) EntryButton(stringResource(R.string.scan_allow), onAllow, Modifier.padding(top = HeyTokens.Space.s16).fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = HeyTokens.Space.s24), horizontalArrangement = Arrangement.spacedBy(HeyTokens.Space.s12)) {
                    EntryButton(stringResource(R.string.join_go), onSubmit, primary = true)
                    EntryButton(stringResource(R.string.join_other), onOtherAccount, Modifier.weight(1f, fill = false))
                }
            }
        }
        return
    }
    PageScaffold {
        HeaderBar(onBack = onBack, backLabel = stringResource(R.string.scan_back))
        if (!granted) CharacterHero(Modifier.padding(bottom = HeyGap.G16.value), size = 96.dp)
        HeyText(title, variant = HeyTextVariant.PairTitle)
        if (hint.isNotEmpty()) HeyText(hint, Modifier.padding(top = HeyGap.G12.value), variant = HeyTextVariant.PairLead, tone = HeyTone.Muted)
        if (!granted) HeyText(stringResource(R.string.scan_permission), Modifier.padding(top = HeyGap.G8.value), variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
        if (granted) {
            camera(Modifier.padding(top = HeyGap.G24.value))
        } else {
            GlassButton(stringResource(R.string.scan_allow), onAllow, Modifier.padding(top = HeyGap.G24.value))
        }
        HeyDivider(Modifier.padding(top = HeyGap.G32.value))
        VStack(HeyGap.G16, Modifier.padding(top = HeyGap.G24.value, bottom = HeyGap.G32.value)) {
            HeyTextField(
                value = typed,
                onValueChange = onTyped,
                label = pasteLabel,
                error = if (wrong) wrongText else null,
            )
            HeyButton(stringResource(R.string.scan_paste_go), onSubmit, variant = HeyButtonVariant.Text)
        }
    }
}

@Composable
private fun CameraFrame(onCode: (String) -> Unit, parse: (String?) -> String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val main = ContextCompat.getMainExecutor(context)
        future.addListener({
            val provider = future.get()
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    val plane = image.planes[0]
                    val buf = plane.buffer
                    val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    val code = decodeQr(bytes, plane.rowStride, image.width, image.height)
                        ?.let(parse)
                    if (code != null && done.compareAndSet(false, true)) main.execute { onCode(code) }
                } finally {
                    image.close()
                }
            }
            val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            provider.unbindAll()
            runCatching { provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis) }
        }, main)
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }
    val shape = RoundedCornerShape(HeyTokens.Radius.lg)
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(shape).border(HeyTokens.Border.strong, HeyTheme.colors.borderDefault, shape)) {
        AndroidView({ preview }, Modifier.fillMaxWidth().aspectRatio(1f))
    }
}
