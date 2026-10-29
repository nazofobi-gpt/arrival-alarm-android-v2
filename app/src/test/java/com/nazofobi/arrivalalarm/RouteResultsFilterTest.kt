package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResultsFilterTest {
    private val origin = MapPoint(52.663, 8.233, "Lohne")
    private val destination = MapPoint(53.012, 9.033, "Achim")

    private fun option(id: String, transfers: Int): RouteOption = RouteOption(
        id = id,
        origin = origin,
        destination = destination,
        line = "RE",
        direction = "Achim",
        departure = "10:00",
        arrival = "11:00",
        walkingMinutes = 2,
        transfers = transfers,
    )

    @Test
    fun disabledFilterPreservesProviderOrderAndAllCandidates() {
        val candidates = listOf(option("via-1", 1), option("direct-1", 0), option("via-2", 2))
        assertEquals(candidates, RouteResultsFilter().apply(candidates))
    }

    @Test
    fun directOnlyRetainsAllDirectCandidatesEvenWhenBeyondFirstThree() {
        val candidates = listOf(
            option("via-1", 1),
            option("via-2", 2),
            option("via-3", 1),
            option("direct-1", 0),
            option("direct-2", 0),
        )
        assertEquals(
            listOf("direct-1", "direct-2"),
            RouteResultsFilter(directOnly = true).apply(candidates).map { it.id },
        )
    }

    @Test
    fun directOnlyShowsNoResultsWhenOnlyTransfersExist() {
        assertTrue(RouteResultsFilter(directOnly = true).apply(listOf(option("via", 1))).isEmpty())
        assertTrue(RouteResultsFilter(directOnly = true).apply(emptyList()).isEmpty())
    }

    @Test
    fun fewerTransfersFirstSortsActualOptionsAndKeepsTiesInProviderOrder() {
        val candidates = listOf(
            option("two", 2), option("first-direct", 0),
            option("one", 1), option("second-direct", 0),
        )
        assertEquals(
            listOf("first-direct", "second-direct", "one", "two"),
            RouteResultsFilter(fewerTransfersFirst = true).apply(candidates).map { it.id },
        )
        assertEquals(listOf("two", "first-direct", "one", "second-direct"),
            candidates.map { it.id })
    }

    @Test
    fun fewerTransfersFirstNeverPromotesMalformedNegativeTransferCount() {
        val candidates = listOf(option("unknown", -1), option("one", 1), option("direct", 0))
        assertEquals(
            listOf("direct", "one"),
            RouteResultsFilter(fewerTransfersFirst = true).apply(candidates).map { it.id },
        )
        assertEquals(
            listOf("direct"),
            RouteResultsFilter(directOnly = true, fewerTransfersFirst = true)
                .apply(candidates).map { it.id },
        )
    }

    @Test
    fun unfilteredRoutesAlsoRejectNegativeTransferCountsWithoutMutatingSource() {
        val candidates = listOf(
            option("invalid", -1),
            option("via", 1),
            option("invalid-min", Int.MIN_VALUE),
            option("direct", 0),
        )
        assertEquals(
            listOf("via", "direct"),
            RouteResultsFilter().apply(candidates).map { it.id },
        )
        assertEquals(
            listOf("direct", "via"),
            RouteResultsFilter(fewerTransfersFirst = true).apply(candidates).map { it.id },
        )
        assertEquals(4, candidates.size)
    }

    @Test
    fun oneTransferLimitPreservesProviderOrderAndExcludesInvalidCounts() {
        val candidates = listOf(
            option("two", 2), option("one", 1),
            option("unknown", -1), option("direct", 0),
        )
        assertEquals(
            listOf("one", "direct"),
            RouteResultsFilter(maxTransfers = 1).apply(candidates).map { it.id },
        )
        assertEquals(
            listOf("direct"),
            RouteResultsFilter(directOnly = true, maxTransfers = 1)
                .apply(candidates).map { it.id },
        )
        assertEquals(
            listOf("direct", "one"),
            RouteResultsFilter(fewerTransfersFirst = true, maxTransfers = 1)
                .apply(candidates).map { it.id },
        )
        assertTrue(RouteResultsFilter(maxTransfers = -1).apply(candidates).isEmpty())
    }

    @Test
    fun oneTransferLimitShowsEmptyWhenOnlyLongConnectionsExist() {
        assertTrue(
            RouteResultsFilter(maxTransfers = 1)
                .apply(listOf(option("two", 2), option("three", 3)))
                .isEmpty(),
        )
    }

    @Test
    fun togglingBackToAllDoesNotMutateCandidates() {
        val candidates = listOf(option("via", 1), option("direct", 0))
        RouteResultsFilter(directOnly = true).apply(candidates)
        assertEquals(listOf("via", "direct"), RouteResultsFilter(false).apply(candidates).map { it.id })
    }

    @Test
    fun negativeWalkingDurationsAreRejectedBeforeEveryRouteFilterAndLimit() {
        val invalid = option("bad-walk", transfers = 0).copy(walkingMinutes = -3)
        val validDirect = option("direct", transfers = 0).copy(walkingMinutes = 0)
        val validTransfer = option("transfer", transfers = 1).copy(walkingMinutes = 7)
        val source = listOf(invalid, validTransfer, validDirect)

        assertEquals(
            listOf("transfer", "direct"),
            RouteResultsFilter().apply(source).map { it.id },
        )
        assertEquals(
            listOf("direct"),
            RouteResultsFilter(directOnly = true).apply(source).map { it.id },
        )
        assertEquals(
            listOf("direct", "transfer"),
            RouteResultsFilter(fewerTransfersFirst = true, maxTransfers = 1)
                .apply(source).map { it.id },
        )
        assertEquals(-3, source.first().walkingMinutes)
    }

    @Test
    fun extremeNegativeWalkingValuesCannotBecomeJourneyCandidates() {
        val invalid = option("impossible", transfers = 0)
            .copy(walkingMinutes = Int.MIN_VALUE)
        val valid = option("valid", transfers = 0).copy(walkingMinutes = Int.MAX_VALUE)
        assertEquals(
            listOf("valid"),
            RouteResultsFilter().apply(listOf(invalid, valid)).map { it.id },
        )
    }

    @Test
    fun blankRouteIdsAndNonphysicalEndpointsAreNotSelectable() {
        val candidates = listOf(
            option("  ", 0),
            option("bad-lat", 0).copy(origin = origin.copy(latitude = Double.NaN)),
            option("bad-lon", 0).copy(destination = destination.copy(longitude = 181.0)),
            option("infinite", 0).copy(origin = origin.copy(longitude = Double.POSITIVE_INFINITY)),
            option("valid", 0),
        )
        assertEquals(listOf("valid"), RouteResultsFilter().apply(candidates).map { it.id })
        assertEquals(
            listOf("valid"),
            RouteResultsFilter(directOnly = true, fewerTransfersFirst = true)
                .apply(candidates).map { it.id },
        )
        assertEquals(5, candidates.size)
    }

    @Test
    fun contradictoryRoutesSharingAnIdCannotSelectTheWrongJourney() {
        val candidates = listOf(
            option("same-id", 0).copy(line = "RE"),
            option("safe-transfer", 1),
            option("same-id", 0).copy(line = "IC"),
            option("safe-direct", 0),
        )
        assertEquals(
            listOf("safe-transfer", "safe-direct"),
            RouteResultsFilter().apply(candidates).map { it.id },
        )
        assertEquals(
            listOf("safe-direct", "safe-transfer"),
            RouteResultsFilter(fewerTransfersFirst = true).apply(candidates).map { it.id },
        )
    }

    @Test
    fun discardedNonphysicalCandidateCannotShadowAnotherRouteWithSameId() {
        // Detail lookup still sees the raw source list and selects by ID.
        // Discarding the first bad option alone would expose the second card
        // while silently opening the first, nonphysical journey.
        val bad = option("reused-id", 0).copy(
            origin = origin.copy(latitude = Double.NaN),
        )
        val good = option("reused-id", 0)
        val unaffected = option("safe-id", 1)
        val candidates = listOf(bad, good, unaffected)

        assertEquals(
            listOf("safe-id"),
            RouteResultsFilter().apply(candidates).map { it.id },
        )
        assertEquals(
            listOf("safe-id"),
            RouteResultsFilter(maxTransfers = 1).apply(candidates).map { it.id },
        )
        assertEquals(candidates, listOf(bad, good, unaffected))
    }

    @Test
    fun exactDuplicateProviderRowsAreDisplayedOnceWithoutChangingOrder() {
        val repeated = option("first", 0)
        val candidates = listOf(repeated, repeated.copy(), option("second", 0), repeated)
        assertEquals(
            listOf("first", "second"),
            RouteResultsFilter().apply(candidates).map { it.id },
        )
        assertEquals(4, candidates.size)
    }

    @Test
    fun invisibleOrControlRouteIdsNeverBecomeSelectableJourneyCards() {
        val unsafe = listOf(
            option("\u200B", 0), // zero-width space is not ordinary whitespace
            option("route\u202Ereversed", 0), // bidirectional override
            option("route\nnew-line", 0),
            option("route\tseparator", 0),
        )
        val valid = option("München-RE-42", 0)
        val source = unsafe + valid

        assertEquals(listOf(valid), RouteResultsFilter().apply(source))
        assertEquals(listOf(valid), RouteResultsFilter(directOnly = true).apply(source))
        assertEquals(
            listOf(valid),
            RouteResultsFilter(fewerTransfersFirst = true, maxTransfers = 0).apply(source),
        )
        assertEquals(5, source.size)
    }

    @Test
    fun validGeographicBoundaryCoordinatesRemainSelectable() {
        val boundary = option("boundary", 0).copy(
            origin = origin.copy(latitude = -90.0, longitude = 180.0),
            destination = destination.copy(latitude = 90.0, longitude = -180.0),
        )
        assertEquals(listOf(boundary), RouteResultsFilter().apply(listOf(boundary)))
    }

}
