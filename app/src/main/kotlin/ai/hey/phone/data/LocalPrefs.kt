package ai.hey.phone.data

import android.content.Context

/** Non-secret local state: a pending friend invite, a pending car code, and why the server logged the phone out. */
class LocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hey_phone_local", Context.MODE_PRIVATE)
    /** A friend invite code from a link opened before this phone was signed in; accepted once the home screen opens. */
    var pendingFriendCode: String?
        get() = prefs.getString("pending_friend", null)
        set(v) { prefs.edit().apply { if (v == null) remove("pending_friend") else putString("pending_friend", v) }.apply() }

    /** Reads and forgets [pendingFriendCode]. */
    fun takePendingFriendCode(): String? = pendingFriendCode?.also { pendingFriendCode = null }

    /** A car pairing code remembered from a `/p/<code>` link or a scan until it is claimed after the Google login (4.6). */
    var pendingPairCode: String?
        get() = prefs.getString("pending_pair", null)
        set(v) { prefs.edit().apply { if (v == null) remove("pending_pair") else putString("pending_pair", v) }.apply() }

    /** The Google account address shown on the car-link page ("이 계정으로 연결할까요?"); the id token itself is never stored. */
    var googleEmail: String?
        get() = prefs.getString("google_email", null)
        set(v) { prefs.edit().apply { if (v == null) remove("google_email") else putString("google_email", v) }.apply() }

    /** Why the phone was logged out by the server; the start screen says it once. Survives [clear]. */
    var startNotice: SignOutNotice?
        get() = prefs.getString("start_notice", null)?.let { n -> SignOutNotice.entries.firstOrNull { it.name == n } }
        set(v) { prefs.edit().apply { if (v == null) remove("start_notice") else putString("start_notice", v.name) }.apply() }

    fun takeStartNotice(): SignOutNotice? = startNotice?.also { startNotice = null }

    fun clear() {
        val notice = prefs.getString("start_notice", null)
        prefs.edit().clear().apply { if (notice != null) putString("start_notice", notice) }.apply()
    }
}
