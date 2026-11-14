package ai.hey.phone.feature.trips

import ai.hey.phone.ui.HeyTheme
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

// 390 dp wide, tall enough for the whole list.
private val phone = DeviceConfig.PIXEL_5.copy(
    screenWidth = 780,
    screenHeight = 3000,
    xdpi = 320,
    ydpi = 320,
    density = Density.XHIGH,
    orientation = ScreenOrientation.PORTRAIT,
)

private val badges = listOf(
    BadgeUi("first-ride", "첫 번째 길", "처음으로 함께 달린 길", playful = false, earned = true),
    BadgeUi("new-region", "새 동네", "처음 가 보는 동네를 만났어요", playful = false, earned = true),
    BadgeUi("regular", "단골 길", "자주 달리는 길이 생겼어요", playful = false, earned = false),
    BadgeUi("parking-master", "최고의 레이서!", "주차 실력도 레이서급이에요", playful = true, earned = true),
    BadgeUi("relaxed-day", "느긋한 하루", "서두르지 않은 날이에요", playful = false, earned = false),
    BadgeUi("welcome-back", "다시 만나요", "오랜만에 다시 함께했어요", playful = true, earned = false),
)

private val sidos = listOf(
    SidoUi("서울특별시", listOf(RegionTileUi("강남구"), RegionTileUi("송파구"))),
    SidoUi("경기도", listOf(RegionTileUi("하남시", demo = true))),
)

private val rows = listOf(
    TripRowUi("t1", "11월 14일", "스타필드 하남", "경기도 하남시", 23, 12.3),
    TripRowUi("t2", "11월 13일", null, "서울특별시 강남구", 41, null),
    TripRowUi("t3", "11월 11일", null, null, null, null),
)

private val content = TripsContentUi(
    tripCount = 7, totalKm = 123.4, regionCount = 3, badges = badges, sidos = sidos, trips = rows,
)

private class Shot(val state: TripsUi, val tone: ToneUi = ToneUi.Serious, val notice: TripsNoticeUi? = null, val confirm: Any? = null)

private val shots: List<Pair<String, Shot>> = listOf(
    "trips-list" to Shot(TripsUi.Content(content)),
    "trips-list-playful" to Shot(TripsUi.Content(content), tone = ToneUi.Playful),
    "trips-list-no-km-no-regions" to Shot(TripsUi.Content(content.copy(totalKm = null, sidos = emptyList(), regionCount = 0))),
    "trips-empty" to Shot(TripsUi.Empty),
    "trips-disabled" to Shot(TripsUi.Disabled),
    "trips-delete-all-confirm" to Shot(TripsUi.Content(content), confirm = TripsConfirms.all),
    "trips-delete-confirm" to Shot(TripsUi.Content(content), confirm = TripsConfirms.one("t1")),
)

@Composable
private fun Screen(s: Shot) {
    TripsScreen(
        state = s.state,
        notice = s.notice,
        tone = s.tone,
        onTone = {},
        onBack = {},
        onRetry = {},
        onDelete = {},
        onDeleteAll = {},
        initialConfirm = s.confirm,
    )
}

class TripsSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone, renderingMode = RenderingMode.NORMAL)

    @Test fun screens() {
        shots.forEach { (name, shot) ->
            listOf(false, true).forEach { dark ->
                paparazzi.snapshot("$name-${if (dark) "dark" else "light"}") { HeyTheme(dark) { Screen(shot) } }
            }
        }
    }
}
