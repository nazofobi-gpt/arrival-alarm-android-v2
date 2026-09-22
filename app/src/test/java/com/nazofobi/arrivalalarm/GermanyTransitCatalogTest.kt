package com.nazofobi.arrivalalarm

import org.junit.Assert.*
import org.junit.Test

class GermanyTransitCatalogTest {
    private val catalog = GermanyTransitCatalog()

    @Test fun resolvesThreeMateriallyDifferentRegions() {
        assertTrue(catalog.searchStops("Bremen").any { it.name == "Bremen Hbf" })
        assertEquals("Berlin Hauptbahnhof", catalog.searchStops("Berlin").single().name)
        assertEquals("München Hauptbahnhof", catalog.searchStops("München").single().name)
        assertEquals("S5", catalog.searchRoutes("S5").single().shortName)
        assertEquals("S8", catalog.searchRoutes("Herrsching").single().shortName)
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
        assertEquals("Marienplatz", stale.searchStops("Marien").single().name)
        assertEquals(1, stale.tripsForRoute("r-by-s8").size)
    }
}
