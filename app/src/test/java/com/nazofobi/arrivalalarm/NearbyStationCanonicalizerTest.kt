package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyStationCanonicalizerTest {
    @Test
    fun duplicatePlatformsCollapseToParentStation() {
        val station = CatalogStop("station", "gtfs", "Lohne Bahnhof", 52.665, 8.237)
        val first = CatalogStop("p1", "gtfs", "Lohne Bahnhof", 52.665, 8.237, "station")
        val second = CatalogStop("p2", "gtfs", "Lohne Bahnhof", 52.665, 8.237, "station")
        val result = NearbyStationCanonicalizer.canonicalize(
            52.664, 8.237,
            listOf(NearbyStop(first, 1), NearbyStop(station, 1), NearbyStop(second, 1)),
            5,
        )
        assertEquals(1, result.size)
        assertEquals("station", result.single().stop.id)
        assertEquals(listOf("p1", "p2"), result.single().childStops.map { it.id })
    }

    @Test
    fun favoriteIdentityUsesParentStationAndIsStableAcrossInputOrder() {
        val first = CatalogStop("lohne-platform-1", "gtfs", "Lohne Bahnhof", 52.66501, 8.23701, "lohne-station")
        val second = CatalogStop("lohne-platform-2", "gtfs", "Lohne Bahnhof", 52.66502, 8.23702, "lohne-station")
        val forward = NearbyStationCanonicalizer.canonicalize(
            52.6645,
            8.237,
            listOf(NearbyStop(first, 1), NearbyStop(second, 2)),
            5,
        )
        val reversed = NearbyStationCanonicalizer.canonicalize(
            52.6645,
            8.237,
            listOf(NearbyStop(second, 2), NearbyStop(first, 1)),
            5,
        )

        assertEquals(1, forward.size)
        assertEquals(1, reversed.size)
        assertEquals("lohne-station", forward.single().stop.id)
        assertEquals(forward.single().stop.id, reversed.single().stop.id)
        assertEquals(forward.single().distanceMeters, reversed.single().distanceMeters)
        assertEquals(forward.single().bearingDegrees, reversed.single().bearingDegrees)
        assertEquals(
            listOf("lohne-platform-1", "lohne-platform-2"),
            forward.single().childStops.map { it.id },
        )
        assertEquals(
            forward.single().childStops.map { it.id },
            reversed.single().childStops.map { it.id },
        )
    }
}
