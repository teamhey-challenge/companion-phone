package ai.hey.phone.feature.login

/** Where the app goes once login and consent are done (the phone is only usable with a car). */
sealed interface Entry {
    data object Today : Entry

    /** A remembered car code is claimed. */
    data class Connect(val code: String) : Entry

    /** No car and no remembered code: the connect screen (scan / 8-digit code) with the one-line explanation. */
    data object Scan : Entry
}

/** The branch after Google login + consent: an account with a car -> Today; else a remembered code is claimed; else the connect screen. */
fun decideEntry(linked: Boolean, pendingCode: String?): Entry = when {
    linked -> Entry.Today
    pendingCode != null -> Entry.Connect(pendingCode)
    else -> Entry.Scan
}
