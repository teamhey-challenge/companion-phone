package ai.hey.phone.feature.login

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.ui.ActionBar
import ai.hey.phone.ui.CharacterStill
import ai.hey.phone.ui.CheckRow
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeaderBar
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.PairMetrics
import ai.hey.phone.ui.VStack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** After the first Google sign-in: the consent the talk needs (required) and the memory choice (optional). Stateless. */
@Composable
fun ConsentScreen(
    consent: Boolean,
    memory: Boolean,
    saving: Boolean,
    error: ConsentError?,
    onConsent: (Boolean) -> Unit,
    onMemory: (Boolean) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(
        modifier,
        bottomBar = {
            ActionBar {
                GlassButton(
                    stringResource(if (saving) R.string.signup_saving else R.string.signup_start), onStart, Modifier.fillMaxWidth(),
                    enabled = !saving, dimmed = !consent, horizontalPadding = PairMetrics.BarPadH, textVariant = HeyTextVariant.PairAction,
                )
            }
        },
    ) {
        HeaderBar()
        Box(Modifier.fillMaxWidth().padding(top = HeyGap.G8.value), contentAlignment = Alignment.Center) {
            CharacterStill(stringResource(R.string.pair_brand_cd), size = 120.dp)
        }
        HeyText(stringResource(R.string.consent_title), Modifier.padding(top = PairMetrics.TitleTop), variant = HeyTextVariant.PairTitle)
        HeyText(stringResource(R.string.consent_lead), Modifier.padding(top = PairMetrics.LeadTop), variant = HeyTextVariant.PairLead, tone = HeyTone.Muted)
        VStack(HeyGap.G8, Modifier.padding(top = HeyGap.G24.value, bottom = HeyGap.G24.value)) {
            CheckRow(
                label = stringResource(R.string.signup_consent), checked = consent, onCheckedChange = onConsent, enabled = !saving,
                error = if (error == ConsentError.Required) stringResource(R.string.signup_err_consent) else null,
            )
            CheckRow(label = stringResource(R.string.signup_memory), checked = memory, onCheckedChange = onMemory, enabled = !saving)
            if (error == ConsentError.Failed) HeyText(stringResource(R.string.signup_err), variant = HeyTextVariant.Caption, tone = HeyTone.Danger)
        }
    }
}

@Composable
fun ConsentRoute(onDone: () -> Unit) {
    val app = HeyPhoneApp.from(LocalContext.current)
    val scope = rememberCoroutineScope()
    val model = remember { ConsentModel(app.api, scope, onDone) }
    ConsentScreen(
        consent = model.consent, memory = model.memory, saving = model.saving, error = model.error,
        onConsent = { model.consent = it }, onMemory = { model.memory = it }, onStart = model::start,
    )
}
