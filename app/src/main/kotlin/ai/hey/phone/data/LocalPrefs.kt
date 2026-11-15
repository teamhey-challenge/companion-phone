package ai.hey.phone.data

import android.content.Context

/** Non-secret local state: the check-in time (so the daily nudge works offline) and the display name last saved. */
class LocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hey_phone_local", Context.MODE_PRIVATE)

    var checkinTime: String?
        get() = prefs.getString("checkin", null)
        set(v) { prefs.edit().apply { if (v == null) remove("checkin") else putString("checkin", v) }.apply() }

    var displayName: String?
        get() = prefs.getString("name", null)
        set(v) { prefs.edit().apply { if (v == null) remove("name") else putString("name", v) }.apply() }

    fun clear() = prefs.edit().clear().apply()
}
