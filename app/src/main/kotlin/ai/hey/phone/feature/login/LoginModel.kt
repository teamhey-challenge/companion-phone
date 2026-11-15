package ai.hey.phone.feature.login

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.GoogleLoginResult
import ai.hey.phone.data.GoogleSignInResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/** Why a Google sign-in did not finish. A closed picker is not an error and leaves [LoginModel.error] null. */
enum class LoginError { Unconfigured, NoAccount, Network, Rate, Generic }

/** Where a finished sign-in goes: [needsConsent] = the account has not answered the record consent yet; [linkedCar] = a car is already bound. */
data class LoginOutcome(val needsConsent: Boolean, val linkedCar: Boolean)

/**
 * Google sign-in state machine: picker ([fetch]) -> `POST /v1/auth/google` (the client stores the phone token) -> consent check.
 * [configured] false (no server client id built in) or a 503 from the server -> [LoginError.Unconfigured]; the QR path stays open.
 */
class LoginModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val configured: Boolean,
    private val fetch: suspend () -> GoogleSignInResult,
    private val onDone: (LoginOutcome) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set
    var error: LoginError? by mutableStateOf(null)
        private set

    fun signIn() {
        if (busy) return
        error = null
        if (!configured) {
            error = LoginError.Unconfigured
            return
        }
        busy = true
        scope.launch {
            when (val picked = fetch()) {
                GoogleSignInResult.Cancelled -> Unit
                GoogleSignInResult.NoAccount -> error = LoginError.NoAccount
                GoogleSignInResult.Failed -> error = LoginError.Generic
                is GoogleSignInResult.Token -> exchange(picked.idToken)
            }
            busy = false
        }
    }

    private suspend fun exchange(idToken: String) {
        val r: GoogleLoginResult = try {
            api.googleLogin(idToken)
        } catch (e: ApiException) {
            error = when {
                e.code == 503 || e.reason == "google_login_unconfigured" -> LoginError.Unconfigured
                e.code == 429 -> LoginError.Rate
                else -> LoginError.Generic
            }
            return
        } catch (_: IOException) {
            error = LoginError.Network
            return
        }
        // An unreadable consent list counts as "not answered": the consent screen asks, and posting it again is harmless.
        val answered = try { api.consents().answer("record") == true } catch (_: IOException) { false }
        onDone(LoginOutcome(needsConsent = !answered, linkedCar = r.linkedCar))
    }
}
