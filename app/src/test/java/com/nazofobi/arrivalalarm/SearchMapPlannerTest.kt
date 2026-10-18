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

    @Test fun nonPositiveSearchAndNearbyLimitsReturnEmptyWithoutThrowing() {
        val center = MapPoint(52.5250, 13.3695, "Berlin")
        for (limit in listOf(0, -1, Int.MIN_VALUE)) {
            assertTrue("search limit=$limit", planner.search("Berlin", limit).isEmpty())
            assertTrue("nearby limit=$limit", planner.nearby(center, limit).isEmpty())
            assertTrue("markers limit=$limit", planner.mapMarkers(center, limit).isEmpty())
        }
        assertTrue(planner.search("Berlin", 1).isNotEmpty())
        assertTrue(planner.nearby(center, 1).isNotEmpty())
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

    @Test fun providerLocalStopIdCollisionsNeverSelectOtherRegionsRoutes() {
        val berlinFrom = CatalogStop("shared-from", "vbb", "Berlin Origin", 52.5200, 13.4050)
        val berlinTo = CatalogStop("shared-to", "vbb", "Berlin Destination", 52.5220, 13.4090)
        val munichFrom = CatalogStop("shared-from", "mvv", "Munich Origin", 48.1400, 11.5800)
        val munichTo = CatalogStop("shared-to", "mvv", "Munich Destination", 48.1420, 11.5840)
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(berlinFrom, berlinTo, munichFrom, munichTo),
            routes = listOf(
                CatalogRoute("common", "mvv", "U", "Munich Destination", listOf("shared-from", "shared-to")),
                CatalogRoute("common", "vbb", "S", "Berlin Destination", listOf("shared-from", "shared-to")),
            ),
            trips = listOf(
                CatalogTrip("munich-trip", "mvv", "common", "09:00"),
                CatalogTrip("berlin-trip", "vbb", "common", "08:00"),
            ),
        )
        val scoped = SearchMapPlanner(GermanyTransitCatalog(snapshot))
        val berlin = scoped.routeOptions(
            MapPoint(berlinFrom.latitude, berlinFrom.longitude, "Berlin Origin"),
            MapPoint(berlinTo.latitude, berlinTo.longitude, "Berlin Destination"),
        )
        val munich = scoped.routeOptions(
            MapPoint(munichFrom.latitude, munichFrom.longitude, "Munich Origin"),
            MapPoint(munichTo.latitude, munichTo.longitude, "Munich Destination"),
        )
        assertEquals(listOf("common:berlin-trip"), berlin.map { it.id })
        assertEquals(listOf("common:munich-trip"), munich.map { it.id })
        assertEquals("S", berlin.single().line)
        assertEquals("U", munich.single().line)
    }

    @Test fun crossProviderEndpointsDoNotInventDirectRouteFromMatchingIds() {
        val berlinFrom = CatalogStop("from", "vbb", "Berlin Origin", 52.5200, 13.4050)
        val munichTo = CatalogStop("to", "mvv", "Munich Destination", 48.1400, 11.5800)
        val misleadingRoute = CatalogRoute(
            "vbb-phantom", "vbb", "RE", "Munich Destination", listOf("from", "to"),
        )
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(berlinFrom, munichTo),
            routes = listOf(misleadingRoute),
            trips = listOf(CatalogTrip("phantom-trip", "vbb", "vbb-phantom", "12:00")),
        )
        val scoped = SearchMapPlanner(GermanyTransitCatalog(snapshot))
        assertTrue(
            scoped.routeOptions(
                MapPoint(berlinFrom.latitude, berlinFrom.longitude, "Berlin Origin"),
                MapPoint(munichTo.latitude, munichTo.longitude, "Munich Destination"),
            ).isEmpty(),
        )
    }

    @Test fun loopRouteAcceptsLaterAlightingOccurrenceButNotEarlierOnly() {
        val origin = CatalogStop("origin", "vbb", "Loop Origin", 52.5200, 13.4050)
        val destination = CatalogStop("destination", "vbb", "Loop Destination", 52.5300, 13.4150)
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(origin, destination),
            routes = listOf(
                CatalogRoute("loop", "vbb", "L", "Loop", listOf("destination", "origin", "middle", "destination")),
                CatalogRoute("past-only", "vbb", "L", "Already passed", listOf("destination", "origin", "middle")),
            ),
            trips = listOf(CatalogTrip("loop-trip", "vbb", "loop", "08:00")),
        )
        val options = SearchMapPlanner(GermanyTransitCatalog(snapshot)).routeOptions(
            MapPoint(origin.latitude, origin.longitude, "Loop Origin"),
            MapPoint(destination.latitude, destination.longitude, "Loop Destination"),
        )
        assertEquals(listOf("loop:loop-trip"), options.map { it.id })
    }

    @Test fun routeWithNoRecordedTripsNeverInventsAScheduledDeparture() {
        val from = CatalogStop("a", "vbb", "Berlin Origin", 52.5200, 13.4050)
        val to = CatalogStop("b", "vbb", "Berlin Destination", 52.5300, 13.4150)
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(from, to),
            routes = listOf(CatalogRoute("no-service", "vbb", "S", "Berlin Destination", listOf("a", "b"))),
            trips = emptyList(),
        )
        val planner = SearchMapPlanner(GermanyTransitCatalog(snapshot))
        assertTrue(
            planner.routeOptions(
                MapPoint(from.latitude, from.longitude, "Berlin Origin"),
                MapPoint(to.latitude, to.longitude, "Berlin Destination"),
            ).isEmpty()
        )
    }

    @Test fun farAwayMapPinsCannotInventDirectRoutes() {
        val boarding = CatalogStop("berlin-a", "vbb", "Berlin A", 52.5200, 13.4050)
        val arrival = CatalogStop("berlin-b", "vbb", "Berlin B", 52.5300, 13.4150)
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(boarding, arrival),
            routes = listOf(CatalogRoute("local", "vbb", "S", "Berlin B", listOf("berlin-a", "berlin-b"))),
            trips = listOf(CatalogTrip("local-trip", "vbb", "local", "09:00")),
        )
        val scoped = SearchMapPlanner(GermanyTransitCatalog(snapshot))
        val from = MapPoint(boarding.latitude, boarding.longitude, "Berlin A")
        val to = MapPoint(arrival.latitude, arrival.longitude, "Berlin B")
        assertEquals(listOf("local:local-trip"), scoped.routeOptions(from, to).map { it.id })
        // Nearby manual coordinates still resolve to the selected stop.
        assertEquals(1, scoped.routeOptions(MapPoint(52.522, 13.405, "near boarding"), to).size)
        val distant = MapPoint(48.1400, 11.5800, "München")
        assertTrue(scoped.routeOptions(distant, to).isEmpty())
        assertTrue(scoped.routeOptions(from, distant).isEmpty())
        assertTrue(scoped.routeOptions(MapPoint(52.5500, 13.4050, "too far"), to).isEmpty())
    }

    @Test fun geocoderIsProviderSwappable() {
        val custom = SearchMapPlanner(GermanyTransitCatalog(TestGermanyTransitFixtures.snapshot), object : GeocodeProvider {
            override fun search(query: String) = listOf(MapPoint(50.0, 8.0, "POI:$query"))
        })
        assertEquals("POI:Museum", custom.addressOrPoi("Museum").single().label)
    }
}
