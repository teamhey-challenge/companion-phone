package ai.hey.phone.feature.home

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.Diary
import ai.hey.phone.data.guardedCall
import ai.hey.phone.data.memoryKnownOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the 오늘 card shows for one day. */
sealed interface TodayUi {
    data object Loading : TodayUi
    data object Failed : TodayUi
    /** Nothing yet today. */
    data object Quiet : TodayUi
    /** memory saving is off, so no diary is written; the card shows the guidance. */
    data object MemoryOff : TodayUi
    data class Day(val tripLine: String?, val entries: List<TodayEntry>, val summary: String?) : TodayUi
}

data class TodayEntry(val time: String, val text: String)

/** "2026-11-15T14:03:00+09:00" -> "14:03"; unreadable -> "". */
fun entryTime(iso: String?): String =
    runCatching { java.time.OffsetDateTime.parse(iso!!).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) } }.getOrDefault("")

/** The newest `trip_done` entry is the day's line; the rest are the list (trip_done itself is not repeated there). */
fun Diary.toToday(): TodayUi {
    val trip = entries.filter { it.kind == "trip_done" }.maxByOrNull { it.at.orEmpty() }?.text
    val rest = entries.filter { it.kind != "trip_done" }.sortedBy { it.at.orEmpty() }.map { TodayEntry(entryTime(it.at), it.text) }
    val sum = summary?.takeIf { it.isNotBlank() }
    return if (trip == null && rest.isEmpty() && sum == null) TodayUi.Quiet else TodayUi.Day(trip, rest, sum)
}

class TodayModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val today: () -> String,
    private val onUnauthorized: () -> Unit,
) {
    var ui: TodayUi by mutableStateOf(TodayUi.Loading)
        private set
    var name: String? by mutableStateOf(null)
        private set

    fun load() {
        scope.launch {
            guardedCall(onUnauthorized, { if (ui !is TodayUi.Day) ui = TodayUi.Failed }) {
                // A failed settings/consents read must not hide the day: only an explicit "off" replaces the diary.
                val s = try { api.settings() } catch (e: ApiException) { if (e.unauthorized) throw e else null } catch (_: java.io.IOException) { null }
                val c = try { api.consents() } catch (e: ApiException) { if (e.unauthorized) throw e else null } catch (_: java.io.IOException) { null }
                if (s != null) name = s.nickname
                ui = if (memoryKnownOff(s, c)) TodayUi.MemoryOff else api.diary(today()).toToday()
            }
        }
    }
}

enum class SettingsNotice { NameSaved, Failed, UnlinkFailed, MemoryFailed }

/** 설정 tab: 이름, 기억 저장, 차 연결. */
class SettingsModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
    private val onUnlinked: () -> Unit = {},
) {
    var name by mutableStateOf("")
    var loaded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var notice: SettingsNotice? by mutableStateOf(null)
        private set

    var unlinking by mutableStateOf(false)
        private set

    /** Memory saving consent as last read; null until read. */
    var memoryOn: Boolean? by mutableStateOf(null)
        private set

    fun load() {
        scope.launch {
            guardedCall(onUnauthorized, { loaded = true }) {
                val s = api.settings()
                name = s.nickname.orEmpty()
                loaded = true
            }
        }
        scope.launch { guardedCall(onUnauthorized, {}) { memoryOn = api.consents().answer("memory") == true } }
    }

    fun saveName() {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME || n.all { it.isDigit() } || saving) { notice = SettingsNotice.Failed; return }
        saving = true
        notice = null
        scope.launch {
            guardedCall(onUnauthorized, { notice = SettingsNotice.Failed }) {
                api.patchSettings(name = n)
                notice = SettingsNotice.NameSaved
            }
            saving = false
        }
    }

    /** 연결 해제: the server revokes this phone's token and keeps the car's memory (6.6); the app then goes back to the start screen. */
    fun unlink() {
        if (unlinking) return
        unlinking = true
        notice = null
        scope.launch {
            try {
                api.unpair()
                onUnlinked()
            } catch (_: java.io.IOException) {
                notice = SettingsNotice.UnlinkFailed
            }
            unlinking = false
        }
    }

    /** Turning memory saving on posts the consent; off is the withdrawal (the sheet "기억·일기·친구 말이 모두 지워져요" comes first, 6.7). */
    fun setMemory(on: Boolean) {
        notice = null
        scope.launch {
            guardedCall(onUnauthorized, { notice = SettingsNotice.MemoryFailed }) {
                api.postConsent("memory", on)
                memoryOn = on
            }
        }
    }

    companion object { const val MAX_NAME = 10 }
}
