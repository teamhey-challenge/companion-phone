package ai.hey.phone.feature.login

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.nav.NO_CODE
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageMessage
import ai.hey.phone.ui.PageScaffold
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.IOException

/** After login + consent: asks the account whether a car is bound and branches with [decideEntry]. A failed read can be retried. */
@Composable
fun EntryRoute(onToday: () -> Unit, onConnect: (String) -> Unit) {
    val app = HeyPhoneApp.from(LocalContext.current)
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        val linked = try { app.api.account().linkedCar } catch (_: IOException) { failed = true; return@LaunchedEffect }
        when (val e = decideEntry(linked, app.prefs.pendingPairCode)) {
            Entry.Today -> { app.prefs.pendingPairCode = null; onToday() }
            is Entry.Connect -> onConnect(e.code)
            Entry.Scan -> onConnect(NO_CODE)
        }
    }
    PageScaffold {
        if (failed) {
            PageMessage(stringResource(R.string.login_err_network), "") { GlassButton(stringResource(R.string.join_act_retry), { attempt++ }) }
        } else {
            CharacterHero(size = 150.dp)
            HeyText(stringResource(R.string.join_loading), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
        }
    }
}
