package ai.hey.phone

import ai.hey.phone.nav.PhoneNavHost
import ai.hey.phone.nav.pairCodeFrom
import ai.hey.phone.ui.HeyTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    /** Pair code from the launching link; consumed by the NavHost once and replaced by a newer link on onNewIntent. */
    private val link = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) link.value = pairCodeFrom(intent?.data)
        val app = HeyPhoneApp.from(this)
        setContent {
            LaunchedEffect(Unit) { if (app.tokens.load() != null) app.refreshLook() }
            HeyTheme {
                PhoneNavHost(
                    hasToken = { app.tokens.load() != null },
                    deepLinkCode = link.value,
                    onDeepLinkHandled = { link.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        link.value = pairCodeFrom(intent.data)
    }
}
