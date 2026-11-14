package ai.hey.phone.feature.trips

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.R
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.ChoiceOption
import ai.hey.phone.ui.ConfirmDialog
import ai.hey.phone.ui.HStack
import ai.hey.phone.ui.HeaderBar
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyButtonVariant
import ai.hey.phone.ui.HeyDivider
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyIconButton
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTheme
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.SegmentedChoice
import ai.hey.phone.ui.SkeletonRow
import ai.hey.phone.ui.VStack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

private sealed interface Confirm {
    data class One(val id: String) : Confirm
    data object All : Confirm
}

/**
 * "함께 다닌 길": heading, 말투 choice (진지하게 / 장난스럽게), stats, badge shelf, visited 동네 tiles per 시·도, trip list,
 * "기록 모두 지우기". Stateless except for the open confirmation. No coordinates, no scores, no "how much is left".
 */
@Composable
fun TripsScreen(
    state: TripsUi,
    notice: TripsNoticeUi?,
    tone: ToneUi,
    onTone: (ToneUi) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
    initialConfirm: Any? = null,
) {
    var confirm by remember { mutableStateOf(initialConfirm as Confirm?) }
    val content = (state as? TripsUi.Content)?.data
    (confirm as? Confirm.One)?.let { one -> if (content?.trips?.none { it.id == one.id } != false) confirm = null }
    if (confirm == Confirm.All && content == null) confirm = null

    Box(modifier) {
        PageScaffold {
            HeaderBar(onBack = onBack, backLabel = stringResource(R.string.trips_back))
            VStack(HeyGap.G32) {
                VStack(HeyGap.G24, Modifier.padding(top = HeyTokens.Space.s16)) {
                    HeyText(stringResource(R.string.trips_title), variant = HeyTextVariant.Title)
                    HeyText(stringResource(R.string.trips_guide), tone = HeyTone.Muted)
                }
                notice?.let { HeyNotice(stringResource(if (it == TripsNoticeUi.ToneFailed) R.string.trips_tone_failed else R.string.trips_delete_failed)) }
                if (state is TripsUi.Content || state is TripsUi.Empty) {
                    SegmentedChoice(
                        options = listOf(
                            ChoiceOption(ToneUi.Serious, stringResource(R.string.trips_tone_serious)),
                            ChoiceOption(ToneUi.Playful, stringResource(R.string.trips_tone_playful)),
                        ),
                        selected = tone,
                        onSelect = onTone,
                    )
                }
                when (state) {
                    TripsUi.Loading -> VStack(HeyGap.G16) {
                        CharacterHero()
                        HeyText(stringResource(R.string.trips_loading), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                        repeat(3) { SkeletonRow() }
                    }
                    TripsUi.Failed -> Message(R.string.trips_error_title, R.string.trips_error_message) {
                        HeyButton(stringResource(R.string.trips_retry), onRetry)
                    }
                    TripsUi.Disabled -> Message(R.string.trips_disabled_title, R.string.trips_disabled_message)
                    TripsUi.Empty -> Message(R.string.trips_empty_title, R.string.trips_empty_message)
                    is TripsUi.Content -> ContentBody(state.data, onAskDelete = { confirm = Confirm.One(it) }, onAskDeleteAll = { confirm = Confirm.All })
                }
            }
        }
        when (val open = confirm) {
            is Confirm.One -> ConfirmDialog(
                title = stringResource(R.string.trips_delete_title),
                message = stringResource(R.string.trips_delete_message),
                confirmText = stringResource(R.string.trips_delete_confirm),
                dismissText = stringResource(R.string.trips_cancel),
                onConfirm = { onDelete(open.id) },
                onDismiss = { confirm = null },
                busy = open.id in (content?.busyIds ?: emptySet()),
            )
            Confirm.All -> ConfirmDialog(
                title = stringResource(R.string.trips_delete_all_title),
                message = stringResource(R.string.trips_delete_all_message),
                confirmText = stringResource(R.string.trips_delete_all_confirm),
                dismissText = stringResource(R.string.trips_cancel),
                onConfirm = onDeleteAll,
                onDismiss = { confirm = null },
                busy = content?.allBusy == true,
            )
            null -> Unit
        }
    }
}

@Composable
private fun Message(title: Int, body: Int, action: (@Composable () -> Unit)? = null) {
    VStack(HeyGap.G8) {
        CharacterHero(Modifier.padding(bottom = HeyGap.G8.value))
        HeyText(stringResource(title), variant = HeyTextVariant.Heading)
        HeyText(stringResource(body), tone = HeyTone.Muted)
        action?.invoke()
    }
}

@Composable
private fun ContentBody(d: TripsContentUi, onAskDelete: (String) -> Unit, onAskDeleteAll: () -> Unit) {
    VStack(HeyGap.G32) {
        Stats(d)
        if (d.badges.isNotEmpty()) BadgeShelf(d.badges)
        Regions(d.sidos)
        VStack(HeyGap.G16) {
            HeyText(stringResource(R.string.trips_list), variant = HeyTextVariant.Heading)
            d.trips.forEach { row ->
                TripCard(row, enabled = row.id !in d.busyIds && !d.allBusy, onDelete = { onAskDelete(row.id) })
            }
        }
        HeyDivider()
        HeyButton(stringResource(R.string.trips_delete_all), onAskDeleteAll, variant = HeyButtonVariant.Danger, enabled = !d.allBusy)
    }
}

@Composable
private fun Stats(d: TripsContentUi) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.lg)
    VStack(HeyGap.G4, Modifier.fillMaxWidth().clip(shape).background(c.secondaryContainer, shape).padding(HeyTokens.Space.s24)) {
        HeyText(stringResource(R.string.trips_stat_count, d.tripCount), variant = HeyTextVariant.Heading)
        HeyText(
            d.totalKm?.let { stringResource(R.string.trips_stat_km, km(it)) } ?: stringResource(R.string.trips_stat_km_unknown),
            tone = HeyTone.Muted,
        )
        HeyText(stringResource(R.string.trips_stat_regions, d.regionCount), tone = HeyTone.Muted)
    }
}

