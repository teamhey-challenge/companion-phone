package ai.hey.phone.feature.pair

import ai.hey.phone.ui.HeyTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test

/** Phone canvas of the delivery-v05 mockups: 2x pixels (xhdpi), so px / 2 = dp. W1 is 750x2076 px, W2 780x2000 px. */
private fun phone(width: Int, height: Int) = DeviceConfig.PIXEL_5.copy(
    screenWidth = width, screenHeight = height, xdpi = 320, ydpi = 320, density = Density.XHIGH,
)

private val w1: List<Pair<String, JoinUi>> = listOf(
    "w1-default" to JoinUi.Ready(),
    "w1-ticked" to JoinUi.Ready(voice = true, memory = true),
    "w1-required-missing" to JoinUi.Ready(notice = Notice.VoiceRequired),
    "w1-saving" to JoinUi.Ready(voice = true, saving = true),
    "w1-save-failed" to JoinUi.Ready(voice = true, notice = Notice.SaveFailed),
    "w1-expired" to JoinUi.Invalid,
    "w1-no-code" to JoinUi.NoCode,
)

class PairSnapshotTest {
    @get:Rule val w1Rule = Paparazzi(deviceConfig = phone(750, 2076))

    @Test fun w1() {
        listOf(false, true).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            w1.forEach { (name, ui) ->
                w1Rule.snapshot("$name-$theme") {
                    HeyTheme(dark) { JoinScreen(ui, {}, {}, {}, {}, {}, {}) }
                }
            }
        }
    }
}

class DoneSnapshotTest {
    @get:Rule val rule = Paparazzi(deviceConfig = phone(780, 2000))

    @Test fun w2() {
        listOf(false, true).forEach { dark ->
            rule.snapshot("w2-${if (dark) "dark" else "light"}") { HeyTheme(dark) { DoneScreen {} } }
        }
    }
}

/** Font scale 1.3 (system "large text"): W1 states with the longest copy, light and dark. Names end in `-fs130`. */
class PairFontScaleSnapshotTest {
    @get:Rule val rule = Paparazzi(deviceConfig = phone(750, 2076).copy(fontScale = 1.3f))

    @Test fun w1() {
        listOf(false, true).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            listOf("w1-default" to JoinUi.Ready(), "w1-required-missing" to JoinUi.Ready(notice = Notice.VoiceRequired), "w1-expired" to JoinUi.Invalid, "w1-no-code" to JoinUi.NoCode).forEach { (name, ui) ->
                rule.snapshot("$name-$theme-fs130") { HeyTheme(dark) { JoinScreen(ui, {}, {}, {}, {}, {}, {}) } }
            }
            rule.snapshot("w2-$theme-fs130") { HeyTheme(dark) { DoneScreen {} } }
        }
    }
}
