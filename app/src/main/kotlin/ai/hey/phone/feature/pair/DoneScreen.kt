package ai.hey.phone.feature.pair

import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.PageScaffold
import androidx.compose.runtime.Composable

/** W2 (connected). OWNER: PhonePair (PLEOS-152). Replace this file's body; keep the signature. */
@Composable
fun DoneRoute(onOpenMemory: () -> Unit) {
    PageScaffold { HeyText("W2", variant = HeyTextVariant.Title) }
}
