package ai.hey.phone.feature.login

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

enum class ConsentError { Required, Failed }

/** The consents the talk needs after a first Google sign-in: "record" + "external_ai", plus "memory" when ticked. Nothing is saved until all posts succeeded. */
class ConsentModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onDone: () -> Unit,
) {
    var consent by mutableStateOf(false)
    var memory by mutableStateOf(false)
    var saving by mutableStateOf(false)
        private set
    var error: ConsentError? by mutableStateOf(null)
        private set

    fun start() {
        if (saving) return
        if (!consent) {
            error = ConsentError.Required
            return
        }
        error = null
        saving = true
        scope.launch {
            try {
                api.postConsent("record", true)
                api.postConsent("external_ai", true)
                if (memory) api.postConsent("memory", true)
                onDone()
            } catch (_: ApiException) {
                error = ConsentError.Failed
            } catch (_: IOException) {
                error = ConsentError.Failed
            }
            saving = false
        }
    }
}
