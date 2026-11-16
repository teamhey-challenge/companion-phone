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
 * the phone is only usable with a paired car. Start: token stored -> entry check (a car -> home, none -> connect screen),
 * otherwise the start screen. Nothing but the start, consent, entry and connect screens is reachable without a car: the server's
 * 403 `car_link_required` (and an unlink) sends the app back to the connect screen from anywhere; a 401 sends it to the start screen.
 * A pair link keeps the code through login; a friend link is kept too and accepted once a car is linked.
 */
@Composable
fun PhoneNavHost(hasToken: () -> Boolean, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val app = HeyPhoneApp.from(LocalContext.current)
    val start = remember { coldStartRoute(hasToken()) }

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            val plan = deepLinkPlan(deepLink, hasToken())
            plan.friendCode?.let { app.prefs.pendingFriendCode = it }
            plan.pairCode?.let { app.prefs.pendingPairCode = it }
            nav.reset(plan.route)
            onDeepLinkHandled()
        }
    }
    LaunchedEffect(Unit) { app.sessionLost.collect { nav.reset(routeFor(Session.SignedOut)) } }
    LaunchedEffect(Unit) { app.carLinkLost.collect { nav.reset(routeFor(Session.CarLinkRequired())) } }

    NavHost(nav, startDestination = start) {
        composable(Routes.START) {
            StartRoute(onLoggedIn = { outcome -> nav.reset(afterLoginRoute(outcome.needsConsent)) })
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
                    nav.reset(todayRoute(app.prefs.pendingFriendCode))
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
            DoneRoute(onOpenToday = { nav.reset(Routes.home(HomeTab.TODAY)) })
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
