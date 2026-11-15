package ai.hey.phone.nav

/** Navigation routes. W1 = join/{code} (also the start screen: 구글로 시작 / 차 QR로 연결), consent = after Google login, W2 = done, home/{tab} = 오늘·대화·친구·기억(W3)·설정, friend/{code} = accept a friend invite. */
object Routes {
    const val JOIN = "join/{code}"
    const val DONE = "done"
    const val HOME = "home/{tab}"
    const val CONSENT = "consent"
    const val FRIEND_ACCEPT = "friend/{code}"

    fun join(code: String) = "join/$code"
    fun home(tab: String) = "home/$tab"
    fun friendAccept(code: String) = "friend/$code"
}

private val LINK_RE = Regex(
    """^(?:https://hey\.lastmile\.ltd/|heyparri://(?:/)?)(p|f)/([A-Za-z0-9]{4,16})(?:[/?#].*)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * The route a deep link opens, or null: https://hey.lastmile.ltd/p/{code} and heyparri://p/{code} -> join/{CODE} (pairing);
 * https://hey.lastmile.ltd/f/{code} and heyparri://f/{code} -> friend/{CODE} (accept a friend invite). `/p/done` is not a code.
 * The server is the judge of the code itself.
 */
fun deepLinkRoute(link: String?): String? {
    val m = LINK_RE.matchEntire(link?.trim().orEmpty()) ?: return null
    val code = m.groupValues[2]
    return when (m.groupValues[1].lowercase()) {
        "p" -> if (code.equals("done", ignoreCase = true)) null else Routes.join(code.uppercase())
        else -> Routes.friendAccept(code.uppercase())
    }
}
