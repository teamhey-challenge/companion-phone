package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import ai.hey.phone.R

// Minimal phone design system. Copied from companion-car core:designsystem (HeyText/HeyIcon/HeyButton/GlassButton/
// ToggleRow/CheckRow/MemoryRow/HeyDialog/HeaderBar) and resized for the phone mockups (W1-W3).
// FOLLOW-UP: move the shared parts into companion-shared/android so car and phone consume one copy.
// Feature code MUST use only these components; no raw Text/Button/Color literals.

private fun HeyTextVariant.isKorean(text: String) = text.any { it in '\uAC00'..'\uD7A3' }

/** Korean must break between words, never inside one: a WORD JOINER between adjacent Hangul syllables (same as the car DS). */
internal fun keepKoreanWords(text: String): String {
    if (text.length < 2) return text
    val sb = StringBuilder(text.length * 2)
    for (i in text.indices) {
        sb.append(text[i])
        if (i + 1 < text.length && text[i] in '\uAC00'..'\uD7A3' && text[i + 1] in '\uAC00'..'\uD7A3') sb.append('\u2060')
    }
    return sb.toString()
}

@Composable
fun HeyText(
    text: String,
    modifier: Modifier = Modifier,
    variant: HeyTextVariant = HeyTextVariant.Body,
    tone: HeyTone = HeyTone.Default,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = keepKoreanWords(text),
        modifier = modifier,
        style = variant.style(tone.resolve(HeyTheme.colors)).copy(textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun InkText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    variant: HeyTextVariant = HeyTextVariant.Body,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = keepKoreanWords(text),
        modifier = modifier,
        style = variant.style(color).copy(textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun HeyIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tone: HeyTone = HeyTone.Default,
    size: Dp = HeyTokens.Size.iconWeb,
) {
    InkIcon(icon, contentDescription, tone.resolve(HeyTheme.colors), modifier, size)
}

@Composable
internal fun InkIcon(icon: ImageVector, contentDescription: String?, color: Color, modifier: Modifier = Modifier, size: Dp = HeyTokens.Size.iconWeb) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
fun VStack(
    gap: HeyGap,
    modifier: Modifier = Modifier,
    horizontal: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.value), horizontalAlignment = horizontal, content = content)
}

@Composable
fun HStack(
    gap: HeyGap,
    modifier: Modifier = Modifier,
    vertical: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap.value), verticalAlignment = vertical, content = content)
}

/** Page background: sea (light) / deep sea (dark) flat page colour with the two soft scene washes of the phone mockups. */
@Composable
fun OceanBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = HeyTheme.colors
    Box(
        modifier
            .background(c.bgPage)
            .background(Brush.radialGradient(listOf(c.sceneSand, Color.Transparent), center = Offset.Zero, radius = 900f))
            .background(Brush.radialGradient(listOf(c.sceneLilac, Color.Transparent), center = Offset(1200f, 500f), radius = 1000f)),
    ) { content() }
}

/** Scrolling page body: safe-area insets, 24 dp side padding (HeyTokens.Size.webPageMax). */
@Composable
fun PageScaffold(
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OceanBackground(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = HeyTokens.Size.webPageMax),
                content = content,
            )
            bottomBar?.invoke()
        }
    }
}

/** Header: optional 66 dp circular back button, then the app name wordmark (헤이파리, D-20). */
@Composable
fun HeaderBar(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, backLabel: String = "") {
    HStack(HeyGap.G16, modifier.fillMaxWidth().padding(top = HeyTokens.Space.s32, bottom = HeyTokens.Space.s16).heightIn(min = 66.dp)) {
        if (onBack != null) HeyBackButton(backLabel, onBack)
        HeyText(stringResource(R.string.app_name), variant = HeyTextVariant.Wordmark, maxLines = 1)
    }
}

/** The character, centred on its own line, as the lead of a page or an empty/error state. Same still as W1/W2. */
@Composable
fun CharacterHero(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CharacterStill(stringResource(R.string.pair_brand_cd), size = size)
    }
}

@Composable
fun HeyBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    Box(
        modifier
            .size(66.dp)
            .clip(RoundedCornerShape(HeyTokens.Radius.full))
            .background(c.bgRaised)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { InkIcon(HeyIcons.ArrowLeft, null, c.fgDefault, size = 33.dp) }
}

