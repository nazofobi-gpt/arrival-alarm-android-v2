package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResultsWalkingLimitTest {
    private fun option(id: String, walkingMinutes: Int, transfers: Int = 0) = RouteOption(
        id = id,
        origin = MapPoint(52.0, 8.0, "Lohne"),
        destination = MapPoint(52.1, 8.1, "Bremen"),
        line = "RE",
        direction = "Bremen",
        departure = "08:00",
        arrival = "09:00",
        walkingMinutes = walkingMinutes,
        transfers = transfers,
    )

    @Test fun walkingLimitExcludesOnlyLongRealCandidates() {
        val routes = listOf(option("long", 27), option("boundary", 15), option("short", 3))
        assertEquals(listOf("boundary", "short"),
            RouteResultsFilter(maxWalkingMinutes = 15).apply(routes).map { it.id })
        assertEquals(listOf("long", "boundary", "short"),
            RouteResultsFilter().apply(routes).map { it.id })
    }

    @Test fun walkingLimitCombinesWithTransferAndStableWalkingRanking() {
        val routes = listOf(
            option("transfer", 8, transfers = 2),
            option("direct", 14),
            option("short", 4, transfers = 1),
            option("too-long", 16),
        )
        assertEquals(listOf("short", "direct"),
            RouteResultsFilter(maxTransfers = 1, maxWalkingMinutes = 15,
                shorterWalkingFirst = true).apply(routes).map { it.id })
    }

    @Test fun negativeLimitNeverLeadsToAccidentalUnrestrictedResults() {
        assertTrue(RouteResultsFilter(maxWalkingMinutes = -1)
            .apply(listOf(option("a", 0))).isEmpty())
    }
}
