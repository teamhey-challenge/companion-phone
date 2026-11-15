package ai.hey.phone.feature.login

import ai.hey.phone.R
import ai.hey.phone.data.SignOutNotice
import androidx.annotation.StringRes

/** One place for the sign-in error copy (4.6 table). */
@StringRes
fun LoginError.messageRes(): Int = when (this) {
    LoginError.Unconfigured -> R.string.login_err_unconfigured
    LoginError.Verify -> R.string.login_err
    LoginError.Network -> R.string.login_err_network
    LoginError.Rate -> R.string.login_err_rate
}

/** Why the server logged this phone out (6.6), shown once on the start screen. */
@StringRes
fun SignOutNotice.messageRes(): Int = when (this) {
    SignOutNotice.SignedInElsewhere -> R.string.login_notice_elsewhere
    SignOutNotice.SessionExpired -> R.string.login_notice_expired
}
