package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Studio 713edb4 social parts (friends / talk / memory): .friends-primary, .friends-secondary, .friends-avatar, .friends-switch.

/** Studio .friends-primary: primary fill, 56 dp min, 14/24 padding, 24 dp corners, 600 weight. */
@Composable
fun SocialPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.heightIn(min = 56.dp).clip(shape).background(if (enabled) c.primary else c.primary.copy(alpha = 0.45f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { InkText(text, c.bgPage, variant = HeyTextVariant.ButtonLabel, maxLines = 2, align = TextAlign.Center) }
}

/** Studio .friends-secondary: raised fill, 48 dp min, 14/24 padding, 24 dp corners. */
@Composable
fun SocialSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.heightIn(min = HeyTokens.Size.touchWeb).clip(shape).background(c.bgRaised, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { InkText(text, c.fgDefault, variant = HeyTextVariant.ButtonLabelRegular, maxLines = 2, align = TextAlign.Center) }
}

/** Studio .friends-avatar: round [size] on the accent container with a centred 28 dp icon. */
@Composable
fun SocialAvatar(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(HeyTheme.colors.accentContainer), contentAlignment = Alignment.Center) {
        HeyIcon(icon, null, size = 28.dp)
    }
}

/** Studio .friends-arrival: 13 sp muted label followed by a 36x22 switch. */
@Composable
fun ArrivalSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    HStack(
        HeyGap.G12,
        modifier.heightIn(min = 36.dp).toggleable(
            value = checked, role = Role.Switch, interactionSource = remember { MutableInteractionSource() }, indication = null, onValueChange = onCheckedChange,
        ),
    ) {
        HeyText(label, variant = HeyTextVariant.Small, tone = HeyTone.Muted, maxLines = 1)
        Box(
            Modifier.size(width = 36.dp, height = 22.dp).clip(RoundedCornerShape(20.dp)).background(if (checked) c.primary else c.borderDefault).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(16.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(Color.White)) }
    }
}

/** Studio .friends-list article: bottom hairline under a 12 dp padded row. */
@Composable
fun SocialListRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        HeyDivider()
    }
}

/** 48 dp circular raised icon button (studio list "말 전하기" send button, 20 dp icon). */
@Composable
fun RaisedIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(HeyTheme.colors.bgRaised)
            .semantics { this.contentDescription = contentDescription }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { HeyIcon(icon, null, size = 20.dp) }
}

/** Studio .mk-card: white raised 16 dp card, 16/20 padding, title 16/500 + note 14/20 muted. */
@Composable
fun SocialCard(title: String, note: String?, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(HeyTheme.colors.bgRaised, shape).padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HeyText(title, variant = HeyTextVariant.CardTitle)
                if (note != null) HeyText(note, variant = HeyTextVariant.Note, tone = HeyTone.Muted)
            }
            trailing?.invoke()
        }
        content()
    }
}

/** Studio .mk-button: white pill, 14/500, 48 dp min. */
@Composable
fun SocialPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.heightIn(min = HeyTokens.Size.touchWeb).clip(shape).background(c.bgRaised, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { InkText(text, if (danger) c.danger else c.fgDefault, variant = HeyTextVariant.PillLabel, maxLines = 1, align = TextAlign.Center) }
}

/** Studio .mk-toggle: 52x32 track, 24 dp thumb, primary when on. */
@Composable
fun MiniSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    Box(
        modifier.size(width = 52.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(if (checked) c.primary else c.borderStrong)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(24.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(Color.White)) }
}
