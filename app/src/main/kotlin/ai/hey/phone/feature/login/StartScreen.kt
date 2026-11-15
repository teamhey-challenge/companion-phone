package ai.hey.phone.feature.login

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.ui.CharacterStill
import ai.hey.phone.ui.EntryButton
import ai.hey.phone.ui.EntryFrame
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.InkText
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/**
 * Start screen (studio phone-start): "Google로 시작" and "차 QR로 연결" both sign in with Google first, then consent, then the
 * car link. [notice] is why the server logged this phone out (6.6), [error] why the last sign-in failed. A closed picker leaves
 * everything as it was.
 */
@Composable
fun StartScreen(
    busy: Boolean,
    error: LoginError?,
    @StringRes notice: Int?,
    onStart: () -> Unit,
) {
    val c = HeyTheme.colors
    EntryFrame {
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = HeyTokens.Space.s24),
            verticalArrangement = Arrangement.spacedBy(HeyTokens.Space.s16),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = HeyTokens.Space.s16), contentAlignment = Alignment.Center) {
                CharacterStill(stringResource(R.string.pair_brand_cd), size = 144.dp)
            }
            InkText(stringResource(R.string.login_title), c.fgDefault, variant = HeyTextVariant.EntryH1)
            InkText(stringResource(R.string.login_lead), c.fgDefault, Modifier.padding(bottom = HeyTokens.Space.s16), variant = HeyTextVariant.EntryBody)
            EntryButton(stringResource(R.string.login_start), onStart, Modifier.fillMaxWidth(), primary = true, enabled = !busy, loading = busy)
            EntryButton(stringResource(R.string.login_qr), onStart, Modifier.fillMaxWidth(), enabled = !busy, leading = HeyIcons.Qr)
            InkText(stringResource(R.string.login_note), c.fgMuted, variant = HeyTextVariant.EntryDesc)
            notice?.let { HeyNotice(stringResource(it), danger = false) }
            error?.let { HeyNotice(stringResource(it.messageRes()), danger = true) }
        }
    }
}

@Composable
fun StartRoute(onLoggedIn: (LoginOutcome) -> Unit) {
    val context = LocalContext.current
    val app = HeyPhoneApp.from(context)
    val scope = rememberCoroutineScope()
    val notice = remember { app.prefs.takeStartNotice()?.messageRes() }
    val login = remember {
        LoginModel(
            app.api, scope, app.google.configured,
            {
                app.google.fetch(context).also { if (it is ai.hey.phone.data.GoogleSignInResult.Token) app.prefs.googleEmail = it.email }
            },
            onNoAccount = { context.startActivity(addGoogleAccountIntent()) },
            onDone = onLoggedIn,
        )
    }
    StartScreen(busy = login.busy, error = login.error, notice = notice, onStart = login::signIn)
}

/** 6.6: no Google account on the phone -> the system "add account" screen, Google accounts only. */
fun addGoogleAccountIntent(): android.content.Intent =
    android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT)
        .putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
