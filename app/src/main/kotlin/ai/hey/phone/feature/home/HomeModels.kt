package ai.hey.phone.feature.home

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.Diary
import ai.hey.phone.data.guardedCall
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
    /** D-52: memory saving is off, so no diary is written; the card shows the guidance. */
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
                // A failed settings read must not hide the day: only an explicit "off" replaces the diary.
                val s = try { api.settings() } catch (e: ApiException) { if (e.unauthorized) throw e else null } catch (_: java.io.IOException) { null }
                if (s != null) name = s.displayName
                ui = if (s != null && !s.memoryEnabled) TodayUi.MemoryOff else api.diary(today()).toToday()
            }
        }
    }
}

enum class SettingsNotice { NameSaved, Failed }

/** 설정 tab: 이름. */
class SettingsModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
) {
    var name by mutableStateOf("")
    var loaded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var notice: SettingsNotice? by mutableStateOf(null)
        private set

    fun load() {
        scope.launch {
            guardedCall(onUnauthorized, { loaded = true }) {
                val s = api.settings()
                name = s.displayName.orEmpty()
                loaded = true
            }
        }
    }

    fun saveName() {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME || saving) { notice = SettingsNotice.Failed; return }
        saving = true
        notice = null
        scope.launch {
            guardedCall(onUnauthorized, { notice = SettingsNotice.Failed }) {
                api.patchSettings(displayName = n)
                notice = SettingsNotice.NameSaved
            }
            saving = false
        }
    }

    companion object { const val MAX_NAME = 20 }
}

enum class SignupError { Name, Consent, Rate, Generic }

/**
 * Phone-only sign-up: 이름 -> POST /v1/phones/register (token stored by the client), then the consents the talk needs
 * ("record" + "external_ai", plus "memory" when ticked). The register is the point of no return; a consent failure after it
 * is not an error here: the 대화 tab asks for the consent again.
 */
class SignupModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onDone: () -> Unit,
) {
    var name by mutableStateOf("")
    var consent by mutableStateOf(false)
    var memory by mutableStateOf(false)
    var saving by mutableStateOf(false)
        private set
    var error: SignupError? by mutableStateOf(null)
        private set

    fun start() {
        if (saving) return
        val n = name.trim()
        error = when {
            n.isEmpty() || n.length > SettingsModel.MAX_NAME -> SignupError.Name
            !consent -> SignupError.Consent
            else -> null
        }
        if (error != null) return
        saving = true
        scope.launch {
            try {
                api.register(n)
            } catch (e: ApiException) {
                error = if (e.code == 429) SignupError.Rate else if (e.code == 400) SignupError.Name else SignupError.Generic
                saving = false
                return@launch
            } catch (_: java.io.IOException) {
                error = SignupError.Generic
                saving = false
                return@launch
            }
            runCatching {
                api.postConsent("record", true)
                api.postConsent("external_ai", true)
                if (memory) api.postConsent("memory", true)
            }
            saving = false
            onDone()
        }
    }
}
