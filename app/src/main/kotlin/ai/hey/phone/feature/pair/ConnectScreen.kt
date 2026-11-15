package ai.hey.phone.feature.pair

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.nav.NO_CODE
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.EntryFrame
import androidx.compose.foundation.layout.Column
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageMessage
import ai.hey.phone.ui.VStack
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@StringRes
fun JoinError.messageRes(): Int = when (this) {
    JoinError.Expired -> R.string.join_err_expired
    JoinError.Unknown -> R.string.join_err_unknown
    JoinError.AlreadyUsed -> R.string.join_err_used
    JoinError.CarLinkedToOther -> R.string.join_err_car_other
    JoinError.GoogleLinkedToOtherCar -> R.string.join_err_google_other
    JoinError.RecordConsent -> R.string.join_err_record
    JoinError.Verify -> R.string.join_err_verify
    JoinError.Rate -> R.string.join_err_rate
    JoinError.Network -> R.string.join_err_network
}

/** Car link after Google login: sending the claim, or the reason it failed (4.6). */
@Composable
fun ConnectScreen(
    ui: JoinUi,
    onRescan: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onConsent: () -> Unit,
) {
    EntryFrame(title = stringResource(R.string.join_title)) {
        Column(Modifier.padding(horizontal = HeyGap.G24.value)) {
            when (ui) {
                JoinUi.Loading -> VStack(HeyGap.G16, Modifier.fillMaxWidth().padding(top = HeyGap.G32.value)) {
                    CharacterHero(size = 150.dp)
                    HeyText(stringResource(R.string.join_loading), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                }
                is JoinUi.Failed -> PageMessage(stringResource(R.string.join_err_title), stringResource(ui.error.messageRes())) {
                    when (ui.error.action()) {
                        JoinAction.Rescan -> GlassButton(stringResource(R.string.join_act_rescan), onRescan)
                        JoinAction.Retry -> GlassButton(stringResource(R.string.join_act_retry), onRetry)
                        JoinAction.Close ->
                            if (ui.error == JoinError.RecordConsent) GlassButton(stringResource(R.string.join_act_consent), onConsent)
                            else GlassButton(stringResource(R.string.join_act_rescan), onRescan)
                    }
                }
            }
        }
    }
}

/**
 * `connect/{code}`: [code] == [NO_CODE] opens the scanner / 8-digit code entry with the one line "먼저 차에서 폰 연결 QR을 띄워 주세요"
 * (the camera may be denied); a remembered or linked code goes straight to the claim. Nothing else is reachable from here except
 * logout ([onLogout], the back arrow). [onClaimed] runs once the car is linked (the token is replaced).
 */
@Composable
fun ConnectRoute(code: String, onClaimed: () -> Unit, onLogout: () -> Unit, onConsent: () -> Unit) {
    val app = HeyPhoneApp.from(LocalContext.current)
    val scope = rememberCoroutineScope()
    var active by rememberSaveable(code) { mutableStateOf(code.takeIf { it != NO_CODE }) }
    val model = remember { ConnectModel(app.api, scope, onClaimed = { app.prefs.pendingPairCode = null; onClaimed() }) }
    val c = active
    if (c == null) {
        ScanRoute(
            onCode = { scanned -> active = scanned }, onBack = onLogout,
            email = app.prefs.googleEmail ?: stringResource(R.string.join_account_unknown), onOtherAccount = onLogout,
        )
        return
    }
    LaunchedEffect(c) { model.start(c) }
    ConnectScreen(
        ui = model.ui,
        onRescan = { active = null },
        onRetry = model::retry,
        onLogout = onLogout,
        onConsent = { app.prefs.pendingPairCode = c; onConsent() },
    )
}
