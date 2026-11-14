package ai.hey.phone.feature.pair

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.PairStatus
import ai.hey.phone.nav.NO_CODE
import ai.hey.phone.ui.ActionBar
import ai.hey.phone.ui.CharacterStill
import ai.hey.phone.ui.CheckRow
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeaderBar
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyButtonVariant
import ai.hey.phone.ui.HeyDivider
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.InfoRow
import ai.hey.phone.ui.PairMetrics
import ai.hey.phone.ui.PageMessage
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.ToggleRow
import ai.hey.phone.ui.VStack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.IOException

/** What the W1 screen shows. [Ready] is the consent form; the others are the states in front of it. */
sealed interface JoinUi {
    /** Looking the code up on the server. */
    data object Loading : JoinUi

    /** Code is valid and open: the consent form. */
    data class Ready(
        val memory: Boolean = false,
        val voice: Boolean = false,
        val saving: Boolean = false,
        /** Notice line under the form (declined, required missing, save failed). */
        val notice: Notice? = null,
    ) : JoinUi

    /** Expired, already used or unknown code: "QR이 만료됐거나 맞지 않아요". */
    data object Invalid : JoinUi

    /** The lookup itself failed (no network). */
    data object LoadFailed : JoinUi

    /** App opened without a link: ask for the QR. */
    data object NoCode : JoinUi
}

enum class Notice { Declined, VoiceRequired, SaveFailed }

/**
 * W1. Stateless: every press is a callback. The start button looks blocked until the required consent is ticked but
 * still takes the press, so it can say why nothing happens.
 */
@Composable
fun JoinScreen(
    ui: JoinUi,
    onMemory: (Boolean) -> Unit,
    onVoice: (Boolean) -> Unit,
    onStart: () -> Unit,
    onDecline: () -> Unit,
    onScan: () -> Unit,
    onRetry: () -> Unit,
) {
    val ready = ui as? JoinUi.Ready
    PageScaffold(
        bottomBar = if (ready == null) null else ({
            ActionBar {
                GlassButton(
                    text = stringResource(if (ready.saving) R.string.w1_saving else R.string.w1_start),
                    onClick = onStart,
                    enabled = !ready.saving,
                    dimmed = !ready.voice,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalPadding = PairMetrics.BarPadH,
                )
                HeyButton(stringResource(R.string.w1_decline), onDecline, variant = HeyButtonVariant.Text, enabled = !ready.saving)
            }
        }),
    ) {
        HeaderBar()
        when (ui) {
            is JoinUi.Ready -> Form(ui, onMemory, onVoice)
            JoinUi.Invalid -> PageMessage(HeyIcons.Qr, stringResource(R.string.w1_invalid_title), stringResource(R.string.w1_invalid_body)) {
                GlassButton(stringResource(R.string.w1_scan), onScan)
            }
            JoinUi.NoCode -> PageMessage(HeyIcons.Qr, stringResource(R.string.w1_none_title), stringResource(R.string.w1_none_body)) {
                GlassButton(stringResource(R.string.w1_scan), onScan)
            }
            JoinUi.LoadFailed -> PageMessage(HeyIcons.Warning, stringResource(R.string.w1_load_failed), "") {
                GlassButton(stringResource(R.string.w1_retry), onRetry)
            }
            JoinUi.Loading -> HeyText(stringResource(R.string.w1_loading), tone = HeyTone.Muted, modifier = Modifier.padding(top = 32.dp))
        }
    }
}

