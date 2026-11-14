package ai.hey.phone.feature.trips

import ai.hey.phone.HeyPhoneApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** "함께 다닌 길" wired to the server. [onUnpaired] runs when the server answers 401/403 (the token is cleared first). */
@Composable
fun TripsRoute(onBack: () -> Unit, onUnpaired: () -> Unit) {
    val app = HeyPhoneApp.from(LocalContext.current)
    val scope = rememberCoroutineScope()
    val leave = rememberUpdatedState(onUnpaired)
    val model = remember { TripsModel(app.api, scope, onUnauthorized = { app.tokens.clear(); leave.value() }) }
    LaunchedEffect(Unit) { model.load() }
    TripsScreen(
        state = model.state,
        notice = model.notice,
        onBack = onBack,
        onRetry = model::load,
        tone = model.tone,
        onTone = model::chooseTone,
        onDelete = model::delete,
        onDeleteAll = model::deleteAll,
    )
}
