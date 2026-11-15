package ai.hey.phone.feature.login

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeaderBar
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.PageMessage
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.VStack
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * Start screen (D-58): one button, 시작하기 -> Google login -> consent -> connect a car. [notice] is why the server logged this phone out
 * (6.6), [error] why the last sign-in failed. A closed picker leaves everything as it was.
 */
@Composable
fun StartScreen(
    busy: Boolean,
    error: LoginError?,
    @StringRes notice: Int?,
    onStart: () -> Unit,
) {
    PageScaffold {
        HeaderBar()
        PageMessage(stringResource(R.string.login_title), stringResource(R.string.login_lead)) {
            VStack(HeyGap.G8) {
                GlassButton(stringResource(R.string.login_start), onStart, enabled = !busy, loading = busy)
                notice?.let { HeyNotice(stringResource(it), danger = false) }
                error?.let { HeyNotice(stringResource(it.messageRes()), danger = true) }
            }
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
            app.api, scope, app.google.configured, { app.google.fetch(context) },
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