@Composable
private fun BadgeShelf(badges: List<BadgeUi>) {
    VStack(HeyGap.G16) {
        HeyText(stringResource(R.string.trips_badges), variant = HeyTextVariant.Heading)
        badges.chunked(2).forEach { pair ->
            HStack(HeyGap.G12, Modifier.fillMaxWidth(), vertical = Alignment.Top) {
                pair.forEach { BadgeCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeCard(b: BadgeUi, modifier: Modifier) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    val bg = if (b.earned) c.scenePeach else c.bgSunken
    val tone = if (b.earned) HeyTone.Default else HeyTone.Subtle
    VStack(HeyGap.G4, modifier.clip(shape).background(bg, shape).padding(HeyTokens.Space.s16)) {
        HeyText(b.title, variant = HeyTextVariant.BodyStrong, tone = tone)
        HeyText(b.description, variant = HeyTextVariant.Caption, tone = if (b.earned) HeyTone.Muted else HeyTone.Subtle)
        if (b.playful) HeyText(stringResource(R.string.trips_badge_playful), variant = HeyTextVariant.Caption, tone = HeyTone.Accent)
        if (b.earned) HeyText(stringResource(R.string.trips_badge_earned), variant = HeyTextVariant.Label, tone = tone)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Regions(sidos: List<SidoUi>) {
    VStack(HeyGap.G16) {
        HeyText(stringResource(R.string.trips_regions), variant = HeyTextVariant.Heading)
        if (sidos.isEmpty()) {
            HeyText(stringResource(R.string.trips_regions_empty), tone = HeyTone.Muted)
        } else {
            HeyText(stringResource(R.string.trips_regions_note), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
            sidos.forEach { s ->
                VStack(HeyGap.G12) {
                    HeyText(s.name, variant = HeyTextVariant.BodyStrong)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(HeyGap.G8.value),
                        verticalArrangement = Arrangement.spacedBy(HeyGap.G8.value),
                    ) {
                        s.tiles.forEach { RegionTile(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionTile(t: RegionTileUi) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.sm)
    HStack(
        HeyGap.G4,
        Modifier.clip(shape).background(c.accentContainer, shape)
            .padding(horizontal = HeyTokens.Space.s12, vertical = HeyTokens.Space.s8),
    ) {
        HeyText(t.name, variant = HeyTextVariant.Label)
        if (t.demo) HeyText(stringResource(R.string.trips_region_demo), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
    }
}

@Composable
private fun TripCard(row: TripRowUi, enabled: Boolean, onDelete: () -> Unit) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    HStack(
        HeyGap.G12,
        Modifier.fillMaxWidth().clip(shape).background(c.bgRaised, shape)
            .border(HeyTokens.Border.hairline, c.borderDefault, shape)
            .padding(start = HeyTokens.Space.s16, top = HeyTokens.Space.s16, bottom = HeyTokens.Space.s16, end = HeyTokens.Space.s8),
    ) {
        VStack(HeyGap.G4, Modifier.weight(1f)) {
            HeyText(row.date, variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
            HeyText(row.destination ?: stringResource(R.string.trips_no_destination), variant = HeyTextVariant.BodyStrong, tone = if (row.destination == null) HeyTone.Muted else HeyTone.Default)
            row.region?.let { HeyText(it, variant = HeyTextVariant.Caption, tone = HeyTone.Muted) }
            val parts = listOfNotNull(
                row.durationMin?.let { stringResource(R.string.trips_minutes, it) },
                row.distanceKm?.let { stringResource(R.string.trips_km, km(it)) },
            )
            if (parts.isNotEmpty()) HeyText(parts.joinToString(" · "), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
        }
        HeyIconButton(HeyIcons.Trash, stringResource(R.string.trips_delete_label), onDelete, enabled = enabled, size = 48.dp)
    }
}

private fun km(v: Double): String = String.format(Locale.US, "%.1f", v)

/** Dialog states for snapshots: the screen opens with this confirmation already showing. */
object TripsConfirms {
    fun one(id: String): Any = Confirm.One(id)
    val all: Any = Confirm.All
}
