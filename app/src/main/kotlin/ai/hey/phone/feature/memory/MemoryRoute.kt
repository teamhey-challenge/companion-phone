package ai.hey.phone.feature.memory

import androidx.compose.foundation.layout.safeDrawing
import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.ui.HeyTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * W3 wired to the server. [onUnpaired] runs after "연결 해제" succeeded (the server ended the pairing and the local token is
 * gone) and also when the server answers 401/403 because the car ended the pairing (the token is cleared first).
 */
@Composable
fun MemoryRoute(onBack: () -> Unit, onUnpaired: () -> Unit, insets: androidx.compose.foundation.layout.WindowInsets = androidx.compose.foundation.layout.WindowInsets.safeDrawing) {
    val app = HeyPhoneApp.from(LocalContext.current)
    val scope = rememberCoroutineScope()
    val leave = rememberUpdatedState(onUnpaired)
    val model = remember {
        MemoryModel(
            api = app.api,
            scope = scope,
            onUnauthorized = { app.tokens.clear(); leave.value() },
            onUnpaired = { leave.value() },
        )
    }
    LaunchedEffect(Unit) { model.load(); app.refreshLook() }
    MemoryScreen(
        insets = insets,
        canUnpair = !app.prefs.phoneOnly,
        list = model.list,
        speech = model.speech,
        speechSaving = model.speechSaving,
        unpairing = model.unpairing,
        notice = model.notice,
        onBack = onBack,
        onRetry = model::load,
        onDelete = model::delete,
        onDeleteAll = model::deleteAll,
        onLoadMore = model::loadMore,
        onSpeech = model::chooseSpeech,
        onUnpair = model::unpair,
    )
}
