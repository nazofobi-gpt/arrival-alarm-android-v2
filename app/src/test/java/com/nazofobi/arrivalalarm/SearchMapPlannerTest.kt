package com.nazofobi.arrivalalarm

import org.junit.Assert.*
import org.junit.Test

class SearchMapPlannerTest {
    private val planner = SearchMapPlanner(GermanyTransitCatalog(TestGermanyTransitFixtures.snapshot))

    @Test fun searchesStopsRoutesAndTripsAcrossRegions() {
        assertTrue(planner.search("Berlin").any { it.label.contains("Berlin") })
        assertTrue(planner.search("S5").any { it.label.contains("S5") })
        assertTrue(planner.search("S8").any { it.label.contains("S8") })
        assertTrue(planner.search("Bremen").any { it.label.contains("Bremen") })
    }

    @Test fun nearestStopsAreRankedAndExposeBearing() {
        val near = planner.nearby(MapPoint(52.5250, 13.3695, "current"), 2)
        assertEquals("Berlin Hauptbahnhof", near.first().stop.name)
        assertTrue(near[0].distanceMeters <= near[1].distanceMeters)
        assertTrue(near.first().bearingDegrees in 0..359)
    }

    @Test fun mapPointAndMarkersWorkWithoutLocationPermission() {
        val manual = planner.arbitraryPoint(48.1402, 11.5586, "manual")
        val markers = planner.mapMarkers(manual, 2)
        assertEquals("München Hauptbahnhof", markers.first().label)
        assertEquals("manual", manual.label)
    }

    @Test fun routeOptionsExposeLineWalkingAndAlternatives() {
        val origin = MapPoint(53.0834, 8.8137, "Bremen Hbf")
        val destination = MapPoint(53.0531, 8.7866, "Flughafen Bremen")
        val options = planner.routeOptions(origin, destination)
        assertTrue(options.isNotEmpty())
        assertEquals("6", options.first().line)
        assertTrue(options.first().walkingMinutes >= 0)
        assertEquals(0, options.first().transfers)
    }

    @Test fun journeyOptionsUseProviderScopedTripsWhenRouteIdsOverlap() {
        val originStop = CatalogStop("berlin-a", "vbb", "Berlin Test A", 52.5200, 13.4050)
        val destinationStop = CatalogStop("berlin-b", "vbb", "Berlin Test B", 52.5210, 13.4080)
        val munichStop = CatalogStop("munich-a", "mvv", "München Test A", 48.1400, 11.5800)
        val berlinRoute = CatalogRoute(
            "shared-id", "vbb", "RE 1", "Berlin Test B",
            listOf(originStop.id, destinationStop.id),
        )
        val munichRoute = CatalogRoute(
            "shared-id", "mvv", "RE 1", "München Test A",
            listOf(munichStop.id, "munich-b"),
        )
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(originStop, destinationStop, munichStop),
            routes = listOf(berlinRoute, munichRoute),
            trips = listOf(
                CatalogTrip("berlin-trip", "vbb", "shared-id", "08:00"),
                CatalogTrip("munich-trip", "mvv", "shared-id", "09:00"),
            ),
        )
        val planner = SearchMapPlanner(GermanyTransitCatalog(snapshot))
        val journeys = planner.routeOptions(
            MapPoint(originStop.latitude, originStop.longitude, "Berlin Test A"),
            MapPoint(destinationStop.latitude, destinationStop.longitude, "Berlin Test B"),
        )

        assertEquals(1, journeys.size)
        assertEquals("shared-id:berlin-trip", journeys.single().id)
        assertEquals("08:00", journeys.single().departure)
        assertFalse(journeys.single().id.startsWith("shared-id:static-"))
        assertFalse(journeys.any { it.id.contains("munich-trip") })
    }

    @Test fun geocoderIsProviderSwappable() {
        val custom = SearchMapPlanner(GermanyTransitCatalog(TestGermanyTransitFixtures.snapshot), object : GeocodeProvider {
            override fun search(query: String) = listOf(MapPoint(50.0, 8.0, "POI:$query"))
        })
        assertEquals("POI:Museum", custom.addressOrPoi("Museum").single().label)
    }
}
