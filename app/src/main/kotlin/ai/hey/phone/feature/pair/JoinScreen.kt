package ai.hey.phone.feature.pair

import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.PageScaffold
import androidx.compose.runtime.Composable

/** W1 (QR join + consent). OWNER: PhonePair (PLEOS-152). Replace this file's body; keep the signature. */
@Composable
fun JoinRoute(code: String, onClaimed: () -> Unit) {
    PageScaffold { HeyText("W1 $code", variant = HeyTextVariant.Title) }
}
