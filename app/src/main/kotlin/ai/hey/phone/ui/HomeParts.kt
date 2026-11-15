package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

/** Page header of the signed-in tabs (N5): the Hey wordmark on the left, a short muted [trailing] line (the date) on the right. */
@Composable
fun ScreenHeader(wordmark: String, trailing: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        HeyText(wordmark, variant = HeyTextVariant.Wordmark, maxLines = 1)
        HeyText(trailing, variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted, maxLines = 1)
    }
}

/** The day's picture on the diary card (N5): fixed 342:182 frame, 22 dp corners, cropped around 55 % of the height. */
@Composable
fun DiaryPhoto(description: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.diary_polaroid),
        contentDescription = description,
        modifier = modifier.fillMaxWidth().aspectRatio(342f / 182f).clip(RoundedCornerShape(22.dp)),
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, 0.1f),
    )
}

/** Row-sized link with a trailing chevron (N5 "함께 기억한 이야기 ›"); at least the web touch size high. */
@Composable
fun LinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    HStack(
        HeyGap.G8,
        modifier.fillMaxWidth().heightIn(min = HeyTokens.Size.touchWeb).clickable(role = Role.Button, onClick = onClick),
    ) {
        HeyText(text, Modifier.weight(1f), variant = HeyTextVariant.Label)
        HeyIcon(HeyIcons.ChevronRight, null, tone = HeyTone.Muted, size = 20.dp)
    }
}
