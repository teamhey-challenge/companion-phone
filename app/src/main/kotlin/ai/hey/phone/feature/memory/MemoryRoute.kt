package ai.hey.phone.feature.memory

import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.PageScaffold
import androidx.compose.runtime.Composable

/**
 * W3 (memory view/delete + phone settings 말투 + unpair). OWNER: PhoneMemory (PLEOS-153). Replace this file's body; keep the
 * signature. [onUnpaired] is called after the local token was cleared (ApiClient.unpair()) and navigates to join/none.
 */
@Composable
fun MemoryRoute(onBack: () -> Unit, onUnpaired: () -> Unit) {
    PageScaffold { HeyText("W3", variant = HeyTextVariant.Title) }
}
