package ai.hey.phone.nav

import ai.hey.phone.feature.home.HomeRoute
import ai.hey.phone.feature.home.HomeTab
import ai.hey.phone.feature.home.SignupRoute
import ai.hey.phone.feature.pair.DoneRoute
import ai.hey.phone.feature.pair.JoinRoute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/** Code the join route shows when the app was opened without a (valid) link: the screen then shows "QR을 다시 찍어 주세요". */
const val NO_CODE = "none"

/**
 * Screens: join/{code} (W1), done (W2), home/{tab} (오늘·대화·친구·기억 W3·설정), signup (phone-only), friend/{code} (home on the
 * 친구 tab, accepting the invite). Start: paired (token stored) -> home, otherwise join/none. A pair link goes to join/{code}; a
 * friend link goes to friend/{code} when signed in, otherwise the code is kept and accepted right after sign-up / pairing.
 */
@Composable
fun PhoneNavHost(hasToken: () -> Boolean, deepLink: String?, savePendingFriend: (String) -> Unit, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val start = remember { if (hasToken()) Routes.home(HomeTab.TODAY) else Routes.join(NO_CODE) }

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            val friendCode = deepLink.removePrefix("friend/").takeIf { it != deepLink }
            when {
                friendCode == null -> nav.navigate(deepLink) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                hasToken() -> nav.navigate(deepLink) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                else -> {
                    savePendingFriend(friendCode)
                    nav.navigate(Routes.join(NO_CODE)) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                }
            }
            onDeepLinkHandled()
        }
    }

    NavHost(nav, startDestination = start) {
        composable(Routes.JOIN, arguments = listOf(navArgument("code") { type = NavType.StringType })) { entry ->
            JoinRoute(
                code = entry.arguments?.getString("code") ?: NO_CODE,
                onClaimed = { nav.navigate(Routes.DONE) { popUpTo(0) { inclusive = true } } },
                onSignup = { nav.navigate(Routes.SIGNUP) },
            )
        }
        composable(Routes.SIGNUP) {
            SignupRoute(
                onDone = { nav.navigate(Routes.home(HomeTab.TALK)) { popUpTo(0) { inclusive = true } } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.DONE) {
            DoneRoute(onOpenMemory = { nav.navigate(Routes.home(HomeTab.MEMORY)) { popUpTo(0) { inclusive = true } } })
        }
        composable(Routes.HOME, arguments = listOf(navArgument("tab") { type = NavType.StringType })) { entry ->
            HomeRoute(
                startTab = entry.arguments?.getString("tab") ?: HomeTab.TODAY,
                onSignedOut = { nav.navigate(Routes.join(NO_CODE)) { popUpTo(0) { inclusive = true } } },
            )
        }
        composable(Routes.FRIEND_ACCEPT, arguments = listOf(navArgument("code") { type = NavType.StringType })) { entry ->
            HomeRoute(
                startTab = HomeTab.FRIENDS,
                acceptFriendCode = entry.arguments?.getString("code"),
                onSignedOut = { nav.navigate(Routes.join(NO_CODE)) { popUpTo(0) { inclusive = true } } },
            )
        }
    }
}
