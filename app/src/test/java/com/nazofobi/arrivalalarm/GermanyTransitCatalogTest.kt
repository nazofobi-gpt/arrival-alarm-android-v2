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

    @Test fun nearlyAntipodalValidCoordinatesCannotCrashDistanceRounding() {
        // Spherical roundoff makes the raw haversine term 1.0000000000000004.
        // Without clamping, sqrt(1 - a) becomes NaN and roundToInt() throws.
        val antipodal = CatalogStop(
            "antipodal", "gtfs", "Far stop",
            59.0670108474441, -35.58357680931971,
        )
        val localCatalog = GermanyTransitCatalog(
            TestGermanyTransitFixtures.snapshot.copy(stops = listOf(antipodal)),
        )

        val nearby = localCatalog.nearestStops(-59.06701098810696, 144.41642361996526)
        assertEquals(listOf("antipodal"), nearby.map { it.stop.id })
        assertTrue(nearby.single().distanceMeters in 20_000_000..20_020_000)
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

    @Test fun duplicateRouteIdsAcrossProvidersDoNotCrossAssociateTrips() {
        val berlinRoute = CatalogRoute("shared-route", "vbb", "S1", "Berlin", emptyList())
        val munichRoute = CatalogRoute("shared-route", "mvv", "S1", "Munich", emptyList())
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            routes = listOf(berlinRoute, munichRoute),
            trips = listOf(
                CatalogTrip("trip-berlin", "vbb", "shared-route", "07:00"),
                CatalogTrip("trip-munich", "mvv", "shared-route", "08:00"),
                CatalogTrip("trip-orphan", "missing-provider", "shared-route", "09:00"),
            ),
        )

        val matched = GermanyTransitCatalog(snapshot).searchTrips("trip-", limit = 10)
        assertEquals(listOf("trip-berlin", "trip-munich"), matched.map { it.first.id })
        assertEquals(listOf(berlinRoute, munichRoute), matched.map { it.second })
    }

    @Test fun unscopedRouteTripLookupFailsClosedWhenProvidersShareRouteId() {
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            routes = listOf(
                CatalogRoute("common-route", "vbb", "RE", "Berlin", emptyList()),
                CatalogRoute("common-route", "mvv", "RE", "München", emptyList()),
            ),
            trips = listOf(
                CatalogTrip("berlin-trip", "vbb", "common-route", "07:00"),
                CatalogTrip("munich-trip", "mvv", "common-route", "08:00"),
                CatalogTrip("orphan-trip", "other", "common-route", "09:00"),
            ),
        )
        val localCatalog = GermanyTransitCatalog(snapshot)

        // The old unqualified lookup must never mix Berlin and München services.
        assertTrue(localCatalog.tripsForRoute("common-route").isEmpty())
        assertEquals(
            listOf("berlin-trip"),
            localCatalog.tripsForRoute("vbb", "common-route").map { it.id },
        )
        assertEquals(
            listOf("munich-trip"),
            localCatalog.tripsForRoute("mvv", "common-route").map { it.id },
        )
        assertTrue(localCatalog.tripsForRoute("other", "common-route").isEmpty())
        assertTrue(localCatalog.tripsForRoute("", "common-route").isEmpty())
    }

    @Test fun providerScopedTripLookupDeduplicatesBeforeRouteOptionLimit() {
        val route = CatalogRoute("regional", "vbb", "RE", "Berlin", emptyList())
        val otherProviderRoute = route.copy(providerId = "mvv")
        val first = CatalogTrip("same-trip", "vbb", route.id, "07:00")
        val second = CatalogTrip("other-trip", "vbb", route.id, "07:30")
        val third = CatalogTrip("third-trip", "vbb", route.id, "08:00")
        val otherProviderTrip = first.copy(providerId = "mvv", departure = "09:00")
        val localCatalog = GermanyTransitCatalog(
            TestGermanyTransitFixtures.snapshot.copy(
                routes = listOf(route, otherProviderRoute),
                trips = listOf(
                    first,
                    first.copy(departure = "07:05"),
                    first,
                    second,
                    second.copy(departure = "07:35"),
                    third,
                    otherProviderTrip,
                ),
            ),
        )

        // The first imported row wins; duplicates cannot hide a third alternative.
        assertEquals(
            listOf(first, second, third),
            localCatalog.tripsForRoute("vbb", route.id).take(3),
        )
        assertEquals(listOf(otherProviderTrip), localCatalog.tripsForRoute("mvv", route.id))
        assertTrue(localCatalog.tripsForRoute(route.id).isEmpty())
    }

    @Test fun unscopedTripLookupAlsoDeduplicatesWithinItsSingleProvider() {
        val route = CatalogRoute("single-provider", "vbb", "S1", "Berlin", emptyList())
        val first = CatalogTrip("trip", "vbb", route.id, "06:00")
        val catalog = GermanyTransitCatalog(
            TestGermanyTransitFixtures.snapshot.copy(
                routes = listOf(route),
                trips = listOf(first, first.copy(departure = "06:05")),
            ),
        )
        assertEquals(listOf(first), catalog.tripsForRoute(route.id))
    }

    @Test fun unambiguousRouteTripLookupRetainsExistingSingleProviderBehavior() {
        val one = catalog.tripsForRoute("r-hb-6")
        assertEquals(1, one.size)
        assertEquals(one, catalog.tripsForRoute(one.single().providerId, "r-hb-6"))
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
    @Test fun providerScopedDuplicateRowsDoNotConsumeSearchResultLimit() {
        val firstStop = CatalogStop("same", "vbb", "Central A", 52.52, 13.40)
        val nextStop = CatalogStop("other", "vbb", "Central B", 52.53, 13.41)
        val otherProviderStop = CatalogStop("same", "mvv", "Central C", 48.14, 11.58)
        val firstRoute = CatalogRoute("same-route", "vbb", "S1", "Outer", emptyList())
        val otherProviderRoute = CatalogRoute("same-route", "mvv", "S1", "Outer", emptyList())
        val firstTrip = CatalogTrip("trip-1", "vbb", "same-route", "08:00")
        val otherProviderTrip = CatalogTrip("trip-1", "mvv", "same-route", "08:00")
        val snapshot = TestGermanyTransitFixtures.snapshot.copy(
            stops = listOf(firstStop, firstStop, nextStop, otherProviderStop),
            routes = listOf(firstRoute, firstRoute, otherProviderRoute),
            trips = listOf(firstTrip, firstTrip, otherProviderTrip),
        )
        val deduplicated = GermanyTransitCatalog(snapshot)

        assertEquals(
            listOf(firstStop, nextStop, otherProviderStop),
            deduplicated.searchStops("Central", limit = 3),
        )
        assertEquals(
            listOf(firstRoute, otherProviderRoute),
            deduplicated.searchRoutes("S1", limit = 2),
        )
        assertEquals(
            listOf(firstTrip, otherProviderTrip),
            deduplicated.searchTrips("trip-", limit = 2).map { it.first },
        )
        assertEquals(3, SearchMapPlanner(deduplicated).search("Central", limit = 3).size)
        assertEquals(listOf(firstStop), deduplicated.searchStops("Central", limit = 1))
    }


    @Test fun stopSearchFallsBackToUnicodeAccentFolding() {
        val munich = CatalogStop("munich", "mvv", "München Hauptbahnhof", 48.14, 11.58)
        val street = CatalogStop("street", "vbb", "Straße der Einheit", 52.52, 13.40)
        val cologne = CatalogStop("cologne", "vrr", "Köln Hauptbahnhof", 50.94, 6.96)
        val search = GermanyTransitCatalog(
            TestGermanyTransitFixtures.snapshot.copy(stops = listOf(munich, street, cologne)),
        )

        assertEquals(listOf(munich), search.searchStops("Munchen"))
        assertEquals(listOf(munich), search.searchStops("MUNCHEN", 1))
        assertEquals(listOf(street), search.searchStops("STRASSE"))
        assertEquals(listOf(cologne), search.searchStops("Koln"))
        assertEquals(listOf(munich), search.searchStops("München"))
    }

    @Test fun stopSearchPreservesLiteralRankingAndRejectsEmptyFoldedQueries() {
        val ascii = CatalogStop("ascii", "vbb", "Munchen", 52.52, 13.40)
        val accented = CatalogStop("accented", "mvv", "München", 48.14, 11.58)
        val search = GermanyTransitCatalog(
            TestGermanyTransitFixtures.snapshot.copy(stops = listOf(accented, ascii, accented)),
        )

        assertEquals(listOf(ascii), search.searchStops("Munchen"))
        assertEquals(listOf(accented), search.searchStops("München"))
        assertTrue(search.searchStops("\u0301").isEmpty())
        assertTrue(search.searchStops("Munchen", 0).isEmpty())
    }

}
