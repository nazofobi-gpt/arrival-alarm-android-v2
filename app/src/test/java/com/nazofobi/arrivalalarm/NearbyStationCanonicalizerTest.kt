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
    }
}
