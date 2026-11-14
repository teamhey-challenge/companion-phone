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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

internal val HeyFontFamily = FontFamily(
    Font(TokensR.font.chiron_goround_tc_regular, FontWeight.Normal),
    Font(TokensR.font.chiron_goround_tc_medium, FontWeight.Medium),
    Font(TokensR.font.chiron_goround_tc_semibold, FontWeight.SemiBold),
    Font(TokensR.font.chiron_goround_tc_bold, FontWeight.Bold),
)

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
)
