package ai.hey.phone.feature.login

import ai.hey.phone.R
import androidx.annotation.StringRes

/** One place for the sign-in error copy. */
@StringRes
fun LoginError.messageRes(): Int = when (this) {
    LoginError.Unconfigured -> R.string.login_err_unconfigured
    LoginError.NoAccount -> R.string.login_err_no_account
    LoginError.Network -> R.string.login_err_network
    LoginError.Rate -> R.string.login_err_rate
    LoginError.Generic -> R.string.login_err
}
