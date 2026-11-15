package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One tab of the bottom bar. [key] is stable across languages (route/test id). */
data class NavTab(val key: String, val label: String, val icon: ImageVector)

/** Bottom navigation: icon over label, selected tab in the primary ink; every tab is at least the web touch size high. */
@Composable
fun BottomNav(tabs: List<NavTab>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    Column(modifier.fillMaxWidth().background(c.bgSurface).windowInsetsPadding(WindowInsets.navigationBars)) {
        HeyDivider()
        HStack(HeyGap.G4, Modifier.fillMaxWidth().padding(horizontal = HeyTokens.Space.s8, vertical = HeyTokens.Space.s4)) {
            tabs.forEach { t ->
                val on = t.key == selected
                VStack(
                    HeyGap.G4,
                    Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(HeyTokens.Radius.md))
                        .selectable(selected = on, role = Role.Tab) { onSelect(t.key) }
                        .padding(vertical = HeyTokens.Space.s4),
                    horizontal = Alignment.CenterHorizontally,
                ) {
                    HeyIcon(t.icon, null, tone = if (on) HeyTone.Accent else HeyTone.Muted, size = 24.dp)
                    HeyText(t.label, variant = HeyTextVariant.PairCaption, tone = if (on) HeyTone.Accent else HeyTone.Muted, maxLines = 1)
                }
            }
        }
    }
}

/** Soft rounded card (the same 20 dp shape as the memory rows). [tone] cycles the three scene tints. */
@Composable
fun HeyCard(modifier: Modifier = Modifier, tone: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val bg = when (tone % 3) {
        0 -> c.secondaryContainer
        1 -> c.infoContainer
        else -> c.sceneSand
    }
    VStack(HeyGap.G8, modifier.fillMaxWidth().clip(shape).background(bg, shape).padding(HeyTokens.Space.s16), content = content)
}

/** Chat bubble: mine on the right in the primary colour, hers on the left on the raised colour. */
@Composable
fun ChatBubble(text: String, mine: Boolean, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.widthIn(max = 300.dp).clip(shape).background(if (mine) c.primary else c.bgRaised, shape)
                .padding(horizontal = HeyTokens.Space.s16, vertical = HeyTokens.Space.s12),
        ) {
            HeyText(text, tone = if (mine) HeyTone.OnPrimary else HeyTone.Default)
        }
    }
}
