package ai.hey.phone.feature.memory

import ai.hey.phone.ui.HeyTheme
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

// 780x2000 px at 320 dpi = 390x1000 dp, the same canvas as the 2x mockup PNGs (delivery-v05/W3.png), so the snapshots can be
// laid next to them 1:1.
private val phone = DeviceConfig.PIXEL_5.copy(
    screenWidth = 780,
    screenHeight = 2000,
    xdpi = 320,
    ydpi = 320,
    density = Density.XHIGH,
    orientation = ScreenOrientation.PORTRAIT,
)

private val rows = listOf(
    MemoryRowUi("1", MemoryKindUi.Fact, "비 오는 날, 잔잔한 음악을 좋아해요.", "11월 13일"),
    MemoryRowUi("2", MemoryKindUi.Summary, "퇴근길에는 조용한 대화를 좋아해요.", "11월 12일"),
    MemoryRowUi("3", MemoryKindUi.Fact, "금요일에 중요한 발표가 있어요.", "11월 11일"),
)

private class Shot(
    val list: MemoryListUi,
    val speech: SpeechUi? = SpeechUi.Banmal,
    val notice: NoticeUi? = null,
    val confirm: Any? = null,
    val unpairing: Boolean = false,
)

private val shots: List<Pair<String, Shot>> = listOf(
    "w3-list" to Shot(MemoryListUi.Items(rows)),
    "w3-empty" to Shot(MemoryListUi.Empty, speech = SpeechUi.Jondaetmal),
    "w3-loading" to Shot(MemoryListUi.Loading, speech = null),
    "w3-error" to Shot(MemoryListUi.Failed, speech = null),
    "w3-delete-confirm" to Shot(MemoryListUi.Items(rows), confirm = MemoryConfirms.one("1")),
    "w3-delete-all-confirm" to Shot(MemoryListUi.Items(rows), confirm = MemoryConfirms.all),
    "w3-delete-failed" to Shot(MemoryListUi.Items(rows), notice = NoticeUi.DeleteFailed),
    "w3-unpair-confirm" to Shot(MemoryListUi.Items(rows), confirm = MemoryConfirms.unpair),
)

@Composable
private fun Screen(s: Shot) {
    MemoryScreen(
        list = s.list,
        speech = s.speech,
        speechSaving = false,
        unpairing = s.unpairing,
        notice = s.notice,
        onBack = {},
        onRetry = {},
        onDelete = {},
        onDeleteAll = {},
        onLoadMore = {},
        onSpeech = {},
        onUnpair = {},
        initialConfirm = s.confirm,
    )
}

class MemorySnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone, renderingMode = RenderingMode.NORMAL)

    @Test fun screens() {
        shots.forEach { (name, shot) ->
            listOf(false, true).forEach { dark ->
                paparazzi.snapshot("$name-${if (dark) "dark" else "light"}") { HeyTheme(dark) { Screen(shot) } }
            }
        }
    }
}

/** Font scale 1.3: W3 list, a dialog and the unpair dialog, light and dark. Names end in `-fs130`. */
class MemoryFontScaleSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone.copy(fontScale = 1.3f), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() {
        listOf("w3-list", "w3-delete-confirm", "w3-unpair-confirm", "w3-error", "w3-delete-failed").forEach { name ->
            val shot = shots.first { it.first == name }.second
            listOf(false, true).forEach { dark ->
                paparazzi.snapshot("$name-${if (dark) "dark" else "light"}-fs130") { HeyTheme(dark) { Screen(shot) } }
            }
        }
    }
}
