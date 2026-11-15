package ai.hey.phone.feature.home

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.data.PushFrame
import ai.hey.phone.feature.friends.FriendsRoute
import ai.hey.phone.feature.memory.MemoryRoute
import ai.hey.phone.feature.talk.TalkRoute
import ai.hey.phone.ui.BottomNav
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.NavTab
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object HomeTab {
    const val TODAY = "today"
    const val TALK = "talk"
    const val FRIENDS = "friends"
    const val MEMORY = "memory"
    const val SETTINGS = "settings"
}

/**
 * Signed-in home: 오늘 · 대화 · 친구 · 기억 · 설정 with a bottom bar. [onSignedOut] runs when the server says the token is dead
 * (401/403) or the user leaves this phone; the caller then goes back to the entry screen. Starts the inbox stream and the polls.
 */
@Composable
fun HomeRoute(startTab: String, onSignedOut: () -> Unit, onConnectCar: () -> Unit, acceptFriendCode: String? = null) {
    val context = LocalContext.current
    val app = HeyPhoneApp.from(context)
    val scope = rememberCoroutineScope()
    // A friend link opened while signed out is kept in prefs; the first home screen after sign-up / pairing accepts it.
    val friendCode = rememberSaveable { mutableStateOf(acceptFriendCode ?: app.prefs.takePendingFriendCode()) }.value
    var tab by rememberSaveable { mutableStateOf(if (friendCode != null) HomeTab.FRIENDS else startTab) }
    val out = rememberUpdatedLambda(onSignedOut)
    val unauthorized = { app.signedOut(); out() }

    LaunchedEffect(Unit) { app.startBackground(); app.refreshLook() }

    val tabs = listOf(
        NavTab(HomeTab.TODAY, stringResource(R.string.tab_today), HeyIcons.Book),
        NavTab(HomeTab.TALK, stringResource(R.string.tab_talk), HeyIcons.Message),
        NavTab(HomeTab.FRIENDS, stringResource(R.string.tab_friends), HeyIcons.Users),
        NavTab(HomeTab.MEMORY, stringResource(R.string.tab_memory), HeyIcons.Pin),
        NavTab(HomeTab.SETTINGS, stringResource(R.string.tab_settings), HeyIcons.Gear),
    )
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.TODAY -> {
                    val model = remember { TodayModel(app.api, scope, { LocalDate.now(ZoneId.of("Asia/Seoul")).toString() }, unauthorized) }
                    LaunchedEffect(Unit) { model.load() }
                    // New trip line / diary while this tab is open.
                    LaunchedEffect(Unit) { app.events.collect { if (it is PushFrame.TripDone || it is PushFrame.DiaryReady) model.load() } }
                    val d = LocalDate.now(ZoneId.of("Asia/Seoul"))
                    val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
                    TodayScreen(model.ui, model.name, stringResource(R.string.today_date, d.monthValue, d.dayOfMonth, dow), onRetry = model::load, onOpenMemory = { tab = HomeTab.MEMORY })
                }
                HomeTab.TALK -> TalkRoute(onUnauthorized = unauthorized)
                HomeTab.FRIENDS -> FriendsRoute(onUnauthorized = unauthorized, acceptCode = friendCode)
                HomeTab.MEMORY -> MemoryRoute(
                    onBack = { tab = HomeTab.TODAY },
                    onUnpaired = { app.signedOut(); out() },
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                HomeTab.SETTINGS -> SettingsRoute(onUnauthorized = unauthorized, onLogout = unauthorized, onConnectCar = onConnectCar)
            }
        }
        BottomNav(tabs, tab, { tab = it })
    }
}

@Composable
private fun rememberUpdatedLambda(f: () -> Unit): () -> Unit {
    val s = androidx.compose.runtime.rememberUpdatedState(f)
    return { s.value() }
}
