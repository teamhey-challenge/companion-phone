package ai.hey.phone.feature.home

import ai.hey.phone.data.Friend
import ai.hey.phone.data.Invite
import ai.hey.phone.feature.friends.FriendsNotice
import ai.hey.phone.feature.friends.FriendsScreen
import ai.hey.phone.feature.friends.FriendsUi
import ai.hey.phone.feature.friends.InboxRow
import ai.hey.phone.feature.talk.Bubble
import ai.hey.phone.feature.talk.TalkNotice
import ai.hey.phone.feature.talk.TalkScreen
import ai.hey.phone.feature.talk.TalkStatus
import ai.hey.phone.ui.BottomNav
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.NavTab
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

private val phone = DeviceConfig.PIXEL_5.copy(
    screenWidth = 780, screenHeight = 2000, xdpi = 320, ydpi = 320, density = Density.XHIGH, orientation = ScreenOrientation.PORTRAIT,
)

private val friends = FriendsUi.Items(listOf(Friend("f1", "민수", "2026-11-10", false), Friend("f2", "지은", null, true)))
private val inbox = listOf(InboxRow("m1", "민수", "오늘 어디까지 가?", "2026-11-15T10:00:00Z"))
private val tabs = listOf(
    NavTab("today", "오늘", HeyIcons.Book), NavTab("talk", "대화", HeyIcons.Message), NavTab("friends", "친구", HeyIcons.Users),
    NavTab("memory", "기억", HeyIcons.Pin), NavTab("settings", "설정", HeyIcons.Gear),
)

private val shots: List<Pair<String, @Composable () -> Unit>> = listOf(
    "today-day" to {
        TodayScreen(
            TodayUi.Day("오늘도 같이 달렸어. 집 도착 축하해!", listOf(TodayEntry("08:10", "출근길에 나갔어"), TodayEntry("12:30", "민수가 메시지를 보냈어")), "비가 와서 천천히 달린 하루였다."),
            "민수", {},
        )
    },
    "today-quiet" to { TodayScreen(TodayUi.Quiet, null, {}) },
    "today-memory-off" to { TodayScreen(TodayUi.MemoryOff, "민수", {}) },
    "today-error" to { TodayScreen(TodayUi.Failed, "민수", {}) },
    "talk-chat" to {
        TalkScreen(
            listOf(Bubble(1, true, "오늘 비 와서 천천히 갈게"), Bubble(2, false, "좋아, 조심해서 가. 도착하면 얘기해 줘!")),
            TalkStatus.Idle, null, false, false, "", {}, {}, {}, {},
        )
    },
    "talk-empty-notice" to { TalkScreen(emptyList(), TalkStatus.Idle, TalkNotice.NoMic, false, false, "", {}, {}, {}, {}) },
    "talk-consent" to { TalkScreen(emptyList(), TalkStatus.Idle, null, true, false, "", {}, {}, {}, {}) },
    "friends-list" to {
        FriendsScreen(
            friends, inbox, Invite("K7M2QX", "https://hey.lastmile.ltd/f/K7M2QX", null), FriendsNotice.Added("지은"), false,
            {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {},
        )
    },
    "friends-empty" to { FriendsScreen(FriendsUi.Items(emptyList()), emptyList(), null, null, false, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}) },
    "settings" to { SettingsScreen("민수", true, false, null, false, {}, {}, {}, {}) },
    "signup" to { SignupScreen("민수", true, false, false, null, {}, {}, {}, {}, {}) },
    "nav" to { BottomNav(tabs, "talk", {}) },
)

class HomeSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone, renderingMode = RenderingMode.NORMAL)

    @Test fun screens() {
        shots.forEach { (name, content) ->
            listOf(false, true).forEach { dark ->
                paparazzi.snapshot("$name-${if (dark) "dark" else "light"}") { HeyTheme(dark) { content() } }
            }
        }
    }
}
