package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign

data class ChoiceOption<T>(val value: T, val label: String)

/** Two-or-three pill choice (W3 말투: 반말 / 존댓말). One pill is always selected; [enabled]=false blocks input while saving. */
@Composable
fun <T> SegmentedChoice(
    options: List<ChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    HStack(HeyGap.G8, modifier.fillMaxWidth()) {
        options.forEach { o ->
            val on = o.value == selected
            Box(
                Modifier.weight(1f).heightIn(min = HeyTokens.Size.touchWeb).clip(shape)
                    .background(if (on) c.primary else c.buttonSecondary, shape)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onSelect(o.value) }
                    .padding(horizontal = HeyTokens.Space.s12, vertical = HeyTokens.Space.s12),
                contentAlignment = Alignment.Center,
            ) {
                InkText(
                    o.label,
                    when {
                        !enabled && !on -> c.fgDisabled
                        on -> c.fgOnPrimary
                        else -> c.fgOnSecondary
                    },
                    variant = HeyTextVariant.BodyStrong,
                    align = TextAlign.Center,
                )
            }
        }
    }
}
