package ai.hey.phone.nav

import ai.hey.phone.feature.home.HomeTab

/**
 * Every "where does the app start / go next" decision of the phone, free of Android so a table can pin it.
 * The phone is only usable with a paired car: signed out -> start, record consent missing -> consent, the server's
 * `car_link_required` -> the connect screen, a car -> home (or the friend invite that waits for it).
 */
sealed interface Session {
    /** No token (never signed in, 401, logout, declined consent). */
    data object SignedOut : Session

    /** Signed in, the record consent is not answered yet. */
    data object NeedsConsent : Session

    /** Signed in and consented, no car bound (or the server said 403 `car_link_required`): [pendingPairCode] is a remembered car code. */
    data class CarLinkRequired(val pendingPairCode: String? = null) : Session

    /** Signed in, consented, a car is linked: [pendingFriendCode] is an invite that waited for it. */
    data class Linked(val pendingFriendCode: String? = null) : Session
}

/** The one screen each [Session] belongs on. */
fun routeFor(session: Session): String = when (session) {
    Session.SignedOut -> Routes.START
    Session.NeedsConsent -> Routes.CONSENT
    is Session.CarLinkRequired -> Routes.connect(session.pendingPairCode ?: NO_CODE)
    is Session.Linked -> todayRoute(session.pendingFriendCode)
}

/** Cold start: a stored token goes to the entry check (it asks the server for the car), nothing stored shows the start screen. */
fun coldStartRoute(hasToken: Boolean): String = if (hasToken) Routes.ENTRY else Routes.START

/** A finished Google sign-in: the account that has not answered the record consent yet goes to the consent screen first. */
fun afterLoginRoute(needsConsent: Boolean): String = if (needsConsent) Routes.CONSENT else Routes.ENTRY

/** Today, unless a friend invite was kept while no car was linked. */
fun todayRoute(pendingFriendCode: String?): String =
    if (pendingFriendCode != null) Routes.friendAccept(pendingFriendCode) else Routes.home(HomeTab.TODAY)

/** What a link that arrived while the app runs or starts does: which code to remember and where to reset to. */
data class DeepLinkPlan(val pairCode: String? = null, val friendCode: String? = null, val route: String)

/** [deepLink] is a route from [deepLinkRoute]; codes are kept through login, so a signed-out app starts at [Routes.START]. */
fun deepLinkPlan(deepLink: String, hasToken: Boolean): DeepLinkPlan {
    val friend = deepLink.removePrefix("friend/").takeIf { it != deepLink }
    val pair = deepLink.removePrefix("connect/").takeIf { it != deepLink }
    return when {
        friend != null -> DeepLinkPlan(friendCode = friend, route = coldStartRoute(hasToken))
        pair != null -> DeepLinkPlan(pairCode = pair, route = coldStartRoute(hasToken))
        else -> DeepLinkPlan(route = deepLink)
    }
}
