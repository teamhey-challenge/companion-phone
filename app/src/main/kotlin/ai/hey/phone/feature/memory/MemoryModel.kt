package ai.hey.phone.feature.memory

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.MemoryItem
import ai.hey.phone.data.Speech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId

enum class MemoryKindUi { Fact, Summary }

/** One remembered thing as the screen shows it. [date] is already formatted ("11월 13일"). */
data class MemoryRowUi(val id: String, val kind: MemoryKindUi, val text: String, val date: String)

/** What the list zone shows. */
sealed interface MemoryListUi {
    data object Loading : MemoryListUi
    data object Failed : MemoryListUi
    data object Empty : MemoryListUi
    data class Items(
        val rows: List<MemoryRowUi>,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val busyIds: Set<String> = emptySet(),
        /** "모두 지우기" is running: rows stay but cannot be used. */
        val allBusy: Boolean = false,
    ) : MemoryListUi
}

enum class SpeechUi { Banmal, Jondaetmal }

/** Transient message under the heading; null = none. */
enum class NoticeUi { DeleteFailed, SpeechFailed, UnpairFailed }

/** "2026-11-13T09:00:00Z" -> "11월 13일" in the device's zone; unreadable -> "". */
fun memoryDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).let { "${it.monthValue}월 ${it.dayOfMonth}일" } }.getOrDefault("")

internal fun MemoryItem.toRow() = MemoryRowUi(
    id = id,
    kind = if (kind == "summary") MemoryKindUi.Summary else MemoryKindUi.Fact,
    text = text,
    date = memoryDate(createdAt),
)

/**
 * State and actions of W3. Every server call runs in [scope]; a 401/403 (the car ended the pairing) calls [onUnauthorized]
 * once. The list keeps its rows when a delete fails (notice only), like the car's C6.
 */
class MemoryModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
    private val onUnpaired: () -> Unit,
    private val pageSize: Int = 50,
) {
    var list: MemoryListUi by mutableStateOf(MemoryListUi.Loading)
        private set
    var speech: SpeechUi? by mutableStateOf(null)
        private set
    var speechSaving by mutableStateOf(false)
        private set
    var unpairing by mutableStateOf(false)
        private set
    var notice: NoticeUi? by mutableStateOf(null)
        private set

    private var cursor: String? = null
    private var leaving = false

    fun load() {
        list = MemoryListUi.Loading
        notice = null
        scope.launch {
            guarded(onFail = { list = MemoryListUi.Failed }) {
                val page = api.memory(null, pageSize)
                cursor = page.nextCursor
                list = page.items.map { it.toRow() }.let { if (it.isEmpty()) MemoryListUi.Empty else MemoryListUi.Items(it, hasMore = cursor != null) }
            }
        }
        scope.launch {
            // The settings read only fills the 말투 choice; the list works without it.
            guarded(onFail = {}) { speech = api.settings().speech.toUi() }
        }
    }

    fun loadMore() {
        val cur = list as? MemoryListUi.Items ?: return
        val c = cursor ?: return
        if (cur.loadingMore) return
        list = cur.copy(loadingMore = true)
        scope.launch {
            guarded(onFail = { (list as? MemoryListUi.Items)?.let { list = it.copy(loadingMore = false) } }) {
                val page = api.memory(c, pageSize)
                cursor = page.nextCursor
                val now = list as? MemoryListUi.Items ?: return@guarded
                val known = now.rows.map { it.id }.toSet()
                list = now.copy(rows = now.rows + page.items.filter { it.id !in known }.map { it.toRow() }, hasMore = cursor != null, loadingMore = false)
            }
        }
    }

    fun delete(id: String) {
        val cur = list as? MemoryListUi.Items ?: return
        if (id in cur.busyIds) return
        notice = null
        list = cur.copy(busyIds = cur.busyIds + id)
        scope.launch {
            guarded(onFail = { markIdle(id); notice = NoticeUi.DeleteFailed }) {
                api.deleteMemory(id)
                val now = list as? MemoryListUi.Items ?: return@guarded
                val rows = now.rows.filter { it.id != id }
                list = if (rows.isEmpty() && !now.hasMore) MemoryListUi.Empty else now.copy(rows = rows, busyIds = now.busyIds - id)
            }
        }
    }

    fun deleteAll() {
        val cur = list as? MemoryListUi.Items ?: return
        notice = null
        list = cur.copy(allBusy = true)
        scope.launch {
            guarded(onFail = { (list as? MemoryListUi.Items)?.let { list = it.copy(allBusy = false) }; notice = NoticeUi.DeleteFailed }) {
                api.deleteAllMemory()
                cursor = null
                list = MemoryListUi.Empty
            }
        }
    }

    fun chooseSpeech(next: SpeechUi) {
        if (speechSaving || next == speech) return
        val before = speech
        notice = null
        speech = next
        speechSaving = true
        scope.launch {
            guarded(onFail = { speech = before; notice = NoticeUi.SpeechFailed }) {
                speech = api.setSpeech(next.wire()).speech.toUi()
            }
            speechSaving = false
        }
    }

    fun unpair() {
        if (unpairing) return
        unpairing = true
        notice = null
        scope.launch {
            try {
                api.unpair()
                leaving = true
                onUnpaired()
            } catch (_: IOException) {
                // ApiException is an IOException: the token stays, the user can try again.
                notice = NoticeUi.UnpairFailed
            }
            unpairing = false
        }
    }

    private fun markIdle(id: String) {
        (list as? MemoryListUi.Items)?.let { list = it.copy(busyIds = it.busyIds - id) }
    }

    private suspend fun guarded(onFail: () -> Unit, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: ApiException) {
            if (e.unauthorized) { if (!leaving) { leaving = true; onUnauthorized() } } else onFail()
        } catch (_: IOException) {
            onFail()
        }
    }
}

private fun String.toUi() = if (this == Speech.JONDAETMAL) SpeechUi.Jondaetmal else SpeechUi.Banmal

private fun SpeechUi.wire() = if (this == SpeechUi.Jondaetmal) Speech.JONDAETMAL else Speech.BANMAL
