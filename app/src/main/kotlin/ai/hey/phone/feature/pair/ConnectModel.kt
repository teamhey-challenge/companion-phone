package ai.hey.phone.feature.pair

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/** Why a car link failed (4.6 failure table), mapped from the server's `error` of `POST /v1/pairings/{code}/claim`. */
enum class JoinError { Expired, Unknown, AlreadyUsed, CarLinkedToOther, GoogleLinkedToOtherCar, RecordConsent, Verify, Rate, Network }

/** What the next press on an error does: scan/type again, retry the same code, or go to the consent screen / leave. */
enum class JoinAction { Rescan, Retry, Close }

fun JoinError.action(): JoinAction = when (this) {
    JoinError.Expired, JoinError.Unknown, JoinError.AlreadyUsed -> JoinAction.Rescan
    JoinError.Network -> JoinAction.Retry
    else -> JoinAction.Close
}

fun joinError(e: ApiException): JoinError = when (e.reason) {
    "expired" -> JoinError.Expired
    "unknown" -> JoinError.Unknown
    "already_used" -> JoinError.AlreadyUsed
    "car_linked_to_other_account" -> JoinError.CarLinkedToOther
    "google_linked_to_other_car" -> JoinError.GoogleLinkedToOtherCar
    "record_consent_required" -> JoinError.RecordConsent
    "rate_limited" -> JoinError.Rate
    else -> if (e.code == 429) JoinError.Rate else JoinError.Verify
}

/** What the car-link screen shows. */
sealed interface JoinUi {
    /** Sending the claim. */
    data object Loading : JoinUi

    data class Failed(val error: JoinError) : JoinUi
}

/**
 * Car link after Google login (D-58: the only way in): [start] claims the scanned / typed / remembered code with the Google token.
 * The memory consent sent with the claim is the answer given on the consent screen.
 */
class ConnectModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onClaimed: () -> Unit,
) {
    var ui: JoinUi by mutableStateOf(JoinUi.Loading)
        private set

    private var code: String? = null

    fun start(code: String) {
        this.code = code
        ui = JoinUi.Loading
        scope.launch {
            try {
                val memory = try { api.consents().answer("memory") == true } catch (_: IOException) { false }
                api.claim(code, record = true, memory = memory)
                onClaimed()
            } catch (e: ApiException) {
                ui = JoinUi.Failed(joinError(e))
            } catch (_: IOException) {
                ui = JoinUi.Failed(JoinError.Network)
            }
        }
    }

    fun retry() { code?.let(::start) }
}
