package ai.hey.phone.feature.login

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.ui.EntryButton
import ai.hey.phone.ui.EntryCard
import ai.hey.phone.ui.EntryCheck
import ai.hey.phone.ui.EntryFrame
import ai.hey.phone.ui.EntryInfoRow
import ai.hey.phone.ui.EntryLink
import ai.hey.phone.ui.HeyDivider
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.InkText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * After the first Google sign-in (studio phone-consent): the consent the talk needs (required) and the memory choice (optional).
 * The back arrow is "동의하지 않기": it signs out again. Stateless.
 */
@Composable
fun ConsentScreen(
    consent: Boolean,
    memory: Boolean,
    saving: Boolean,
    error: ConsentError?,
    onConsent: (Boolean) -> Unit,
    onMemory: (Boolean) -> Unit,
    onStart: () -> Unit,
    onDecline: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPrivacy: () -> Unit = {},
) {
    val c = HeyTheme.colors
    EntryFrame(modifier, title = stringResource(R.string.consent_bar), onBack = onDecline, backLabel = stringResource(R.string.consent_back)) {
        Column(Modifier.fillMaxWidth().padding(start = HeyTokens.Space.s24, end = HeyTokens.Space.s24, top = 12.dp, bottom = HeyTokens.Space.s24)) {
            InkText(stringResource(R.string.consent_lead), c.fgMuted, variant = HeyTextVariant.EntryDesc)
            EntryCard(Modifier.padding(top = HeyTokens.Space.s16)) {
                EntryInfoRow(HeyIcons.Mic, stringResource(R.string.consent_voice_title), stringResource(R.string.consent_voice_body))
                HeyDivider(Modifier.padding(vertical = HeyTokens.Space.s12))
                EntryInfoRow(HeyIcons.Book, stringResource(R.string.consent_memory_title), stringResource(R.string.consent_memory_body))
            }
            Column(Modifier.padding(top = HeyTokens.Space.s12)) {
                EntryCheck(stringResource(R.string.signup_consent), consent, onConsent, Modifier.padding(vertical = HeyTokens.Space.s4), enabled = !saving)
                EntryCheck(stringResource(R.string.signup_memory), memory, onMemory, Modifier.padding(vertical = HeyTokens.Space.s4), enabled = !saving)
            }
            if (error == ConsentError.Required) InkText(stringResource(R.string.signup_err_consent), c.danger, variant = HeyTextVariant.EntryDesc)
            if (error == ConsentError.Failed) InkText(stringResource(R.string.signup_err), c.danger, variant = HeyTextVariant.EntryDesc)
            EntryLink(stringResource(R.string.consent_privacy), onPrivacy, Modifier.padding(top = HeyTokens.Space.s8))
            EntryButton(
                stringResource(if (saving) R.string.signup_saving else R.string.signup_start), onStart,
                Modifier.padding(top = 36.dp), primary = true, enabled = !saving, dimmed = !consent,
            )
        }
    }
}

@Composable
fun ConsentRoute(onDone: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    val app = HeyPhoneApp.from(context)
    val scope = rememberCoroutineScope()
    val model = remember { ConsentModel(app.api, scope, onDone) }
    ConsentScreen(
        consent = model.consent, memory = model.memory, saving = model.saving, error = model.error,
        onConsent = { model.consent = it }, onMemory = { model.memory = it }, onStart = model::start, onDecline = onDecline,
        onPrivacy = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://hey.lastmile.ltd/privacy"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}
