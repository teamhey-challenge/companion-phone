package ai.hey.phone.feature.home

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.ui.ActionBar
import ai.hey.phone.ui.CheckRow
import ai.hey.phone.ui.CharacterStill
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeaderBar
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextField
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.PairMetrics
import ai.hey.phone.ui.VStack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** Phone-only sign-up: "뭐라고 불러 줄까?" + the consent the talk needs. Stateless. */
@Composable
fun SignupScreen(
    name: String,
    consent: Boolean,
    memory: Boolean,
    saving: Boolean,
    error: SignupError?,
    onName: (String) -> Unit,
    onConsent: (Boolean) -> Unit,
    onMemory: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(
        modifier,
        bottomBar = {
            ActionBar {
                GlassButton(
                    stringResource(if (saving) R.string.signup_saving else R.string.signup_start), onStart, Modifier.fillMaxWidth(),
                    enabled = !saving, dimmed = !consent || name.isBlank(), horizontalPadding = PairMetrics.BarPadH, textVariant = HeyTextVariant.PairAction,
                )
            }
        },
    ) {
        HeaderBar(onBack = onBack, backLabel = stringResource(R.string.scan_back))
        Box(Modifier.fillMaxWidth().padding(top = HeyGap.G8.value), contentAlignment = Alignment.Center) {
            CharacterStill(stringResource(R.string.pair_brand_cd), size = 120.dp)
        }
        HeyText(stringResource(R.string.signup_title), Modifier.padding(top = PairMetrics.TitleTop), variant = HeyTextVariant.PairTitle)
        HeyText(stringResource(R.string.signup_lead), Modifier.padding(top = PairMetrics.LeadTop), variant = HeyTextVariant.PairLead, tone = HeyTone.Muted)
        VStack(HeyGap.G8, Modifier.padding(top = HeyGap.G24.value, bottom = HeyGap.G24.value)) {
            HeyTextField(
                value = name, onValueChange = { onName(it.take(SettingsModel.MAX_NAME)) }, label = stringResource(R.string.signup_name_label),
                capitalize = false,
                error = if (error == SignupError.Name) stringResource(R.string.signup_err_name) else null,
            )
            CheckRow(
                label = stringResource(R.string.signup_consent), checked = consent, onCheckedChange = onConsent, enabled = !saving,
                error = if (error == SignupError.Consent) stringResource(R.string.signup_err_consent) else null,
            )
            CheckRow(label = stringResource(R.string.signup_memory), checked = memory, onCheckedChange = onMemory, enabled = !saving)
            when (error) {
                SignupError.Rate -> HeyText(stringResource(R.string.signup_err_rate), variant = HeyTextVariant.Caption, tone = HeyTone.Danger)
                SignupError.Generic -> HeyText(stringResource(R.string.signup_err), variant = HeyTextVariant.Caption, tone = HeyTone.Danger)
                else -> Unit
            }
        }
    }
}

@Composable
fun SignupRoute(onDone: () -> Unit, onBack: () -> Unit) {
    val app = HeyPhoneApp.from(LocalContext.current)
    val scope = rememberCoroutineScope()
    val model = remember { SignupModel(app.api, scope) { app.prefs.phoneOnly = true; onDone() } }
    BackHandler(onBack = onBack)
    SignupScreen(
        name = model.name, consent = model.consent, memory = model.memory, saving = model.saving, error = model.error,
        onName = { model.name = it }, onConsent = { model.consent = it }, onMemory = { model.memory = it },
        onStart = model::start, onBack = onBack,
    )
}
