package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * W3-delete confirmation as the mockup draws it: a surface card on the scrim, title + one muted line, two equal pills on the
 * raised colour (cancel in the default ink, confirm in the danger ink; [danger] false uses the default ink).
 * Place last inside a full-size Box. [busy] blocks both buttons while the request runs.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = true,
    busy: Boolean = false,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxSize().background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy, onClick = onDismiss)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        VStack(
            HeyGap.G24,
            Modifier.fillMaxWidth().clip(shape).background(c.bgSurface, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(28.dp),
        ) {
            HeyText(title, variant = HeyTextVariant.DialogTitle)
            HeyText(message, variant = HeyTextVariant.DialogBody, tone = HeyTone.Muted)
            HStack(HeyGap.G12, Modifier.fillMaxWidth()) {
                PillAction(dismissText, onDismiss, Modifier.weight(1f), enabled = !busy, ink = c.fgDefault)
                PillAction(confirmText, onConfirm, Modifier.weight(1f), enabled = !busy, ink = if (danger) c.danger else c.fgDefault)
            }
        }
    }
}

@Composable
private fun PillAction(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, ink: Color) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    Box(
        modifier.heightIn(min = 60.dp).clip(shape).background(c.bgRaised, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = HeyTokens.Space.s12, vertical = HeyTokens.Space.s12),
        contentAlignment = Alignment.Center,
    ) { InkText(text, if (enabled) ink else c.fgDisabled, variant = HeyTextVariant.BodyStrong, maxLines = 2, align = TextAlign.Center) }
}
