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
}
