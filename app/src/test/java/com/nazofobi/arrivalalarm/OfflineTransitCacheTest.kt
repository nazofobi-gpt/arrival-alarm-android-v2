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


    @Test fun backingStoreIsLoadedAndPublishedOnMutation() {
        val origin = MapPoint(52.665, 8.237, "Lohne")
        val destination = MapPoint(53.083, 8.813, "Bremen")
        val route = RouteOption("r1", origin, destination, "RE 9", "Bremen", "10:00", "10:45", 0, 0)
        val seeded = CachedTransitPlan("seed", listOf(route), emptyList(), 10L)
        var saved = emptyList<CachedTransitPlan>()
        val backing = object : TransitCacheBackingStore {
            override fun load(): List<CachedTransitPlan> = listOf(seeded)
            override fun save(plans: List<CachedTransitPlan>) {
                saved = plans
            }
        }

        val cache = OfflineTransitCache(maxEntries = 2, backingStore = backing)
        assertEquals(listOf("r1"), cache.routeOptions("seed").map { it.id })

        cache.put(CachedTransitPlan("next", listOf(route.copy(id = "r2")), emptyList(), 20L))
        assertEquals(listOf("seed", "next"), saved.map { it.key })
    }

    @Test fun boundedCacheEvictsOldest() {
        val cache = OfflineTransitCache(maxEntries = 1)
        cache.put(CachedTransitPlan("a", emptyList(), emptyList(), 1))
        cache.put(CachedTransitPlan("b", emptyList(), emptyList(), 2))
        assertTrue(cache.routeOptions("a").isEmpty())
        assertEquals("Offline cache", cache.snapshot("b")!!.sourceLabel)
    }
}
