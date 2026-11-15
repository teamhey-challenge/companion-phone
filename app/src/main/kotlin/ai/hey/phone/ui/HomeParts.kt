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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
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

/** Bottom navigation (studio PhoneTabs): 12/8/20 padding, 54 dp tabs, 56x32 icon pill behind the active tab, 12 sp labels (active 700). */
@Composable
fun BottomNav(tabs: List<NavTab>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    Column(modifier.fillMaxWidth().background(c.bgRaised).windowInsetsPadding(WindowInsets.navigationBars)) {
        HeyDivider()
        HStack(
            HeyGap.G4,
            Modifier.fillMaxWidth().padding(start = HeyTokens.Space.s8, end = HeyTokens.Space.s8, top = HeyTokens.Space.s12, bottom = 20.dp),
        ) {
            tabs.forEach { t ->
                val on = t.key == selected
                Column(
                    Modifier.weight(1f).heightIn(min = 54.dp)
                        .selectable(selected = on, role = Role.Tab) { onSelect(t.key) },
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (on) c.accentContainer else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { HeyIcon(t.icon, null, tone = if (on) HeyTone.Accent else HeyTone.Muted, size = 24.dp) }
                    HeyText(
                        t.label,
                        variant = if (on) HeyTextVariant.TabLabelActive else HeyTextVariant.TabLabel,
                        tone = if (on) HeyTone.Accent else HeyTone.Muted, maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Sub-page app bar (studio friends-phone header): min 80 dp, 24 dp sides, 16 dp top/bottom, 48 dp back button, 16 dp gap,
 * 22/28 title weight 600. [trailing] is an optional 48 dp icon button slot.
 */
@Composable
fun AppBar(title: String, onBack: () -> Unit, backLabel: String, modifier: Modifier = Modifier, sidePadding: androidx.compose.ui.unit.Dp = HeyTokens.Space.s24, trailing: (@Composable () -> Unit)? = null) {
    HStack(HeyGap.G16, modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = sidePadding, vertical = HeyTokens.Space.s16)) {
        HeyIconButton(HeyIcons.ArrowLeft, backLabel, onBack, size = 28.dp)
        HeyText(title, Modifier.weight(1f), variant = HeyTextVariant.AppBarTitle)
        trailing?.invoke()
    }
}

/** Tab-root header (studio .friends-heading): 32/41.6 title with up to two 48 dp icon actions on the right. */
@Composable
fun TabHeader(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    HStack(HeyGap.G12, modifier.fillMaxWidth()) {
        HeyText(title, Modifier.weight(1f), variant = HeyTextVariant.ScreenTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Studio .friends-shortcuts button: raised 20 dp card, leading 28 dp icon, label, trailing 20 dp arrow, 18 dp padding. */
@Composable
fun ShortcutRow(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(20.dp)
    HStack(
        HeyGap.G12,
        modifier.fillMaxWidth().clip(shape).background(c.bgRaised, shape).clickable(role = Role.Button, onClick = onClick).heightIn(min = HeyTokens.Size.touchWeb).padding(18.dp),
    ) {
        HeyIcon(icon, null, size = 28.dp)
        HeyText(text, Modifier.weight(1f), variant = HeyTextVariant.Label, maxLines = 1)
        HeyIcon(HeyIcons.ArrowRight, null, size = 20.dp)
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
