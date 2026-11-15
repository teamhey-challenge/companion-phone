package ai.hey.phone.nav

import android.net.Uri

/** Navigation routes. W1 = join/{code}, W2 = done, home/{tab} = 오늘·대화·친구·기억(W3)·설정, signup = phone-only sign-up. */
object Routes {
    const val JOIN = "join/{code}"
    const val DONE = "done"
    const val HOME = "home/{tab}"
    const val SIGNUP = "signup"

    fun join(code: String) = "join/$code"
    fun home(tab: String) = "home/$tab"
}

/** Pair code: 8 chars, upper-case letters and digits (server PAIR_CODE_RE; lenient here, server is the judge). */
private val CODE_RE = Regex("^[A-Za-z0-9]{4,16}$")

/**
 * The pair code in a deep link, or null. Accepts https://hey.lastmile.ltd/p/{code} (App Link) and heyparri://p/{code}.
 * `/p/done` is not a code.
 */
fun pairCodeFrom(uri: Uri?): String? {
    uri ?: return null
    val segs = uri.pathSegments.orEmpty()
    val code = when (uri.scheme) {
        "https" -> if (uri.host == "hey.lastmile.ltd" && segs.size == 2 && segs[0] == "p") segs[1] else null
        "heyparri" -> when {
            uri.host == "p" && segs.size == 1 -> segs[0]
            segs.size == 2 && segs[0] == "p" -> segs[1]
            else -> null
        }
        else -> null
    }
    return code?.takeIf { it != "done" && CODE_RE.matches(it) }?.uppercase()
}
