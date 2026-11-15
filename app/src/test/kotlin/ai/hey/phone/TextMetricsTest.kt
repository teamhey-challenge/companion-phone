package ai.hey.phone

import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.LineHeightStyle
import ai.hey.phone.ui.HeyFontFamily
import ai.hey.phone.ui.cssLeading
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Text renders like the studio: CSS half-leading line boxes and the full variable Chiron GoRound TC.
 * Density 1.0 here, so 1 sp = 1 px = 1 CSS pixel. Paparazzi supplies the layoutlib text stack; nothing is recorded.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
class TextMetricsTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_C)

    private val density = Density(1f, 1f)

    private fun style(size: Float, line: Float, weight: Int = 400) = TextStyle(
        fontFamily = HeyFontFamily,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = FontWeight(weight),
    ).cssLeading()

    private fun paragraph(text: String, style: TextStyle) = Paragraph(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = 4000),
        density = density,
        fontFamilyResolver = createFontFamilyResolver(paparazzi.context),
    )

    /** CSS: baseline = (L - (ascent + descent) * s) / 2 + ascent * s, with the font's ascent 970 / descent 210 on a 1000 em. */
    private fun cssBaseline(size: Float, line: Float) = (line - 1.18f * size) / 2f + 0.97f * size

    @Test fun firstBaselineMatchesCssHalfLeading() {
        val failures = mutableListOf<String>()
        for ((size, line) in listOf(150f to 150f, 28f to 39.2f, 24f to 32f, 60f to 78f, 40f to 40f, 40f to 100f)) {
            val p = paragraph("헤이리", style(size, line))
            val baseline = p.getLineBaseline(0)
            if (kotlin.math.abs(cssBaseline(size, line) - baseline) > 1f) {
                failures += "${size}sp/${line}sp css=${cssBaseline(size, line)} got=$baseline top=${p.getLineTop(0)} bottom=${p.getLineBottom(0)} h=${p.height}"
            }
        }
        val two = paragraph("헤이리\n헤이리", style(150f, 150f))
        assertEquals("line pitch of a 150/150 two-line text", 150f, two.getLineBaseline(1) - two.getLineBaseline(0), 1f)
        assertEquals("height of a 150/150 two-line text", 300f, two.height, 1f)
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    /** 뒷 (U+B4B7), 뱟 (U+BC5F), 썩 (U+C369) are not among KS X 1001's 2,350 syllables the old static subset carried. */
    @Test fun syllablesOutsideTheOldSubsetUseTheBrandFont() {
        val font = paragraph("가", style(1000f, 1000f)).maxIntrinsicWidth
        for (ch in listOf("뒷", "뱟", "썩")) {
            val width = paragraph(ch, style(1000f, 1000f)).maxIntrinsicWidth
            // The brand font sets Hangul on a full em (1000 units); a system-font fallback has a different advance.
            assertEquals("$ch advance must equal the brand font's 가 advance", font, width, 1f)
        }
    }

    @Test fun weight450IsItsOwnInstance() {
        val w = listOf(400, 450, 500).map { paragraph("Weight 가나다라마바사 gOlden", style(40f, 56f, it)).maxIntrinsicWidth }
        assertTrue("450 must lie strictly between 400 and 500: $w", w[0] < w[1] && w[1] < w[2])
    }
}
