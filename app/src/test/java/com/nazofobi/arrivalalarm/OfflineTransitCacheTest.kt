package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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


    @Test fun realtimeRouteDeltasAreRemovedBeforeOfflinePersistence() {
        val origin = MapPoint(52.665, 8.237, "Lohne")
        val destination = MapPoint(52.700, 8.400, "Achim")
        val route = RouteOption(
            id = "rt",
            origin = origin,
            destination = destination,
            line = "RE 1",
            direction = "Achim",
            departure = "08:00",
            arrival = "08:30",
            walkingMinutes = 0,
            transfers = 0,
            tripIds = listOf("T1"),
            stops = listOf(
                RouteStop(
                    id = "A",
                    name = "Lohne",
                    departure = "2026-10-04T08:03:00+02:00",
                    plannedDeparture = "2026-10-04T08:00:00+02:00",
                    platform = "5",
                    plannedPlatform = "1",
                ),
                RouteStop(
                    id = "B",
                    name = "Achim",
                    arrival = "2026-10-04T08:33:00+02:00",
                    plannedArrival = "2026-10-04T08:30:00+02:00",
                    platform = "7",
                    plannedPlatform = "2",
                ),
            ),
            realtime = RouteRealtimeInfo(
                state = RouteRealtimeState.FRESH_MATCHED,
                sourceLabel = "GTFS-RT",
                updatedAtEpochSeconds = 100L,
                ageSeconds = 5L,
                matchedTripIds = listOf("T1"),
            ),
        )
        val cache = OfflineTransitCache()
        cache.put(CachedTransitPlan("rt", listOf(route), emptyList(), 100L))

        val restored = cache.routeOptions("rt").single()
        assertNull(restored.realtime)
        assertNull(restored.stops.first().departure)
        assertNull(restored.stops.first().platform)
        assertEquals("2026-10-04T08:00:00+02:00", restored.stops.first().plannedDeparture)
        assertEquals("1", restored.stops.first().plannedPlatform)
        assertNull(restored.stops.last().arrival)
        assertNull(restored.stops.last().platform)
        assertEquals("2026-10-04T08:30:00+02:00", restored.stops.last().plannedArrival)
        assertEquals("2", restored.stops.last().plannedPlatform)
    }

    @Test fun restoredLegacyCacheCannotExposeRealtimeCancellationOrPlatform() {
        val origin = MapPoint(52.665, 8.237, "Lohne")
        val destination = MapPoint(53.083, 8.813, "Bremen")
        val route = RouteOption(
            id = "legacy-route",
            origin = origin,
            destination = destination,
            line = "RE 9",
            direction = "Bremen",
            departure = "10:00",
            arrival = "10:45",
            walkingMinutes = 0,
            transfers = 0,
            stops = listOf(
                RouteStop(
                    id = "S1",
                    name = "Lohne",
                    departure = "2026-10-11T10:09:00+02:00",
                    plannedDeparture = "2026-10-11T10:00:00+02:00",
                    platform = "7",
                    plannedPlatform = "2",
                ),
            ),
            // SharedPreferences JSON historically omitted RouteRealtimeInfo
            // while retaining stop-level actual values and planned baselines.
            realtime = null,
        )
        val departure = Departure(
            tripId = "T1",
            line = "RE 9",
            direction = "Bremen",
            scheduledEpochSeconds = 1000L,
            realtimeEpochSeconds = 1500L,
            cancelled = true,
            platform = "7",
            realtimeTripStartDate = "20261011",
            realtimeTripStartTime = "10:00:00",
        )
        val legacyPlan = CachedTransitPlan("legacy", listOf(route), listOf(departure), 800L)
        val backing = object : TransitCacheBackingStore {
            override fun load() = listOf(legacyPlan)
            override fun save(plans: List<CachedTransitPlan>) = Unit
        }

        val cache = OfflineTransitCache(backingStore = backing)
        val stored = cache.get("legacy")!!
        val restoredRoute = stored.routeOptions.single()
        val restoredStop = restoredRoute.stops.single()
        val restoredDeparture = stored.departures.single()

        assertNull(restoredRoute.realtime)
        assertNull(restoredStop.departure)
        assertNull(restoredStop.platform)
        assertEquals("2026-10-11T10:00:00+02:00", restoredStop.plannedDeparture)
        assertEquals("2", restoredStop.plannedPlatform)
        assertNull(restoredDeparture.realtimeEpochSeconds)
        assertFalse(restoredDeparture.cancelled)
        assertNull(restoredDeparture.platform)
        assertNull(restoredDeparture.realtimeTripStartDate)
        assertNull(restoredDeparture.realtimeTripStartTime)
        assertEquals(1000L, restoredDeparture.effectiveEpochSeconds)
        assertTrue(cache.snapshot("legacy")!!.isOfflineCache)
        assertFalse(cache.snapshot("legacy")!!.departures.single().cancelled)
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
