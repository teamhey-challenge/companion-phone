package ai.hey.phone

import ai.hey.phone.nav.PhoneNavHost
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ai.hey.phone.nav.deepLinkRoute
import ai.hey.phone.ui.HeyTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    /** Route the launching link opens (join/{code} or friend/{code}); consumed by the NavHost once and replaced by a newer link on onNewIntent. */
    private val link = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) link.value = deepLinkRoute(intent?.dataString)
        val app = HeyPhoneApp.from(this)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { app.foreground = true }
            override fun onStop(owner: LifecycleOwner) { app.foreground = false }
        })
        setContent {
            LaunchedEffect(Unit) { if (app.tokens.load() != null) app.refreshLook() }
            HeyTheme {
                PhoneNavHost(
                    hasToken = { app.tokens.load() != null },
                    deepLink = link.value,
                    onDeepLinkHandled = { link.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        link.value = deepLinkRoute(intent.dataString)
    }
}
