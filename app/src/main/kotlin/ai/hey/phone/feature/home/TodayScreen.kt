package ai.hey.phone.feature.home

import ai.hey.phone.R
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyCard
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.VStack
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

/** Tab 오늘: greeting, the day's brand line (latest trip_done), the day's entries and the diary text once written. */
@Composable
fun TodayScreen(ui: TodayUi, name: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    PageScaffold(modifier, insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) {
        VStack(HeyGap.G24, Modifier.padding(top = HeyGap.G32.value, bottom = HeyGap.G24.value)) {
            HeyText(
                if (name.isNullOrBlank()) stringResource(R.string.today_greeting_anon) else stringResource(R.string.today_greeting, name),
                variant = HeyTextVariant.Title,
            )
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
                TodayUi.Quiet -> VStack(HeyGap.G8) {
                    CharacterHero(Modifier.padding(bottom = HeyGap.G8.value))
                    HeyText(stringResource(R.string.today_empty_title), variant = HeyTextVariant.Heading)
                    HeyText(stringResource(R.string.today_empty_body), tone = HeyTone.Muted)
                }
                is TodayUi.Day -> {
                    CharacterHero(size = 96.dp)
                    ui.tripLine?.let {
                        HeyCard(tone = 0) {
                            HeyText(stringResource(R.string.today_trip_title), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                            HeyText(it, variant = HeyTextVariant.Heading)
                        }
                    }
                    HeyCard(tone = 1) {
                        HeyText(stringResource(R.string.today_summary_title), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                        HeyText(ui.summary ?: stringResource(R.string.today_summary_wait), tone = if (ui.summary == null) HeyTone.Muted else HeyTone.Default)
                    }
                    if (ui.entries.isNotEmpty()) {
                        HeyCard(tone = 2) {
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
