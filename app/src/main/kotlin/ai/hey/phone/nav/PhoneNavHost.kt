package ai.hey.phone.nav

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.feature.home.HomeRoute
import ai.hey.phone.feature.home.HomeTab
import ai.hey.phone.feature.login.ConsentRoute
import ai.hey.phone.feature.login.EntryRoute
import ai.hey.phone.feature.login.StartRoute
import ai.hey.phone.feature.pair.ConnectRoute
import ai.hey.phone.feature.pair.DoneRoute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private fun NavHostController.reset(route: String) = navigate(route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }

/**
 * D-58: the phone is only usable with a paired car. Start: token stored -> entry check (a car -> home, none -> connect screen),
 * otherwise the start screen. Nothing but the start, consent, entry and connect screens is reachable without a car: the server's
 * 403 `car_link_required` (and an unlink) sends the app back to the connect screen from anywhere; a 401 sends it to the start screen.
 * A pair link keeps the code through login; a friend link is kept too and accepted once a car is linked.
 */
@Composable
fun PhoneNavHost(hasToken: () -> Boolean, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val app = HeyPhoneApp.from(LocalContext.current)
    val start = remember { if (hasToken()) Routes.ENTRY else Routes.START }

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            val friendCode = deepLink.removePrefix("friend/").takeIf { it != deepLink }
            val pairCode = deepLink.removePrefix("connect/").takeIf { it != deepLink }
            when {
                friendCode != null -> { app.prefs.pendingFriendCode = friendCode; nav.reset(if (hasToken()) Routes.ENTRY else Routes.START) }
                pairCode != null -> { app.prefs.pendingPairCode = pairCode; nav.reset(if (hasToken()) Routes.ENTRY else Routes.START) }
                else -> nav.reset(deepLink)
            }
            onDeepLinkHandled()
        }
    }
    LaunchedEffect(Unit) { app.sessionLost.collect { nav.reset(Routes.START) } }
    LaunchedEffect(Unit) { app.carLinkLost.collect { nav.reset(Routes.connect(NO_CODE)) } }

    NavHost(nav, startDestination = start) {
        composable(Routes.START) {
            StartRoute(onLoggedIn = { outcome -> nav.reset(if (outcome.needsConsent) Routes.CONSENT else Routes.ENTRY) })
        }
        composable(Routes.CONSENT) {
            ConsentRoute(
                onDone = { nav.reset(Routes.ENTRY) },
                onDecline = { app.signedOut(); nav.reset(Routes.START) },
            )
        }
        composable(Routes.ENTRY) {
            EntryRoute(
                onToday = {
                    val friend = app.prefs.pendingFriendCode
                    nav.reset(if (friend != null) Routes.friendAccept(friend) else Routes.home(HomeTab.TODAY))
                },
                onConnect = { code -> nav.reset(Routes.connect(code)) },
            )
        }
        composable(Routes.CONNECT, arguments = listOf(navArgument("code") { type = NavType.StringType })) { entry ->
            ConnectRoute(
                code = entry.arguments?.getString("code") ?: NO_CODE,
                onClaimed = { nav.reset(Routes.DONE) },
                onLogout = { app.signedOut(); nav.reset(Routes.START) },
                onConsent = { nav.reset(Routes.CONSENT) },
            )
        }
        composable(Routes.DONE) {
            DoneRoute(onOpenMemory = { nav.reset(Routes.home(HomeTab.MEMORY)) })
        }
        composable(Routes.HOME, arguments = listOf(navArgument("tab") { type = NavType.StringType })) { entry ->
            HomeRoute(
                startTab = entry.arguments?.getString("tab") ?: HomeTab.TODAY,
                onSignedOut = { nav.reset(Routes.START) },
            )
        }
        composable(Routes.FRIEND_ACCEPT, arguments = listOf(navArgument("code") { type = NavType.StringType })) { entry ->
            HomeRoute(
                startTab = HomeTab.FRIENDS,
                acceptFriendCode = entry.arguments?.getString("code"),
                onSignedOut = { nav.reset(Routes.START) },
            )
        }
    }
}
