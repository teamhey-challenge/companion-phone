package ai.hey.phone.feature.memory

import ai.hey.designsystem.token.HeyTokens
import ai.hey.phone.R
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.ChoiceOption
import ai.hey.phone.ui.ConfirmDialog
import ai.hey.phone.ui.AppBar
import androidx.compose.ui.unit.dp
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyButtonVariant
import ai.hey.phone.ui.HeyDivider
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.MemoryRow
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.SegmentedChoice
import ai.hey.phone.ui.SkeletonRow
import ai.hey.phone.ui.VStack
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

/** Which confirmation is open. Held by the screen; the rows and the model never know about it. */
private sealed interface Confirm {
    data class One(val id: String) : Confirm
    data object All : Confirm
    data object Unpair : Confirm
}

/**
 * W3 (PRD 4.11): heading, guide line, memory rows with trash buttons, "기억 모두 지우기", then the phone settings (말투) and
 * "연결 해제". Stateless except for the open confirmation. A row that vanished (deleted, reloaded away) closes its dialog.
 */
@Composable
fun MemoryScreen(
    list: MemoryListUi,
    speech: SpeechUi?,
    speechSaving: Boolean,
    unpairing: Boolean,
    notice: NoticeUi?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onLoadMore: () -> Unit,
    onSpeech: (SpeechUi) -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
    initialConfirm: Any? = null,
    /** False for a phone-only account: there is no car to disconnect from, so the button is not shown. */
    canUnpair: Boolean = true,
    insets: androidx.compose.foundation.layout.WindowInsets = androidx.compose.foundation.layout.WindowInsets.safeDrawing,
) {
    var confirm by remember { mutableStateOf(initialConfirm as Confirm?) }
    val items = list as? MemoryListUi.Items
    (confirm as? Confirm.One)?.let { one -> if (items?.rows?.none { it.id == one.id } != false) confirm = null }
    if (confirm == Confirm.All && items == null) confirm = null
    if (confirm == Confirm.Unpair && !unpairing && notice == NoticeUi.UnpairFailed) confirm = null

    Box(modifier) {
        PageScaffold(insets = insets) {
            // Studio W3 (compact): 64 dp-class app bar with back + 기억, no body heading, 16/24 muted guide, 16 dp rows.
            AppBar(stringResource(R.string.memory_title), onBack, stringResource(R.string.memory_back), sidePadding = 0.dp)
            VStack(HeyGap.G24, Modifier.padding(bottom = HeyTokens.Space.s24)) {
                HeyText(stringResource(R.string.memory_guide), tone = HeyTone.Muted, variant = HeyTextVariant.ButtonLabelRegular)
                notice?.let { HeyNotice(stringResource(it.message())) }
                when (list) {
                    MemoryListUi.Loading -> VStack(HeyGap.G16) {
                        CharacterHero()
                        HeyText(stringResource(R.string.memory_loading), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                        repeat(3) { SkeletonRow() }
                    }
                    MemoryListUi.Failed -> VStack(HeyGap.G8) {
                        CharacterHero(Modifier.padding(bottom = HeyGap.G8.value))
                        HeyText(stringResource(R.string.memory_error_title), variant = HeyTextVariant.Heading)
                        HeyText(stringResource(R.string.memory_error_message), tone = HeyTone.Muted)
                        HeyButton(stringResource(R.string.memory_retry), onRetry)
                    }
                    MemoryListUi.Off -> VStack(HeyGap.G8) {
                        CharacterHero(Modifier.padding(bottom = HeyGap.G8.value))
                        HeyText(stringResource(R.string.memory_off_guide), variant = HeyTextVariant.Heading)
                    }
                    MemoryListUi.Empty -> VStack(HeyGap.G8) {
                        CharacterHero(Modifier.padding(bottom = HeyGap.G8.value))
                        HeyText(stringResource(R.string.memory_empty_title), variant = HeyTextVariant.Heading)
                        HeyText(stringResource(R.string.memory_empty_message), tone = HeyTone.Muted)
                    }
                    is MemoryListUi.Items -> VStack(HeyGap.G16) {
                        list.rows.forEachIndexed { index, row ->
                            MemoryRow(
                                kind = stringResource(if (row.kind == MemoryKindUi.Fact) R.string.memory_kind_fact else R.string.memory_kind_summary),
                                text = row.text,
                                date = row.date,
                                deleteLabel = stringResource(R.string.memory_delete_label),
                                onDelete = { confirm = Confirm.One(row.id) },
                                index = index,
                                enabled = row.id !in list.busyIds && !list.allBusy,
                            )
                        }
                        if (list.hasMore) {
                            HeyButton(stringResource(R.string.memory_more), onLoadMore, enabled = !list.loadingMore)
                        }
                        HeyButton(
                            stringResource(R.string.memory_delete_all),
                            { confirm = Confirm.All },
                            variant = HeyButtonVariant.Text,
                            enabled = !list.allBusy,
                        )
                    }
                }
                HeyDivider()
                VStack(HeyGap.G16) {
                    HeyText(stringResource(R.string.memory_speech), variant = HeyTextVariant.Heading)
                    SegmentedChoice(
                        options = listOf(
                            ChoiceOption(SpeechUi.Banmal, stringResource(R.string.memory_speech_banmal)),
                            ChoiceOption(SpeechUi.Jondaetmal, stringResource(R.string.memory_speech_jondaetmal)),
                        ),
                        selected = speech,
                        onSelect = onSpeech,
                        enabled = speech != null && !speechSaving,
                    )
                    HeyText(stringResource(R.string.memory_speech_note), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                }
                if (canUnpair) HeyButton(stringResource(R.string.memory_unpair), { confirm = Confirm.Unpair }, variant = HeyButtonVariant.Danger, enabled = !unpairing)
            }
        }
        when (val open = confirm) {
            is Confirm.One -> ConfirmDialog(
                title = stringResource(R.string.memory_delete_title),
                message = stringResource(R.string.memory_delete_message),
                confirmText = stringResource(R.string.memory_delete_confirm),
                dismissText = stringResource(R.string.memory_cancel),
                onConfirm = { onDelete(open.id) },
                onDismiss = { confirm = null },
                busy = open.id in (items?.busyIds ?: emptySet()),
            )
            Confirm.All -> ConfirmDialog(
                title = stringResource(R.string.memory_delete_all_title),
                message = stringResource(R.string.memory_delete_all_message),
                confirmText = stringResource(R.string.memory_delete_all_confirm),
                dismissText = stringResource(R.string.memory_cancel),
                onConfirm = onDeleteAll,
                onDismiss = { confirm = null },
                busy = items?.allBusy == true,
            )
            Confirm.Unpair -> ConfirmDialog(
                title = stringResource(R.string.memory_unpair_title),
                message = stringResource(R.string.memory_unpair_message),
                confirmText = stringResource(R.string.memory_unpair_confirm),
                dismissText = stringResource(R.string.memory_cancel),
                onConfirm = onUnpair,
                onDismiss = { confirm = null },
                busy = unpairing,
            )
            null -> Unit
        }
    }
}

private fun NoticeUi.message() = when (this) {
    NoticeUi.DeleteFailed -> R.string.memory_delete_failed
    NoticeUi.SpeechFailed -> R.string.memory_speech_failed
    NoticeUi.UnpairFailed -> R.string.memory_unpair_failed
}

/** Dialog states for snapshots: the screen opens with this confirmation already showing. */
object MemoryConfirms {
    fun one(id: String): Any = Confirm.One(id)
    val all: Any = Confirm.All
    val unpair: Any = Confirm.Unpair
}
