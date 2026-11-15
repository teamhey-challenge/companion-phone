package ai.hey.phone.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** What the Google account picker answered. The ID token goes straight to the server and is never stored or logged. */
sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult
    /** The user closed the picker: not an error. */
    data object Cancelled : GoogleSignInResult
    /** No Google account on this phone. */
    data object NoAccount : GoogleSignInResult
    data object Failed : GoogleSignInResult
}

/** Where the Google ID token comes from (Credential Manager in the app, a fake in tests). */
interface GoogleIdTokenSource {
    /** True when a server client id was built in (`-PgoogleServerClientId=`). Without it the Google button says it is unavailable. */
    val configured: Boolean

    /** Shows the account picker from [context] (an Activity) and returns the ID token for this app's server client id. */
    suspend fun fetch(context: Context): GoogleSignInResult

    /** Signs out of Credential Manager state so the next sign-in shows the picker again. Never throws. */
    suspend fun clear()
}

/**
 * Sign in with Google through Credential Manager. [serverClientId] is the *web* OAuth client id the server checks as `aud`;
 * it is not a secret (it is public in any Google sign-in) and comes from build config, never from a file in the repo.
 */
class CredentialManagerGoogleSource(private val appContext: Context, private val serverClientId: String) : GoogleIdTokenSource {
    override val configured get() = serverClientId.isNotBlank()

    override suspend fun fetch(context: Context): GoogleSignInResult {
        if (!configured) return GoogleSignInResult.Failed
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build()).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleSignInResult.Failed
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.NoAccount
        } catch (_: GetCredentialException) {
            GoogleSignInResult.Failed
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed
        }
    }

    override suspend fun clear() {
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
