package ai.hey.phone.feature.pair

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.R
import ai.hey.phone.ui.ConnectedHero
import ai.hey.phone.ui.EntryButton
import ai.hey.phone.ui.EntryFrame
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.InkText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

/** Car linked (studio phone-done): the whale-sea scene, "이제 어디서든, 헤이리와 함께해요" and the pill to the 오늘 tab. Stateless. */
@Composable
fun DoneScreen(onOpenToday: () -> Unit) {
    val c = HeyTheme.colors
    EntryFrame(
        bottom = {
            EntryButton(
                stringResource(R.string.w2_memory), onOpenToday,
                Modifier.fillMaxWidth().padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s24),
                primary = true, large = true, trailing = HeyIcons.ArrowRight,
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = HeyTokens.Space.s24),
            verticalArrangement = Arrangement.spacedBy(HeyTokens.Space.s24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectedHero(stringResource(R.string.w2_badge))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                InkText(stringResource(R.string.w2_title), c.fgDefault, Modifier.fillMaxWidth(), variant = HeyTextVariant.DoneTitle, align = TextAlign.Center)
                InkText(
                    stringResource(R.string.w2_subtitle), c.fgMuted, Modifier.fillMaxWidth().padding(top = HeyTokens.Space.s16),
                    variant = HeyTextVariant.DoneBody, align = TextAlign.Center,
                )
            }
        }
    }
}

/** W2 route for `done`. */
@Composable
fun DoneRoute(onOpenToday: () -> Unit) {
    val app = ai.hey.phone.HeyPhoneApp.from(androidx.compose.ui.platform.LocalContext.current)
    androidx.compose.runtime.LaunchedEffect(Unit) { app.refreshLook() }
    DoneScreen(onOpenToday)
}
