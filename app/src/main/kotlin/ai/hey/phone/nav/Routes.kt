package ai.hey.phone.nav

/**
 * Navigation routes (4.6). start = 구글로 시작 / 차 QR로 연결 (both go through Google login first), consent = after Google login,
 * entry = decides Today / car link after login + consent, connect/{code} = link a car (code [NO_CODE] = scanner / code entry),
 * done = W2, home/{tab} = 오늘·대화·친구·기억·설정, friend/{code} = accept a friend invite.
 */
object Routes {
    const val START = "start"
    const val CONSENT = "consent"
    const val ENTRY = "entry"
    const val CONNECT = "connect/{code}"
    const val DONE = "done"
    const val HOME = "home/{tab}"
    const val FRIEND_ACCEPT = "friend/{code}"

    fun connect(code: String) = "connect/$code"
    fun home(tab: String) = "home/$tab"
    fun friendAccept(code: String) = "friend/$code"
}

/** Code the connect route gets when no code is known yet: the scanner (or the typed code) comes first. */
const val NO_CODE = "none"

private val LINK_RE = Regex(
    """^(?:https://hey\.lastmile\.ltd/|heyparri://(?:/)?)(p|f)/([A-Za-z0-9]{4,16})(?:[/?#].*)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * The route a deep link opens, or null: https://hey.lastmile.ltd/p/{code} and heyparri://p/{code} -> connect/{CODE} (car pairing);
 * https://hey.lastmile.ltd/f/{code} and heyparri://f/{code} -> friend/{CODE} (accept a friend invite). `/p/done` is not a code.
 * The server is the judge of the code itself.
 */
fun deepLinkRoute(link: String?): String? {
    val m = LINK_RE.matchEntire(link?.trim().orEmpty()) ?: return null
    val code = m.groupValues[2]
    return when (m.groupValues[1].lowercase()) {
        "p" -> if (code.equals("done", ignoreCase = true)) null else Routes.connect(code.uppercase())
        else -> Routes.friendAccept(code.uppercase())
    }
}
