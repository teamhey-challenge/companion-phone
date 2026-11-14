package ai.hey.phone.feature.trips

import ai.hey.phone.data.BadgeItem
import ai.hey.phone.data.SidoRegions
import ai.hey.phone.data.TripItem
import ai.hey.phone.data.TripStats
import ai.hey.phone.data.TripsResponse
import ai.hey.phone.data.UnlockedRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripsModelTest {
    @Test fun disabledWins() {
        assertEquals(TripsUi.Disabled, TripsResponse(enabled = false).toUi())
    }

    @Test fun noTripsIsEmpty() {
        assertEquals(TripsUi.Empty, TripsResponse(enabled = true).toUi())
    }

    @Test fun contentMapsRegionsAndBadges() {
        val r = TripsResponse(
            stats = TripStats(tripCount = 1, totalKm = null, regionCount = 1),
            trips = listOf(TripItem("a", "2026-11-14T08:01:00.000Z")),
            badges = listOf(BadgeItem("b", "배지", earned = false)),
            regions = listOf(SidoRegions("서울특별시", listOf(UnlockedRegion("강남구", demo = true)))),
        )
        val c = (r.toUi() as TripsUi.Content).data
        assertEquals(null, c.totalKm)
        assertEquals(false, c.badges[0].earned)
        assertEquals(listOf("강남구"), c.sidos[0].tiles.map { it.name })
        assertTrue(c.sidos[0].tiles[0].demo)
    }
}
