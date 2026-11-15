package ai.hey.phone.data

import android.content.Context

/** Non-secret local state: whether this account has no car, and a pending friend invite. */
class LocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hey_phone_local", Context.MODE_PRIVATE)

    /** True for an account made on the phone alone (sign-up, no car): there is nothing to unpair and no way back in after leaving. */
    var phoneOnly: Boolean
        get() = prefs.getBoolean("phone_only", false)
        set(v) { prefs.edit().putBoolean("phone_only", v).apply() }

    /** A friend invite code from a link opened before this phone was signed in; accepted once the home screen opens. */
    var pendingFriendCode: String?
        get() = prefs.getString("pending_friend", null)
        set(v) { prefs.edit().apply { if (v == null) remove("pending_friend") else putString("pending_friend", v) }.apply() }

    /** Reads and forgets [pendingFriendCode]. */
    fun takePendingFriendCode(): String? = pendingFriendCode?.also { pendingFriendCode = null }

    fun clear() = prefs.edit().clear().apply()
}
