package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyColors
import ai.hey.designsystem.token.HeyDarkColors
import ai.hey.designsystem.token.HeyLightColors
import ai.hey.designsystem.token.HeyTokens
import ai.hey.tokens.R as TokensR
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The only spacing steps (HeyTokens.Space). Containers pass one of these as `gap`. */
enum class HeyGap(val value: Dp) {
    G4(HeyTokens.Space.s4),
    G8(HeyTokens.Space.s8),
    G12(HeyTokens.Space.s12),
    G16(HeyTokens.Space.s16),
    G24(HeyTokens.Space.s24),
    G32(HeyTokens.Space.s32),
}

/**
 * Phone type scale (mockups W1-W3, 360-430 dp canvas). Sizes follow the phone mockups; weight/tracking follow the web
 * tokens. FOLLOW-UP: generate this from the `type.web` tokens in companion-shared/android instead of keeping it here.
 */
enum class HeyTextVariant(val size: TextUnit, val lineHeight: TextUnit, val weight: Int, val trackingEm: Float) {
    Title(32.sp, 44.sp, 500, -0.055f),
    Heading(20.sp, 28.sp, 600, -0.01f),
    Body(17.sp, 28.sp, 400, 0f),
    BodyStrong(17.sp, 26.sp, 600, 0f),
    Label(16.sp, 24.sp, 500, 0f),
    Caption(15.sp, 22.sp, 400, 0f),
    Wordmark(27.sp, 34.sp, 700, -0.02f),
    DialogTitle(28.sp, 38.sp, 600, -0.02f),
    DialogBody(19.sp, 28.sp, 400, 0f),
    // Studio 4dc8ad3 start / consent / link / done / today screens (PLEOS-201 PhoneEntry): px of the mockup = dp/sp.
    EntryBar(22.sp, 28.sp, 600, 0f),
    EntryH1(24.sp, 32.sp, 500, 0f),
    EntryBody(16.sp, 24.sp, 400, 0.027f),
    EntryBodyMedium(16.sp, 24.sp, 500, 0f),
    EntryDesc(14.sp, 20.sp, 400, 0f),
    EntryButton(14.sp, 20.sp, 500, 0f),
    EntryButtonLarge(16.sp, 20.sp, 500, 0f),
    EntryCode(28.sp, 36.sp, 400, 0.12f),
    DoneTitle(30.sp, 39.sp, 600, -0.04f),
    DoneBody(15.sp, 24.sp, 400, 0f),
    DoneBadge(14.sp, 21.sp, 600, 0f),
    DiaryBrand(28.sp, 24.sp, 700, -0.0357f),
    DiaryTitle(32.sp, 44.8.sp, 600, -0.04f),
    DiaryGreeting(15.sp, 27.sp, 400, 0f),
    DiaryCardTitle(20.sp, 30.sp, 600, -0.063f),
    DiaryCardBody(14.sp, 25.2.sp, 400, 0f),
    DiaryCardLink(12.sp, 24.sp, 400, 0f),
    // W1/W2 mockup sizes, measured from delivery-v05 W1.png/W2.png (px / 2 = dp).
    PairTitle(31.sp, 44.sp, 500, -0.055f),
    PairLead(16.47.sp, 26.sp, 400, 0.016f),
    PairRowTitle(17.5.sp, 22.sp, 600, 0f),
    PairCaption(14.sp, 22.sp, 400, 0f),
    PairBody(14.1.sp, 26.sp, 400, 0f),
    /** Text of the W1/W2 buttons: the mockup's 600-weight label measures 16.2 sp. */
    PairAction(16.2.sp, 26.sp, 600, 0f),
}

enum class HeyTone { Default, Muted, Subtle, Disabled, Accent, Danger, OnPrimary }

/** One variable Chiron GoRound TC (wght 200-900, all 11,172 Hangul syllables), as the studio loads it; minSdk is 28. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFont(weight: Int) = Font(
    TokensR.font.chiron_goround_tc_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val HeyFontFamily = FontFamily(
    listOf(300, 400, 450, 500, 600, 700, 800).map(::variableFont),
)

/** The font's ascent + descent (970 + 210 on a 1000 em): the height Compose gives a line before any line-height applies. */
private const val NaturalLineEm = 1.18f

/**
 * CSS half-leading: every line box, first and last included, is exactly `line-height` tall with the glyph box centred.
 * Measured on layoutlib (car TextMetricsTest): Trim.Both + Mode.Tight is the only style that shrinks the first line when
 * the line height is below the natural height; Trim.None + Mode.Fixed grows lines correctly. Same function as the car DS.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal fun TextStyle.cssLeading(): TextStyle {
    val shrinks = lineHeight.value < fontSize.value * NaturalLineEm
    return copy(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = if (shrinks) LineHeightStyle.Trim.Both else LineHeightStyle.Trim.None,
            mode = if (shrinks) LineHeightStyle.Mode.Tight else LineHeightStyle.Mode.Fixed,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

internal val LocalHeyColors = staticCompositionLocalOf<HeyColors> { error("HeyTheme is missing above this composable") }
internal val LocalHeyDark = staticCompositionLocalOf { false }

object HeyTheme {
    val colors: HeyColors
        @Composable @ReadOnlyComposable get() = LocalHeyColors.current
    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalHeyDark.current
}

/** D-26: the phone follows the system theme. */
@Composable
fun HeyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHeyColors provides if (dark) HeyDarkColors else HeyLightColors,
        LocalHeyDark provides dark,
        content = content,
    )
}

internal fun HeyTone.resolve(colors: HeyColors): Color = when (this) {
    HeyTone.Default -> colors.fgDefault
    HeyTone.Muted -> colors.fgMuted
    HeyTone.Subtle -> colors.fgSubtle
    HeyTone.Disabled -> colors.fgDisabled
    HeyTone.Accent -> colors.primary
    HeyTone.Danger -> colors.danger
    HeyTone.OnPrimary -> colors.fgOnPrimary
}

internal fun HeyTextVariant.style(color: Color): TextStyle = TextStyle(
    fontFamily = HeyFontFamily,
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = FontWeight(weight),
    letterSpacing = trackingEm.em,
    color = color,
    lineBreak = LineBreak.Paragraph,
).cssLeading()
