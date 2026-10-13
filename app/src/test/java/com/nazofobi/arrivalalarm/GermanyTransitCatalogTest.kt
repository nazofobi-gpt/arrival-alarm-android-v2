package com.nazofobi.arrivalalarm

import org.junit.Assert.*
import org.junit.Test

class GermanyTransitCatalogTest {
    private val catalog = GermanyTransitCatalog(TestGermanyTransitFixtures.snapshot)

    @Test fun resolvesThreeMateriallyDifferentRegions() {
        assertTrue(catalog.searchStops("Bremen").any { it.name == "Bremen Hbf" })
        assertTrue(catalog.searchStops("Berlin").any { it.name == "Berlin Hauptbahnhof" })
        assertTrue(catalog.searchStops("München").any { it.name == "München Hauptbahnhof" })
        assertTrue(catalog.searchRoutes("S5").any { it.shortName == "S5" })
        assertTrue(catalog.searchRoutes("Herrsching").any { it.shortName == "S8" })
        assertEquals(1, catalog.tripsForRoute("r-hb-6").size)
    }

    @Test fun nearestStopIsGeospatialNotHardCoded() {
        val berlin = catalog.nearestStops(52.5250, 13.3695, 2)
        assertEquals("Berlin Hauptbahnhof", berlin.first().stop.name)
        assertTrue(berlin.first().distanceMeters < berlin.last().distanceMeters)
    }


    @Test fun invalidLocationAndLimitsFailClosedWithoutThrowing() {
        assertTrue(catalog.nearestStops(Double.NaN, 8.2).isEmpty())
        assertTrue(catalog.nearestStops(52.0, Double.POSITIVE_INFINITY).isEmpty())
        assertTrue(catalog.nearestStops(90.1, 8.2).isEmpty())
        assertTrue(catalog.nearestStops(52.0, -180.1).isEmpty())
        assertTrue(catalog.nearestStops(52.0, 8.2, -1).isEmpty())
        assertTrue(catalog.nearestStops(52.0, 8.2, 0).isEmpty())
        assertTrue(catalog.searchStops("Berlin", -1).isEmpty())
        assertTrue(catalog.searchRoutes("S5", -1).isEmpty())
        assertTrue(catalog.searchTrips("train", -1).isEmpty())
    }

    @Test fun malformedFeedStopCoordinatesCannotCrashNearbyResults() {
        val valid = CatalogStop("valid", "gtfs", "Lohne", 52.665, 8.237)
        val invalid = CatalogStop("nan", "gtfs", "Bad latitude", Double.NaN, 8.237)
        val outside = CatalogStop("outside", "gtfs", "Bad longitude", 52.665, 181.0)
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(stops = listOf(invalid, outside, valid))
        val result = GermanyTransitCatalog(snapshot).nearestStops(52.664, 8.237)
        assertEquals(listOf("valid"), result.map { it.stop.id })
    }

    @Test fun boundedTripSearchPreservesFirstRouteForDuplicateIds() {
        val primary = CatalogRoute("duplicate", "gtfs", "First", "Center", emptyList())
        val laterDuplicate = primary.copy(shortName = "Second")
        val trips = (1..128).map { index ->
            CatalogTrip("trip-$index", "gtfs", "duplicate", "08:00")
        }
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            routes = listOf(primary, laterDuplicate),
            trips = trips,
        )
        val localCatalog = GermanyTransitCatalog(snapshot)
        val result = localCatalog.searchTrips("trip-", limit = 3)
        assertEquals(listOf("trip-1", "trip-2", "trip-3"), result.map { it.first.id })
        assertTrue(result.all { it.second.shortName == "First" })
        assertEquals(128, localCatalog.searchTrips("trip-", limit = 200).size)
    }

    @Test fun registryExposesCoverageFreshnessAndRealtimeCapability() {
        val delfi = requireNotNull(catalog.provider("delfi"))
        val vbb = requireNotNull(catalog.provider("vbb"))
        val mvv = requireNotNull(catalog.provider("mvv"))
        assertEquals("Germany", delfi.coverage)
        assertTrue(delfi.realtimeCapability)
        assertTrue(vbb.realtimeCapability)
        assertFalse(mvv.realtimeCapability)
        assertTrue(mvv.license.contains("CC BY"))
        assertFalse(catalog.isStale())
    }

    @Test fun staleSnapshotRemainsSearchableStaticFallback() {
        val stale = GermanyTransitCatalog(TestGermanyTransitFixtures.snapshot.copy(stale = true))
        assertTrue(stale.isStale())
        assertTrue(stale.searchStops("Marien").any { it.name == "Marienplatz" })
        assertEquals(1, stale.tripsForRoute("r-by-s8").size)
    }
}
