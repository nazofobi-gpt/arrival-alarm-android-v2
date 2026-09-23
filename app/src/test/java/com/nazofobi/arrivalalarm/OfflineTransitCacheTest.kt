package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTransitCacheTest {
    @Test fun offlineSnapshotStripsRealtimeAndKeepsRoute() {
        val origin = MapPoint(53.08, 8.81, "Bremen")
        val destination = MapPoint(53.10, 8.85, "Ziel")
        val route = RouteOption("r:t", origin, destination, "6", "Flughafen", "10:00", "10:20", 4, 0)
        val departure = Departure("t", "6", "Flughafen", 1000, realtimeEpochSeconds = 1060)
        val cache = OfflineTransitCache()
        cache.put(CachedTransitPlan("trip", listOf(route), listOf(departure), 900))

        val snapshot = cache.snapshot("trip")!!
        assertTrue(snapshot.isOfflineCache)
        assertFalse(snapshot.departures.single().isRealtime)
        assertEquals(1000, snapshot.departures.single().effectiveEpochSeconds)
        assertEquals(listOf(route), cache.routeOptions("trip"))
    }

    @Test fun boundedCacheEvictsOldest() {
        val cache = OfflineTransitCache(maxEntries = 1)
        cache.put(CachedTransitPlan("a", emptyList(), emptyList(), 1))
        cache.put(CachedTransitPlan("b", emptyList(), emptyList(), 2))
        assertTrue(cache.routeOptions("a").isEmpty())
        assertEquals("Offline cache", cache.snapshot("b")!!.sourceLabel)
    }
}
