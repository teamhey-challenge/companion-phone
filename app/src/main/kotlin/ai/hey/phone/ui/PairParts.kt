package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.R
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Parts of the start / consent / car-link / done screens (feature/login, feature/pair). Measured from the studio mockups at
// hey-companion-ui 4dc8ad3 (phone-start/consent/join/done, 390x844): a 24 dp system bar on top, an 80 dp app bar, 24 dp page margins.

/** The studio phone frame has a 24 dp status bar and a 24 dp gesture bar; a real device brings its own (the larger one wins). */
private val SystemBar = 24.dp

/**
 * One page of the entry flow: app bar (back button + [title], or the Hey wordmark), scrolling body, optional fixed [bottom].
 * The body owns its horizontal padding.
 */
@Composable
fun EntryFrame(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = HeyTheme.colors
    val bars = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        modifier.fillMaxSize().background(c.bgPage)
            .padding(top = maxOf(bars.calculateTopPadding(), SystemBar), bottom = maxOf(bars.calculateBottomPadding(), SystemBar)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = HeyTokens.Space.s24),
            horizontalArrangement = Arrangement.spacedBy(HeyTokens.Space.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    Modifier.size(HeyTokens.Size.touchWeb).clip(CircleShape)
                        .semantics { contentDescription = backLabel }
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { InkIcon(HeyIcons.ArrowLeft, null, c.fgDefault) }
            }
            InkText(title ?: stringResource(R.string.app_name), c.fgDefault, variant = HeyTextVariant.EntryBar, maxLines = 1)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), content = content)
        bottom?.invoke(this)
    }
}

/** Pill button of the entry flow: 48 dp (56 dp [large]), 24 dp corners; [primary] is the filled one, [dimmed] the not-yet-allowed look (still pressable). */
@Composable
fun EntryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    large: Boolean = false,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    loading: Boolean = false,
    trailing: ImageVector? = null,
    leading: ImageVector? = null,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Sheet.radius)
    val blocked = dimmed || !enabled
    val bg = when {
        !primary -> c.bgRaised
        blocked -> c.bgSunken
        else -> c.primary
    }
    val ink = when {
        !enabled -> c.fgDisabled
        blocked -> c.fgMuted
        primary -> c.bgPage
        else -> c.fgDefault
    }
    val gap = if (large) HeyTokens.Space.s12 else HeyTokens.Space.s8
    Box(
        modifier.heightIn(min = if (large) 56.dp else HeyTokens.Size.touchWeb).clip(shape).background(bg, shape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = HeyTokens.Space.s12),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) InkIcon(leading, null, ink, size = 20.dp)
            InkText(
                if (loading) "…" else text, ink,
                variant = if (large) HeyTextVariant.EntryButtonLarge else HeyTextVariant.EntryButton, maxLines = 2, align = TextAlign.Center,
            )
            if (trailing != null) InkIcon(trailing, null, ink, size = 20.dp)
        }
    }
}

/** White rounded card (16 dp padding and corners). */
@Composable
fun EntryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(HeyTokens.Space.s16)).background(HeyTheme.colors.bgRaised).padding(HeyTokens.Space.s16), content = content)
}

/** A card row: bold [title], one muted [body] line and a 24 dp [icon] on the right. */
@Composable
fun EntryInfoRow(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HeyTokens.Space.s16), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HeyTokens.Space.s4)) {
            InkText(title, c.fgDefault, variant = HeyTextVariant.EntryBodyMedium)
            InkText(body, c.fgMuted, variant = HeyTextVariant.EntryDesc)
        }
        InkIcon(icon, null, c.fgDefault)
    }
}

/** Checkbox row: 24 dp square box, 14 sp label, at least 48 dp high. */
@Composable
fun EntryCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Space.s4)
    Row(
        modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, interactionSource = null, indication = null, onValueChange = onCheckedChange)
            .heightIn(min = HeyTokens.Size.touchWeb),
        horizontalArrangement = Arrangement.spacedBy(HeyTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(shape).background(if (checked) c.primary else Color.Transparent, shape)
                .border(HeyTokens.Border.hairline, if (checked) c.primary else c.fgSubtle, shape),
            contentAlignment = Alignment.Center,
        ) { if (checked) InkIcon(HeyIcons.Check, null, c.fgOnPrimary, size = 18.dp) }
        InkText(label, if (enabled) c.fgDefault else c.fgDisabled, Modifier.weight(1f), variant = HeyTextVariant.EntryDesc)
    }
}

/** Text link of the entry flow (privacy policy): 14 sp in the primary colour, 48 dp touch height. */
@Composable
fun EntryLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = HeyTokens.Size.touchWeb).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        InkText(text, HeyTheme.colors.primary, variant = HeyTextVariant.EntryDesc)
    }
}

