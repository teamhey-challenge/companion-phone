package ai.hey.phone.feature.trips

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.BadgeItem
import ai.hey.phone.data.SidoRegions
import ai.hey.phone.data.TripItem
import ai.hey.phone.data.TripTone
import ai.hey.phone.data.TripsResponse
import ai.hey.phone.feature.memory.memoryDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/** One trip row as the screen shows it. [date] is formatted ("11월 14일"); null fields are not known to the car. */
data class TripRowUi(
    val id: String,
    val date: String,
    val destination: String?,
    val region: String?,
    val durationMin: Int?,
    val distanceKm: Double?,
)

/** A locked badge is shown muted with its description only: no progress, no remaining count. */
data class BadgeUi(
    val id: String,
    val title: String,
    val description: String,
    val playful: Boolean,
    val earned: Boolean,
)

data class RegionTileUi(val name: String, val demo: Boolean = false)

/** One visited 시·도 with the 동네 the car has been to. Nothing about what is left. */
data class SidoUi(val name: String, val tiles: List<RegionTileUi>)

enum class ToneUi { Serious, Playful }

data class TripsContentUi(
    val tripCount: Int,
    /** null = no trip carried a distance ("거리 미확인"). Informational only. */
    val totalKm: Double?,
    val regionCount: Int,
    val badges: List<BadgeUi>,
    val sidos: List<SidoUi>,
    val trips: List<TripRowUi>,
    val busyIds: Set<String> = emptySet(),
    /** "기록 모두 지우기" is running. */
    val allBusy: Boolean = false,
)

/** What the screen shows. */
sealed interface TripsUi {
    data object Loading : TripsUi
    data object Failed : TripsUi
    /** The server has no memory consent: it records nothing. */
    data object Disabled : TripsUi
    /** Consent is on but there is no trip yet. */
    data object Empty : TripsUi
    data class Content(val data: TripsContentUi) : TripsUi
}

enum class TripsNoticeUi { DeleteFailed, ToneFailed }

internal fun TripItem.toRow() = TripRowUi(id, memoryDate(startedAt), destination, region, durationMin, distanceKm)

internal fun BadgeItem.toUi() = BadgeUi(id, title, description, playful, earned)

internal fun SidoRegions.toUi() = SidoUi(sido, unlocked.map { RegionTileUi(it.name, it.demo) })

internal fun TripsResponse.toUi(): TripsUi = when {
    !enabled -> TripsUi.Disabled
    trips.isEmpty() -> TripsUi.Empty
    else -> TripsUi.Content(
        TripsContentUi(
            tripCount = stats.tripCount,
            totalKm = stats.totalKm,
            regionCount = stats.regionCount,
            badges = badges.map { it.toUi() },
            sidos = regions.map { it.toUi() },
            trips = trips.map { it.toRow() },
        ),
    )
}

private fun ToneUi.wire() = if (this == ToneUi.Playful) TripTone.PLAYFUL else TripTone.SERIOUS

/** State and actions of the "함께 다닌 길" screen. A 401/403 calls [onUnauthorized] once. */
class TripsModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
) {
    var state: TripsUi by mutableStateOf(TripsUi.Loading)
        private set
    var notice: TripsNoticeUi? by mutableStateOf(null)
        private set
    var tone: ToneUi by mutableStateOf(ToneUi.Serious)
        private set

    private var leaving = false

    fun load() {
        state = TripsUi.Loading
        notice = null
        scope.launch { guarded(onFail = { state = TripsUi.Failed }) { state = api.trips(tone.wire()).toUi() } }
    }

    /** Re-fetches with `?tone=`; the list stays on screen meanwhile, and the old tone returns if the call fails. */
    fun chooseTone(next: ToneUi) {
        if (next == tone) return
        val before = tone
        notice = null
        tone = next
        scope.launch {
            guarded(onFail = { tone = before; notice = TripsNoticeUi.ToneFailed }) { state = api.trips(next.wire()).toUi() }
        }
    }

    fun delete(id: String) {
        val cur = (state as? TripsUi.Content)?.data ?: return
        if (id in cur.busyIds) return
        notice = null
        state = TripsUi.Content(cur.copy(busyIds = cur.busyIds + id))
        scope.launch {
            guarded(onFail = { idle(id); notice = TripsNoticeUi.DeleteFailed }) {
                api.deleteTrip(id)
                // Badges and regions derive from the trips: ask the server again, fall back to dropping the row.
                try {
                    state = api.trips(tone.wire()).toUi()
                } catch (_: IOException) {
                    val now = (state as? TripsUi.Content)?.data ?: return@guarded
                    val rows = now.trips.filter { it.id != id }
                    state = if (rows.isEmpty()) TripsUi.Empty else TripsUi.Content(now.copy(trips = rows, busyIds = now.busyIds - id))
                }
            }
        }
    }

    fun deleteAll() {
        val cur = (state as? TripsUi.Content)?.data ?: return
        notice = null
        state = TripsUi.Content(cur.copy(allBusy = true))
        scope.launch {
            guarded(onFail = { (state as? TripsUi.Content)?.let { state = TripsUi.Content(it.data.copy(allBusy = false)) }; notice = TripsNoticeUi.DeleteFailed }) {
                api.deleteTrips()
                state = TripsUi.Empty
            }
        }
    }

    private fun idle(id: String) {
        (state as? TripsUi.Content)?.let { state = TripsUi.Content(it.data.copy(busyIds = it.data.busyIds - id)) }
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
