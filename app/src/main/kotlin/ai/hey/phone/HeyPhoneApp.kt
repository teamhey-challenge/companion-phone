package ai.hey.phone

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.EncryptedTokenStore
import ai.hey.phone.data.LocalPrefs
import ai.hey.phone.data.PrefsSeenStore
import ai.hey.phone.data.PushFrame
import ai.hey.phone.data.PushGate
import ai.hey.phone.data.TokenStore
import ai.hey.phone.notify.InboxStream
import ai.hey.phone.notify.Notifier
import ai.hey.phone.notify.Schedule
import ai.hey.phone.ui.CharacterLook
import ai.hey.phone.ui.LookStore
import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDate

/** Process-wide singletons: the token store, the API client and the notification plumbing. Screens get them through [HeyPhoneApp.from]. */
class HeyPhoneApp : Application() {
    lateinit var tokens: TokenStore
        private set
    lateinit var api: ApiClient
        private set
    lateinit var prefs: LocalPrefs
        private set
    lateinit var seen: PrefsSeenStore
        private set
    lateinit var gate: PushGate
        private set

    /** Every push frame (stream or poll) for screens that refresh themselves while open. */
    val events = MutableSharedFlow<PushFrame>(extraBufferCapacity = 16)

    /** True while MainActivity is started: a visible app does not also notify. */
    @Volatile var foreground = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var stream: InboxStream

    override fun onCreate() {
        super.onCreate()
        tokens = EncryptedTokenStore(this)
        api = ApiClient(BuildConfig.SERVER_URL, tokens)
        prefs = LocalPrefs(this)
        seen = PrefsSeenStore(this)
        gate = PushGate(seen) { foreground }
        stream = InboxStream(api, scope, onFrame = { Notifier.handle(this, it) }, onUnauthorized = { signedOut() })
        Notifier.ensureChannels(this)
        if (tokens.load() != null) startBackground()
    }

    /** Stream + 15-minute poll, once there is a token. Safe to call again. */
    fun startBackground() {
        stream.start()
        Schedule.poll(this)
        Schedule.dropLegacy(this)
    }

    /** The token is gone (unpaired, logged out, or the server said 401/403): stop everything that talks to the server. */
    fun signedOut() {
        stream.stop()
        Schedule.cancelAll(this)
        tokens.clear()
        prefs.clear()
        seen.clear()
    }

    /** Reads the look the driver picked in the car (D-41) so every character on the phone wears it. A failed read keeps the last look. */
    suspend fun refreshLook() {
        runCatching { api.settings() }.onSuccess { LookStore.look = CharacterLook(it.bodyColor, it.hat, it.neck) }
    }

    companion object {
        fun from(context: android.content.Context): HeyPhoneApp = context.applicationContext as HeyPhoneApp
    }
}
