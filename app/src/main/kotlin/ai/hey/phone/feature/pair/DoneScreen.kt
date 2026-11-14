package ai.hey.phone.feature.pair

import ai.hey.phone.R
import ai.hey.phone.ui.CharacterStill
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeaderBar
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.PairMetrics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

/** W2 (connected): "이제, 함께해요." and the glass pill to the memory page (W3). Stateless. */
@Composable
fun DoneScreen(onOpenMemory: () -> Unit) {
    PageScaffold {
        HeaderBar()
        Box(Modifier.fillMaxWidth().padding(top = PairMetrics.CharacterTop), contentAlignment = Alignment.Center) {
            CharacterStill(stringResource(R.string.pair_brand_cd), size = PairMetrics.CharacterSize)
        }
        HeyText(
            stringResource(R.string.pair_caption),
            Modifier.fillMaxWidth().padding(top = PairMetrics.CaptionTop),
            variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted, align = TextAlign.Center,
        )
        HeyText(stringResource(R.string.w2_title), Modifier.padding(top = PairMetrics.TitleTop), variant = HeyTextVariant.PairTitle)
        HeyText(stringResource(R.string.w2_subtitle), Modifier.padding(top = PairMetrics.LeadTop), variant = HeyTextVariant.PairLead, tone = HeyTone.Muted)
        GlassButton(
            stringResource(R.string.w2_memory),
            onOpenMemory,
            Modifier.padding(top = PairMetrics.DoneButtonTop),
            horizontalPadding = PairMetrics.DoneButtonPadding,
            textVariant = HeyTextVariant.PairAction,
        )
        HeyText(
            stringResource(R.string.w2_note),
            Modifier.fillMaxWidth().padding(top = PairMetrics.DoneNoteTop),
            variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted, align = TextAlign.Center,
        )
    }
}

/** W2 route for `done`. */
@Composable
fun DoneRoute(onOpenMemory: () -> Unit) = DoneScreen(onOpenMemory)
