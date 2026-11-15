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

/**
 * Why a Google sign-in did not finish (4.6 failure table). A closed picker is not an error ([LoginModel.error] stays null) and
 * a phone without a Google account opens the system add-account screen instead ([LoginModel] `onNoAccount`).
 */
enum class LoginError { Unconfigured, Verify, Network, Rate }

/** Where a finished sign-in goes: [needsConsent] = the account has not answered the record consent yet; [linkedCar] = a car is already bound. */
data class LoginOutcome(val needsConsent: Boolean, val linkedCar: Boolean)

/**
 * Google sign-in state machine: picker ([fetch]) -> `POST /v1/auth/google` (the client stores the phone token) -> consent check.
 * [configured] false (no server client id built in) or a 503 `google_login_unconfigured` -> [LoginError.Unconfigured].
 * Both start buttons run this first (D-57); what follows login is decided by [decideEntry].
 */
class LoginModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val configured: Boolean,
    private val fetch: suspend () -> GoogleSignInResult,
    private val onNoAccount: () -> Unit,
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
                GoogleSignInResult.NoAccount -> onNoAccount()
                GoogleSignInResult.Failed -> error = LoginError.Verify
                is GoogleSignInResult.Token -> exchange(picked.idToken)
            }
            busy = false
        }
    }

    private suspend fun exchange(idToken: String) {
        val r: GoogleLoginResult = try {
            api.googleLogin(idToken)
        } catch (e: ApiException) {
            error = loginError(e)
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

/** Server answer of `POST /v1/auth/google` -> the notice (`google_verify_failed` covers a bad token and a Google outage). */
fun loginError(e: ApiException): LoginError = when {
    e.reason == "google_login_unconfigured" -> LoginError.Unconfigured
    e.reason == "rate_limited" || e.code == 429 -> LoginError.Rate
    else -> LoginError.Verify
}