@Composable
private fun Form(ui: JoinUi.Ready, onMemory: (Boolean) -> Unit, onVoice: (Boolean) -> Unit) {
    var providers by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = PairMetrics.CharacterTop), contentAlignment = Alignment.Center) {
        CharacterStill(stringResource(R.string.pair_brand_cd), size = PairMetrics.CharacterSize)
    }
    HeyText(
        stringResource(R.string.pair_caption),
        Modifier.fillMaxWidth().padding(top = PairMetrics.CaptionTop),
        variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted, align = TextAlign.Center,
    )
    HeyText(stringResource(R.string.w1_title), Modifier.padding(top = PairMetrics.TitleTop), variant = HeyTextVariant.PairTitle)
    HeyText(stringResource(R.string.w1_subtitle), Modifier.padding(top = PairMetrics.LeadTop), variant = HeyTextVariant.PairLead, tone = HeyTone.Muted)
    VStack(HeyGap.G16, Modifier.padding(top = PairMetrics.InfoTop)) {
        InfoRow(HeyIcons.Mic, stringResource(R.string.w1_point_voice_title), stringResource(R.string.w1_point_voice_body))
        InfoRow(HeyIcons.Book, stringResource(R.string.w1_point_memory_title), stringResource(R.string.w1_point_memory_body), Modifier.padding(top = PairMetrics.InfoGap - HeyGap.G16.value))
    }
    ToggleRow(
        label = stringResource(R.string.w1_toggle_memory),
        checked = ui.memory,
        onCheckedChange = onMemory,
        description = stringResource(R.string.w1_toggle_memory_body),
        enabled = !ui.saving,
        modifier = Modifier.padding(top = PairMetrics.ToggleTop),
    )
    HeyDivider(Modifier.padding(top = HeyGap.G8.value))
    CheckRow(
        label = stringResource(R.string.w1_check_voice),
        checked = ui.voice,
        onCheckedChange = onVoice,
        enabled = !ui.saving,
        modifier = Modifier.padding(top = HeyGap.G8.value),
    )
    HeyButton(stringResource(R.string.w1_policy), { providers = !providers }, variant = HeyButtonVariant.Text, modifier = Modifier.padding(top = HeyGap.G8.value))
    if (providers) {
        VStack(HeyGap.G8, Modifier.padding(top = HeyGap.G8.value)) {
            HeyText(stringResource(R.string.w1_notice_voice), variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
            HeyText(stringResource(R.string.w1_notice_providers), variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
        }
    }
    ui.notice?.let {
        val text = stringResource(
            when (it) {
                Notice.Declined -> R.string.w1_declined
                Notice.VoiceRequired -> R.string.w1_required
                Notice.SaveFailed -> R.string.w1_failed
            },
        )
        HeyNotice(text, Modifier.padding(top = PairMetrics.NoticeTop), danger = it != Notice.Declined)
    }
    Box(Modifier.padding(bottom = HeyGap.G24.value))
}

/**
 * W1 route for `join/{code}`. [code] == [NO_CODE] means the app was opened without a link: the scanner entry is shown
 * and the scanned (or typed) code replaces it. [onClaimed] runs once the server accepted the claim and the phone token is stored.
 */
@Composable
fun JoinRoute(code: String, onClaimed: () -> Unit) {
    val context = LocalContext.current
    val app = HeyPhoneApp.from(context)
    val scope = rememberCoroutineScope()
    var active by rememberSaveable(code) { mutableStateOf(code.takeIf { it != NO_CODE }) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var memory by rememberSaveable { mutableStateOf(false) }
    var voice by rememberSaveable { mutableStateOf(false) }
    var ui: JoinUi by remember(active) { mutableStateOf(if (active == null) JoinUi.NoCode else JoinUi.Loading) }
    var attempt by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(active, attempt) {
        val c = active ?: return@LaunchedEffect
        ui = JoinUi.Loading
        ui = try {
            when (app.api.pairing(c).status) {
                PairStatus.PENDING -> JoinUi.Ready(memory = memory, voice = voice)
                else -> JoinUi.Invalid
            }
        } catch (_: IOException) {
            JoinUi.LoadFailed
        }
    }

    if (scanning) {
        ScanRoute(onCode = { scanned -> active = scanned; scanning = false }, onBack = { scanning = false })
        return
    }

    // The checkbox/switch live outside [ui] so a failed claim keeps both ticks (PRD 4.9).
    val shown = (ui as? JoinUi.Ready)?.copy(memory = memory, voice = voice) ?: ui
    JoinScreen(
        ui = shown,
        onMemory = { memory = it; (ui as? JoinUi.Ready)?.let { r -> ui = r.copy(notice = null) } },
        onVoice = { voice = it; (ui as? JoinUi.Ready)?.let { r -> ui = r.copy(notice = null) } },
        onStart = {
            val r = ui as? JoinUi.Ready ?: return@JoinScreen
            val c = active ?: return@JoinScreen
            if (!voice) {
                ui = r.copy(notice = Notice.VoiceRequired)
            } else if (!r.saving) {
                ui = r.copy(saving = true, notice = null)
                scope.launch {
                    try {
                        app.api.claim(c, record = true, memory = memory)
                        onClaimed()
                    } catch (e: ApiException) {
                        // 400 = record missing (cannot happen here), 404/409/410 = the code died while the form was open.
                        ui = if (e.code == 400) r.copy(saving = false, notice = Notice.SaveFailed) else JoinUi.Invalid
                    } catch (_: IOException) {
                        ui = r.copy(saving = false, notice = Notice.SaveFailed)
                    }
                }
            }
        },
        onDecline = { (ui as? JoinUi.Ready)?.let { r -> ui = r.copy(notice = Notice.Declined) } },
        onScan = { scanning = true },
        onRetry = { attempt++ },
    )
}