@Composable
fun HeyIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, size: Dp = 44.dp) {
    val c = HeyTheme.colors
    Box(
        modifier
            .heightIn(min = HeyTokens.Size.touchWeb)
            .size(maxOf(size, HeyTokens.Size.touchWeb))
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { InkIcon(icon, null, if (enabled) c.fgDefault else c.fgDisabled, size = size) }
}

enum class HeyButtonVariant { Primary, Text, Danger }

/** Plain button: Primary = filled, Text = no surface (mockup "동의하지 않기", "개인정보 처리 안내"), Danger = destructive text. */
@Composable
fun HeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HeyButtonVariant = HeyButtonVariant.Primary,
    enabled: Boolean = true,
    textVariant: HeyTextVariant = HeyTextVariant.BodyStrong,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    val container = if (variant == HeyButtonVariant.Primary) (if (enabled) c.primary else c.bgSunken) else Color.Transparent
    val ink = when {
        !enabled -> c.fgDisabled
        variant == HeyButtonVariant.Primary -> c.fgOnPrimary
        variant == HeyButtonVariant.Danger -> c.danger
        else -> c.fgMuted
    }
    Box(
        modifier
            .heightIn(min = HeyTokens.Size.touchWeb)
            .clip(shape)
            .background(container, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s8),
        contentAlignment = Alignment.Center,
    ) { InkText(text, ink, variant = textVariant, maxLines = 2, align = TextAlign.Center) }
}

/**
 * Glass pill for a screen's one main action (W1 "동의하고 시작하기", W2 "우리의 기억 보기"). [dimmed] draws the blocked look
 * of the mockup but still takes the press (so a screen can explain why it is blocked); [enabled]=false ignores it.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    loading: Boolean = false,
    horizontalPadding: Dp = HeyTokens.Space.s32,
    textVariant: HeyTextVariant = HeyTextVariant.BodyStrong,
) {
    val c = HeyTheme.colors
    val dark = HeyTheme.dark
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    val blocked = !enabled || dimmed
    val fill = when {
        blocked -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        dark -> Brush.verticalGradient(listOf(Color(0xFF72BFD7).copy(alpha = 0.14f), Color(0xFF33A0B1).copy(alpha = 0.22f)))
        else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), c.accentContainer))
    }
    val rim = if (blocked) Color.Transparent else c.borderDefault
    Box(
        modifier
            .heightIn(min = HeyTokens.Size.touchWeb)
            .clip(shape)
            .background(fill, shape)
            .border(HeyTokens.Border.hairline, rim, shape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = HeyTokens.Space.s12),
        contentAlignment = Alignment.Center,
    ) {
        InkText(
            if (loading) "…" else text,
            // Blocked-but-pressable (explains itself on press) must stay readable (>= 4.5): muted, not the disabled grey.
            if (!enabled) c.fgDisabled else if (blocked) c.fgMuted else c.fgOnAccent.takeIf { !dark } ?: c.fgDefault,
            variant = textVariant,
            maxLines = 2,
            align = TextAlign.Center,
        )
    }
}

private val RowShape = RoundedCornerShape(HeyTokens.Radius.md)

/** Switch row with title + optional description (W1 "이야기 기억하기 · 선택", W3 말투). */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val c = HeyTheme.colors
    HStack(
        HeyGap.G16,
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, interactionSource = remember { MutableInteractionSource() }, indication = null, onValueChange = onCheckedChange)
            .heightIn(min = HeyTokens.Size.touchWeb)
            .padding(vertical = HeyTokens.Space.s12),
    ) {
        VStack(HeyGap.G8, Modifier.weight(1f)) {
            HeyText(label, variant = HeyTextVariant.PairRowTitle, tone = if (enabled) HeyTone.Default else HeyTone.Disabled)
            if (description != null) HeyText(description, variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
        }
        val track = when {
            !enabled -> c.bgSunken
            checked -> c.primary
            else -> c.borderStrong
        }
        val thumb = if (checked && enabled) c.fgOnPrimary else c.bgRaised
        Box(
            Modifier.size(width = 48.dp, height = 28.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(track),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(HeyTokens.Space.s4).size(20.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(thumb))
        }
    }
}

/** Checkbox row (W1 "목소리 녹음과 대화 처리에 동의해요 · 필수"). [error] is shown under the row in the danger colour. */
@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    val c = HeyTheme.colors
    VStack(HeyGap.G8, modifier.fillMaxWidth()) {
        HStack(
            HeyGap.G16,
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, interactionSource = remember { MutableInteractionSource() }, indication = null, onValueChange = onCheckedChange)
                .heightIn(min = HeyTokens.Size.touchWeb)
                .padding(vertical = HeyTokens.Space.s8),
        ) {
            val boxShape = RoundedCornerShape(HeyTokens.Space.s4) // W1 mockup: 4 dp square checkbox
            Box(
                Modifier
                    .padding(start = HeyTokens.Space.s4)
                    .size(22.dp)
                    .clip(boxShape)
                    .background(if (checked) c.primary else Color.Transparent, boxShape)
                    .border(HeyTokens.Border.hairline, if (checked) c.primary else c.fgSubtle, boxShape),
                contentAlignment = Alignment.Center,
            ) { if (checked) InkIcon(HeyIcons.Check, null, c.fgOnPrimary) }
            HeyText(label, Modifier.weight(1f), variant = HeyTextVariant.PairBody, tone = if (enabled) HeyTone.Default else HeyTone.Disabled)
        }
        if (error != null) HeyText(error, variant = HeyTextVariant.Caption, tone = HeyTone.Danger)
    }
}

