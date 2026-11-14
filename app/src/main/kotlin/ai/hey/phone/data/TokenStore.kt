package ai.hey.phone.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Where the phone token lives. One token: this phone is paired with one car. */
interface TokenStore {
    fun load(): String?
    fun save(token: String)
    fun clear()
}

/** Phone token in EncryptedSharedPreferences (AES-256 keyset wrapped by an Android Keystore master key). */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "hey_phone_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load(): String? = prefs.getString(KEY, null)
    override fun save(token: String) = prefs.edit().putString(KEY, token).apply()
    override fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "phone_token"
    }
}

/** In-memory store for unit tests and previews. */
class MemoryTokenStore(private var token: String? = null) : TokenStore {
    override fun load() = token
    override fun save(token: String) { this.token = token }
    override fun clear() { token = null }
}