/** The 8-digit car code, shown as `XXXX XXXX`; [value] is the bare code. Typed text is drawn by [InkText] so it is measured like every other text. */
@Composable
fun EntryCodeField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, wrong: Boolean = false, onDone: () -> Unit = {}) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Space.s4)
    val grouped = if (value.length > 4) value.substring(0, 4) + " " + value.substring(4) else value
    Box(
        modifier.fillMaxWidth().clip(shape).background(c.bgRaised, shape)
            .border(HeyTokens.Border.hairline, if (wrong) c.danger else c.borderDefault, shape)
            .padding(HeyTokens.Space.s16),
    ) {
        if (value.isEmpty()) InkText(placeholder, c.fgSubtle, variant = HeyTextVariant.EntryCode, maxLines = 1)
        else InkText(grouped, c.fgDefault, variant = HeyTextVariant.EntryCode, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isLetterOrDigit).uppercase().take(16)) },
            singleLine = true,
            textStyle = HeyTextVariant.EntryCode.style(Color.Transparent),
            cursorBrush = SolidColor(c.primary),
            visualTransformation = CodeGrouping,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onAny = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private object CodeGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val out = if (s.length > 4) s.substring(0, 4) + " " + s.substring(4) else s
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset > 4) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (offset > 4) offset - 1 else offset
        }
        return TransformedText(AnnotatedString(out), map)
    }
}

/** Full-width page message led by the character, used for "차를 연결하지 못했어요" and the entry check. */
@Composable
fun PageMessage(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    VStack(HeyGap.G16, modifier.fillMaxWidth().padding(top = HeyTokens.Space.s32)) {
        CharacterHero(size = 150.dp)
        HeyText(title, Modifier.padding(top = HeyTokens.Space.s16), variant = HeyTextVariant.PairTitle)
        if (body.isNotEmpty()) HeyText(body, variant = HeyTextVariant.PairLead, tone = HeyTone.Muted)
        if (action != null) Box(Modifier.padding(top = HeyTokens.Space.s8)) { action() }
    }
}

/** Single-line text input with a hairline rim (hand-typed code, name, chat line). [capitalize] is for codes only. */
@Composable
fun HeyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    capitalize: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    multiline: Boolean = false,
    counter: String? = null,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HeyGap.G8.value)) {
        if (label.isNotEmpty()) HeyText(label, variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            minLines = if (multiline) 4 else 1,
            textStyle = HeyTextVariant.Body.style(c.fgDefault),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HeyTokens.Size.touchWeb)
                .clip(shape)
                .background(c.bgRaised, shape)
                .border(HeyTokens.Border.hairline, if (error != null) c.danger else c.borderDefault, shape)
                .padding(horizontal = HeyTokens.Space.s16, vertical = HeyTokens.Space.s12),
        )
        if (counter != null) HeyText(counter, Modifier.fillMaxWidth(), variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted, align = TextAlign.End)
        if (error != null) HeyText(error, variant = HeyTextVariant.Caption, tone = HeyTone.Danger)
    }
}

// ---- Connected (W2) scene: the studio's `mk-connected-*` whale-sea hero. The inks of the scene have no design token. ----
private val SceneInk = Color(0xFF285A70)
private val SceneChip = Color(0xFFF4FCFF)
private val SceneSpark = Color(0xFFFFF1B0)

private fun sceneIcon(name: String, filled: Boolean, vararg d: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    d.forEach {
        if (filled) b.addPath(addPathNodes(it), pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black))
        else b.addPath(
            addPathNodes(it), pathFillType = PathFillType.NonZero, stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

private val PhoneIcon = sceneIcon("device-mobile", false, "M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M11 18h2")
private val SparkIcon = sceneIcon("sparkle", true, "M12 2l2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z")

/** 342 x 300 hero of the "연결됐어요" page: ocean picture, halo, the character, car and phone chips, sparkles and the badge. */
@Composable
fun ConnectedHero(badge: String, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    Box(
        modifier.fillMaxWidth().height(300.dp)
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 96.dp, bottomEnd = 96.dp))
            .background(c.accentContainer),
    ) {
        Image(
            painterResource(R.drawable.connected_ocean), null, Modifier.fillMaxSize().alpha(0.8f),
            contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, 0.4f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x44E9FAFF), 0.7f to Color(0x30CBF5F0), 1f to Color(0xB0DCF8FF)),
            ),
        )
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 38.dp).size(214.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xA0FFFFFF), Color.Transparent)))
                .border(HeyTokens.Border.hairline, Color(0xA0FFFFFF), CircleShape),
        )
        CharacterStill(stringResource(R.string.pair_brand_cd), Modifier.align(Alignment.TopCenter).padding(top = 20.dp), size = 250.dp)
        ConnectedChip(HeyIcons.Car, Modifier.offset(17.dp, 39.dp), -12f)
        ConnectedChip(PhoneIcon, Modifier.offset(265.dp, 151.dp), 12f)
        InkIcon(SparkIcon, null, SceneSpark, Modifier.offset(272.dp, 35.dp), size = 24.dp)
        InkIcon(SparkIcon, null, SceneSpark, Modifier.offset(34.dp, 219.dp), size = 16.dp)
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clip(RoundedCornerShape(HeyTokens.Sheet.radius))
                .background(SceneChip.copy(alpha = 0.95f)).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(HeyTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkIcon(HeyIcons.Check, null, SceneInk, size = 18.dp)
            InkText(badge, SceneInk, variant = HeyTextVariant.DoneBadge, maxLines = 1)
        }
    }
}

@Composable
private fun ConnectedChip(icon: ImageVector, modifier: Modifier, degrees: Float) {
    Box(
        modifier.rotate(degrees).size(52.dp).clip(RoundedCornerShape(18.dp)).background(SceneChip.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) { InkIcon(icon, null, SceneInk, size = 28.dp) }
}

/** Vertical gap helper for the pixel-measured entry layouts. */
@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))

@Suppress("unused")
private val NoRipple = MutableInteractionSource()

@Suppress("unused")
private val OriginOffset = Offset.Zero