@Composable
fun HeyDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().size(HeyTokens.Border.hairline).background(HeyTheme.colors.borderDefault))
}

/** Memory card (W3): kind label, text, date, trash button. Tone alternates by [index] like the mockup (3-colour cycle). */
@Composable
fun MemoryRow(
    kind: String,
    text: String,
    date: String,
    deleteLabel: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    enabled: Boolean = true,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val bg = when (index % 3) {
        0 -> c.secondaryContainer
        1 -> c.infoContainer
        else -> c.sceneSand
    }
    HStack(HeyGap.G16, modifier.fillMaxWidth().clip(shape).background(bg, shape).padding(start = HeyTokens.Space.s16, end = HeyTokens.Space.s16, top = HeyTokens.Space.s24, bottom = HeyTokens.Space.s24)) {
        VStack(HeyGap.G8, Modifier.weight(1f)) {
            HeyText(kind, variant = HeyTextVariant.Caption)
            HeyText(text, variant = HeyTextVariant.Body)
            HeyText(date, variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
        }
        HeyIconButton(HeyIcons.Trash, deleteLabel, onDelete, enabled = enabled, size = 48.dp)
    }
}

/** Placeholder block while a list loads. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(20.dp)).background(HeyTheme.colors.bgSunken))
}

data class HeyDialogAction(val text: String, val onClick: () -> Unit)

/**
 * Modal confirmation drawn in the composition (scrim + centred card). Place it last inside a full-size Box.
 * Phone mockup: padding 28, radius 28, buttons 60 high (ui-spec "폰 확인 창 60px").
 */
@Composable
fun HeyDialog(
    title: String,
    message: String,
    confirm: HeyDialogAction,
    dismiss: HeyDialogAction,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxSize().background(c.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss.onClick).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        VStack(
            HeyGap.G16,
            Modifier.fillMaxWidth().clip(shape).background(c.bgSurface, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(28.dp),
        ) {
            HeyText(title, variant = HeyTextVariant.Heading)
            HeyText(message, variant = HeyTextVariant.Body, tone = HeyTone.Muted)
            HStack(HeyGap.G12, Modifier.fillMaxWidth().padding(top = HeyTokens.Space.s12)) {
                DialogButton(dismiss.text, dismiss.onClick, Modifier.weight(1f), primary = false, danger = false)
                DialogButton(confirm.text, confirm.onClick, Modifier.weight(1f), primary = true, danger = danger)
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier, primary: Boolean, danger: Boolean) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    val bg = when {
        !primary -> c.buttonSecondary
        danger -> c.danger
        else -> c.primary
    }
    val ink = when {
        !primary -> c.fgOnSecondary
        danger -> c.fgOnDanger
        else -> c.fgOnPrimary
    }
    Box(modifier.heightIn(min = 60.dp).clip(shape).background(bg, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = HeyTokens.Space.s12, vertical = HeyTokens.Space.s12), contentAlignment = Alignment.Center) {
        InkText(text, ink, variant = HeyTextVariant.BodyStrong, maxLines = 2, align = TextAlign.Center)
    }
}

/** Short notice line (errors, saved). */
@Composable
fun HeyNotice(text: String, modifier: Modifier = Modifier, danger: Boolean = true) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    Box(modifier.fillMaxWidth().clip(shape).background(if (danger) c.dangerContainer else c.infoContainer, shape).padding(HeyTokens.Space.s16)) {
        HeyText(text, variant = HeyTextVariant.Label, tone = if (danger) HeyTone.Danger else HeyTone.Default)
    }
}

/** The 헤이파리 still, theme-aware: aqua in the sea theme, peach in the deep-sea theme. */
@Composable
fun CharacterStill(description: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    Image(
        painter = painterResource(if (HeyTheme.dark) R.drawable.hey_character_still_peach else R.drawable.hey_character_still),
        contentDescription = description,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
