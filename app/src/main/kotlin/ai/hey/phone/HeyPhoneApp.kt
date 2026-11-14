package ai.hey.phone

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.EncryptedTokenStore
import ai.hey.phone.data.TokenStore
import android.app.Application

/** Process-wide singletons: the token store and the API client. Screens get them through [HeyPhoneApp.from]. */
class HeyPhoneApp : Application() {
    lateinit var tokens: TokenStore
        private set
    lateinit var api: ApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        tokens = EncryptedTokenStore(this)
        api = ApiClient(BuildConfig.SERVER_URL, tokens)
    }

    companion object {
        fun from(context: android.content.Context): HeyPhoneApp = context.applicationContext as HeyPhoneApp
    }
}
