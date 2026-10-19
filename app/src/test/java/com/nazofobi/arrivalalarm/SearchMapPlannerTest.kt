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

    @Test fun searchRetainsSameNamedStopsByProviderScopedStopId() {
        val a = CatalogStop("shared", "vbb", "Central Station", 52.5200, 13.4050)
        val b = CatalogStop("shared", "mvv", "Central Station", 48.1400, 11.5800)
        val c = CatalogStop("platform", "vbb", "Central Station", 52.5210, 13.4060)
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(a, b, c, a),
            routes = emptyList(),
            trips = emptyList(),
        )
        val results = SearchMapPlanner(GermanyTransitCatalog(snapshot))
            .search("Central", 10).filterIsInstance<SearchResult.Stop>()
        assertEquals(listOf(a, b, c), results.map { it.value })
        assertEquals(2, SearchMapPlanner(GermanyTransitCatalog(snapshot))
            .search("Central", 2).size)
    }

    @Test fun searchRetainsSameLabelRoutesAndTripsWithDifferentIdentities() {
        val berlin = CatalogRoute("route-1", "vbb", "S1", "Central", emptyList())
        val munich = CatalogRoute("route-1", "mvv", "S1", "Central", emptyList())
        val berlinFirst = CatalogTrip("trip-1", "vbb", "route-1", "08:00")
        val berlinSecond = CatalogTrip("trip-2", "vbb", "route-1", "08:00")
        val munichTrip = CatalogTrip("trip-1", "mvv", "route-1", "08:00")
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = emptyList(),
            routes = listOf(berlin, munich, berlin),
            trips = listOf(berlinFirst, berlinSecond, munichTrip, berlinFirst),
        )
        val results = SearchMapPlanner(GermanyTransitCatalog(snapshot)).search("S1", 10)
        val routes = results.filterIsInstance<SearchResult.Route>()
        val trips = results.filterIsInstance<SearchResult.Trip>()
        assertEquals(listOf(berlin, munich), routes.map { it.value })
        assertEquals(
            listOf(berlinFirst, berlinSecond, munichTrip),
            trips.map { it.value },
        )
        assertEquals(5, results.size)
        assertEquals(2, results.map { it.label }.distinct().size)
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

    @Test fun invalidMapPointCoordinatesNeverSnapToNearbyStopsOrRoutes() {
        val from = MapPoint(53.0834, 8.8137, "Bremen Hbf")
        val to = MapPoint(53.0531, 8.7866, "Flughafen Bremen")
        assertTrue(planner.routeOptions(from, to).isNotEmpty())
        assertTrue(planner.nearby(from, 1).isNotEmpty())
        val invalidPoints = listOf(
            MapPoint(Double.NaN, 8.8137, "invalid latitude"),
            MapPoint(53.0834, Double.NaN, "invalid longitude"),
            MapPoint(Double.POSITIVE_INFINITY, 8.8137, "infinite latitude"),
            MapPoint(53.0834, Double.NEGATIVE_INFINITY, "infinite longitude"),
            MapPoint(91.0, 8.8137, "too far north"),
            MapPoint(-91.0, 8.8137, "too far south"),
            MapPoint(53.0834, 181.0, "too far east"),
            MapPoint(53.0834, -181.0, "too far west"),
        )
        invalidPoints.forEach { point ->
            assertTrue("Invalid origin ${point.label}", planner.routeOptions(point, to).isEmpty())
            assertTrue("Invalid destination ${point.label}", planner.routeOptions(from, point).isEmpty())
            assertTrue("Invalid nearby point ${point.label}", planner.nearby(point).isEmpty())
            assertTrue("Invalid marker center ${point.label}", planner.mapMarkers(point).isEmpty())
        }
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

    @Test fun blankOrInvalidGeocoderResultsNeverBecomeMapPins() {
        val queries = mutableListOf<String>()
        val valid = MapPoint(52.5200, 13.4050, "Berlin")
        val geocoder = SearchMapPlanner(
            GermanyTransitCatalog(TestGermanyTransitFixtures.snapshot),
            object : GeocodeProvider {
                override fun search(query: String): List<MapPoint> {
                    queries.add(query)
                    return listOf(
                        valid,
                        MapPoint(Double.NaN, 13.4050, "NaN"),
                        MapPoint(52.5200, Double.POSITIVE_INFINITY, "infinite"),
                        MapPoint(91.0, 13.4050, "north"),
                        MapPoint(52.5200, -181.0, "west"),
                    )
                }
            },
        )

        assertTrue(geocoder.addressOrPoi("  \\t  ").isEmpty())
        assertTrue(queries.isEmpty())
        assertEquals(listOf(valid), geocoder.addressOrPoi("  Berlin  "))
        assertEquals(listOf("Berlin"), queries)
    }

    @Test fun geocoderIsProviderSwappable() {
        val custom = SearchMapPlanner(GermanyTransitCatalog(TestGermanyTransitFixtures.snapshot), object : GeocodeProvider {
            override fun search(query: String) = listOf(MapPoint(50.0, 8.0, "POI:$query"))
        })
        assertEquals("POI:Museum", custom.addressOrPoi("Museum").single().label)
    }
}
