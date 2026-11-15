package ai.hey.phone.feature.home

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.R
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyCard
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.InkIcon
import ai.hey.phone.ui.InkText
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.VStack
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** "지우야" / "민수야" -> "…아" after a final consonant: the particle that calls a name. */
private fun callName(name: String): String {
    val last = name.trim().lastOrNull() ?: return name
    val batchim = last in '\uAC00'..'\uD7A3' && (last.code - 0xAC00) % 28 != 0
    return name.trim() + if (batchim) "아" else "야"
}

/**
 * Tab 오늘 (studio phone-today, diary view): the Hey wordmark, "오늘도, 함께", the greeting, the day's picture and the "같이 다닌 하루" card
 * (latest trip_done line, opens the memory page), then the day's entries and the diary text once written.
 */
@Composable
fun TodayScreen(ui: TodayUi, name: String?, dateLabel: String, onRetry: () -> Unit, onOpenMemory: () -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    PageScaffold(modifier, insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically) {
            InkText(stringResource(R.string.app_name), c.fgDefault, variant = HeyTextVariant.DiaryBrand, maxLines = 1)
        }
        Column(Modifier.padding(top = HeyTokens.Space.s16, bottom = HeyTokens.Space.s24)) {
            InkText(stringResource(R.string.today_heading), c.fgDefault, variant = HeyTextVariant.DiaryTitle)
            if (ui is TodayUi.Day || ui is TodayUi.Quiet) {
                InkText(
                    if (name.isNullOrBlank()) stringResource(R.string.today_greeting_anon) else stringResource(R.string.today_greeting, callName(name)),
                    c.fgMuted, Modifier.padding(top = 4.dp, bottom = HeyTokens.Space.s24), variant = HeyTextVariant.DiaryGreeting,
                )
            } else {
                Column(Modifier.height(HeyTokens.Space.s24)) {}
            }
            when (ui) {
                TodayUi.Loading -> {
                    CharacterHero()
                    HeyText(stringResource(R.string.today_loading), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                }
                TodayUi.Failed -> VStack(HeyGap.G8) {
                    CharacterHero(Modifier.padding(bottom = HeyGap.G8.value))
                    HeyText(stringResource(R.string.today_error_title), variant = HeyTextVariant.Heading)
                    HeyText(stringResource(R.string.today_error_body), tone = HeyTone.Muted)
                    HeyButton(stringResource(R.string.retry), onRetry)
                }
                TodayUi.MemoryOff -> VStack(HeyGap.G8) {
                    CharacterHero(Modifier.padding(bottom = HeyGap.G8.value))
                    HeyText(stringResource(R.string.memory_off_guide), variant = HeyTextVariant.Heading)
                }
                TodayUi.Quiet -> VStack(HeyGap.G8) {
                    DiaryHero()
                    HeyText(stringResource(R.string.today_empty_title), Modifier.padding(top = HeyTokens.Space.s24), variant = HeyTextVariant.Heading)
                    HeyText(stringResource(R.string.today_empty_body), tone = HeyTone.Muted)
                }
                is TodayUi.Day -> {
                    DiaryHero()
                    DayCard(ui.tripLine, onOpenMemory, Modifier.padding(top = 30.dp))
                    if (ui.summary != null) {
                        VStack(HeyGap.G8, Modifier.padding(top = HeyTokens.Space.s24)) {
                            HeyText(ui.summary)
                            HeyText(stringResource(R.string.today_written_by), variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
                        }
                    }
                    if (ui.entries.isNotEmpty()) {
                        HeyCard(Modifier.padding(top = HeyTokens.Space.s24), tone = 2) {
                            HeyText(stringResource(R.string.today_entries_title), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                            ui.entries.forEach { e ->
                                HeyText(if (e.time.isEmpty()) e.text else "${e.time}  ${e.text}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The day's picture: 216 dp high, 26 dp corners, cropped. */
@Composable
private fun DiaryHero(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.diary_polaroid),
        contentDescription = stringResource(R.string.today_photo_cd),
        modifier = modifier.fillMaxWidth().height(216.dp).clip(RoundedCornerShape(26.dp)),
        contentScale = ContentScale.Crop,
    )
}

/** "같이 다닌 하루": book icon, the day's line and "오늘의 일기 펼쳐 보기", an arrow at the right. */
@Composable
private fun DayCard(tripLine: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Sheet.radius)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.bgRaised, shape).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = HeyTokens.Space.s24),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIcon(HeyIcons.Book, null, c.primary, Modifier.padding(bottom = 76.dp), size = 24.dp)
        Column(Modifier.weight(1f)) {
            InkText(stringResource(R.string.today_card_title), c.fgDefault, variant = HeyTextVariant.DiaryCardTitle)
            InkText(tripLine ?: stringResource(R.string.today_summary_wait), c.fgDefault, Modifier.padding(vertical = 10.dp), variant = HeyTextVariant.DiaryCardBody)
            InkText(stringResource(R.string.today_open_diary), c.primary, variant = HeyTextVariant.DiaryCardLink)
        }
        InkIcon(HeyIcons.ArrowRight, null, c.primary, size = 18.dp)
    }
}
