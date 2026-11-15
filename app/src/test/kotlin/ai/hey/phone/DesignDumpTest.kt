package ai.hey.phone

import ai.hey.phone.data.Friend
import ai.hey.phone.data.Invite
import ai.hey.phone.feature.friends.FriendsScreen
import ai.hey.phone.feature.friends.FriendsUi
import ai.hey.phone.feature.friends.FriendsView
import ai.hey.phone.feature.friends.InboxRow
import ai.hey.phone.feature.home.SettingsScreen
import ai.hey.phone.feature.home.TodayEntry
import ai.hey.phone.feature.home.TodayScreen
import ai.hey.phone.feature.home.TodayUi
import ai.hey.phone.feature.login.ConsentScreen
import ai.hey.phone.feature.login.StartScreen
import ai.hey.phone.feature.memory.MemoryKindUi
import ai.hey.phone.feature.memory.MemoryListUi
import ai.hey.phone.feature.memory.MemoryRowUi
import ai.hey.phone.feature.memory.MemoryScreen
import ai.hey.phone.feature.memory.SpeechUi
import ai.hey.phone.feature.pair.ConnectScreen
import ai.hey.phone.feature.pair.DoneScreen
import ai.hey.phone.feature.pair.JoinUi
import ai.hey.phone.feature.talk.Bubble
import ai.hey.phone.feature.talk.TalkScreen
import ai.hey.phone.feature.talk.TalkStatus
import ai.hey.phone.ui.HeyTheme
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * PLEOS-199: dumps every HeyText/InkText (bounds in dp, TextStyle) to build/reports/design-diff/NAME@390xH.json for
 * ops/design-diff/screens.json (the phone codes). 780x2H px at 320 dpi = 390xH dp, the canvas of the studio phone frames.
 * Runs only with -Pdesign-diff; nothing is recorded or verified. Add a screen = one dump("phone-…") line below and the
 * matching app.dump in screens.json.
 */
private fun phone(heightDp: Int) = DeviceConfig.PIXEL_5.copy(
    screenWidth = 780, screenHeight = heightDp * 2, xdpi = 320, ydpi = 320, density = Density.XHIGH, orientation = ScreenOrientation.PORTRAIT,
)

private val friends = FriendsUi.Items(listOf(Friend("f1", "민수", "2026-11-10", false), Friend("f2", "지은", null, true)))
private val inbox = listOf(InboxRow("m1", "민수", "오늘 어디까지 가?", "2026-11-15T10:00:00Z", "f1"))
private val memoryRows = listOf(
    MemoryRowUi("1", MemoryKindUi.Fact, "비 오는 날, 잔잔한 음악을 좋아해요.", "11월 13일"),
    MemoryRowUi("2", MemoryKindUi.Summary, "퇴근길에는 조용한 대화를 좋아해요.", "11월 12일"),
)

private fun Paparazzi.dumpScreens(h: Int) {
    fun dump(name: String, content: @Composable () -> Unit) {
        val dump = DesignDump("$name@390x$h", 390, h, dark = false)
        snapshot("$name@390x$h") { HeyTheme(false) { dump.capture(content) } }
        dump.write()
    }
    val invite = Invite("K7M2QX", "https://hey.lastmile.ltd/f/K7M2QX", null)
    dump("phone-start") { StartScreen(false, null, null, {}) }
    dump("phone-consent") { ConsentScreen(true, false, false, null, {}, {}, {}) }
    dump("phone-join") {
        ai.hey.phone.feature.pair.ScanContent(
            granted = true, typed = "48271953", wrong = false, onTyped = {}, onAllow = {}, onSubmit = {}, onBack = {},
            title = "", hint = "", wrongText = "코드를 다시 확인해 주세요", pasteLabel = "", camera = {}, email = "jiwoo@example.com",
        )
    }
    dump("phone-done") { DoneScreen {} }
    dump("phone-today") {
        TodayScreen(
            TodayUi.Day("긴장이 설렘이 된 날", emptyList(), null),
            "지우", "11월 15일 일요일", {}, {},
        )
    }
    dump("phone-talk") {
        TalkScreen(listOf(Bubble(1, true, "오늘 비 와서 천천히 갈게"), Bubble(2, false, "좋아, 조심해서 가. 도착하면 얘기해 줘!")), TalkStatus.Idle, null, false, false, "", {}, {}, {}, {})
    }
    dump("phone-friends") { FriendsScreen(friends, inbox, invite, null, false, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}) }
    dump("phone-friends-code") { FriendsScreen(friends, inbox, invite, null, false, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, initialView = FriendsView.Code) }
    dump("phone-friends-add") { FriendsScreen(friends, inbox, null, null, false, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, initialView = FriendsView.Add) }
    dump("phone-friend-accept") {
        FriendsScreen(
            friends, inbox, null, null, false, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {},
            initialView = FriendsView.Requests, requests = listOf(ai.hey.phone.data.FriendRequest("r1", from = ai.hey.phone.data.RequestPerson("u1", "서연"))),
        )
    }
    dump("phone-memory") {
        MemoryScreen(
            list = MemoryListUi.Items(memoryRows), speech = SpeechUi.Banmal, speechSaving = false, unpairing = false, notice = null,
            onBack = {}, onRetry = {}, onDelete = {}, onDeleteAll = {}, onLoadMore = {}, onSpeech = {}, onUnpair = {}, initialConfirm = null,
        )
    }
    dump("phone-settings") { SettingsScreen("민수", true, false, null, false, {}, {}, {}, {}, memoryOn = true) }
    dump("phone-settings-car") { SettingsScreen("민수", true, false, null, false, {}, {}, {}, {}, memoryOn = true) }
}

class DesignDump1000Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(1000), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1000)
}

class DesignDump844Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(844), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(844)
}

class DesignDump1100Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(1100), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1100)
}
