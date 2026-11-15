package ai.hey.phone.feature.pair

import ai.hey.phone.feature.login.LoginError
import ai.hey.phone.feature.login.StartScreen
import ai.hey.phone.ui.HeyTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test

/** Phone canvas of the delivery-v05 mockups: 2x pixels (xhdpi), so px / 2 = dp. W1 is 750x2076 px, W2 780x2000 px. */
private fun phone(width: Int, height: Int) = DeviceConfig.PIXEL_5.copy(
    screenWidth = width, screenHeight = height, xdpi = 320, ydpi = 320, density = Density.XHIGH,
)

private val joinShots: List<Pair<String, JoinUi>> = listOf("join-loading" to JoinUi.Loading) +
    JoinError.entries.map { "join-err-${it.name.lowercase()}" to JoinUi.Failed(it) }

private val scanHint = "먼저 차에서 폰 연결 QR을 띄워 주세요"

class PairSnapshotTest {
    @get:Rule val rule = Paparazzi(deviceConfig = phone(750, 2076))

    @Test fun start() {
        listOf(false, true).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            rule.snapshot("start-$theme") { HeyTheme(dark) { StartScreen(false, null, null, {}) } }
            rule.snapshot("start-busy-$theme") { HeyTheme(dark) { StartScreen(true, null, null, {}) } }
            rule.snapshot("start-unconfigured-$theme") { HeyTheme(dark) { StartScreen(false, LoginError.Unconfigured, null, {}) } }
            rule.snapshot("start-verify-$theme") { HeyTheme(dark) { StartScreen(false, LoginError.Verify, null, {}) } }
            rule.snapshot("start-network-$theme") { HeyTheme(dark) { StartScreen(false, LoginError.Network, null, {}) } }
            rule.snapshot("start-rate-$theme") { HeyTheme(dark) { StartScreen(false, LoginError.Rate, null, {}) } }
            rule.snapshot("start-elsewhere-$theme") { HeyTheme(dark) { StartScreen(false, null, ai.hey.phone.R.string.login_notice_elsewhere, {}) } }
            rule.snapshot("start-expired-$theme") { HeyTheme(dark) { StartScreen(false, null, ai.hey.phone.R.string.login_notice_expired, {}) } }
        }
    }

    @Test fun join() {
        listOf(false, true).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            joinShots.forEach { (name, ui) ->
                rule.snapshot("$name-$theme") { HeyTheme(dark) { ConnectScreen(ui, {}, {}, {}, {}) } }
            }
        }
    }

    @Test fun scan() {
        listOf(false, true).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            // Camera allowed: the preview slot is a plain box (no camera in Paparazzi).
            rule.snapshot("scan-camera-$theme") {
                HeyTheme(dark) {
                    ScanContent(
                        granted = true, typed = "", wrong = false, onTyped = {}, onAllow = {}, onSubmit = {}, onBack = {},
                        title = "QR을 비춰 주세요", hint = scanHint, wrongText = "코드를 다시 확인해 주세요", pasteLabel = "8자리 연결 코드 넣기",
                        camera = { m -> Box(m.fillMaxWidth().height(300.dp).background(androidx.compose.ui.graphics.Color.Gray)) },
                    )
                }
            }
            // Camera denied: the 8-digit code entry (6.6), empty / typed / wrong.
            listOf("code-entry" to ("" to false), "code-typed" to ("K7M2QX9P" to false), "code-wrong" to ("K7M" to true)).forEach { (name, v) ->
                rule.snapshot("scan-$name-$theme") {
                    HeyTheme(dark) {
                        ScanContent(
                            granted = false, typed = v.first, wrong = v.second, onTyped = {}, onAllow = {}, onSubmit = {}, onBack = {},
                            title = "QR을 비춰 주세요", hint = scanHint, wrongText = "코드를 다시 확인해 주세요", pasteLabel = "8자리 연결 코드 넣기",
                            camera = {},
                        )
                    }
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
            // the look picked in the car (bucket hat + scarf, peach body) shows on the phone too
            ai.hey.phone.ui.LookStore.look = ai.hey.phone.ui.CharacterLook(bodyColor = 1, hat = 2, neck = 1)
            try {
                rule.snapshot("w2-look-${if (dark) "dark" else "light"}") { HeyTheme(dark) { DoneScreen {} } }
            } finally {
                ai.hey.phone.ui.LookStore.look = ai.hey.phone.ui.CharacterLook()
            }
        }
    }
}

/** Font scale 1.3 (system "large text"): the start and join states with the longest copy, light and dark. Names end in `-fs130`. */
class PairFontScaleSnapshotTest {
    @get:Rule val rule = Paparazzi(deviceConfig = phone(750, 2076).copy(fontScale = 1.3f))

    @Test fun screens() {
        listOf(false, true).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            rule.snapshot("start-$theme-fs130") { HeyTheme(dark) { StartScreen(false, LoginError.Verify, null, {}) } }
            listOf("join-err-carlinkedtoother" to JoinUi.Failed(JoinError.CarLinkedToOther), "join-err-googlelinkedtoothercar" to JoinUi.Failed(JoinError.GoogleLinkedToOtherCar)).forEach { (name, ui) ->
                rule.snapshot("$name-$theme-fs130") { HeyTheme(dark) { ConnectScreen(ui, {}, {}, {}, {}) } }
            }
            rule.snapshot("w2-$theme-fs130") { HeyTheme(dark) { DoneScreen {} } }
        }
    }
}
