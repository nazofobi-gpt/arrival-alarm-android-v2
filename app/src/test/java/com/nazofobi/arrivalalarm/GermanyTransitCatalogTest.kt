package com.nazofobi.arrivalalarm

import org.junit.Assert.*
import org.junit.Test

class GermanyTransitCatalogTest {
    private val catalog = GermanyTransitCatalog()

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
        val stale = GermanyTransitCatalog(GermanyTransitFixtures.snapshot.copy(stale = true))
        assertTrue(stale.isStale())
        assertTrue(stale.searchStops("Marien").any { it.name == "Marienplatz" })
        assertEquals(1, stale.tripsForRoute("r-by-s8").size)
    }
}
