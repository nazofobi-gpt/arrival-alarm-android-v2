package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitResultLimitTest {
    @Test
    fun nonPositiveRequestsNeverBecomeUnboundedSqliteLimits() {
        listOf(Int.MIN_VALUE, -1, 0).forEach { requested ->
            assertEquals(0 to 0, boundedTransitResultSplit(requested))
        }
    }

    @Test
    fun ordinaryRequestsSplitWithoutChangingTheRequestedTotal() {
        assertEquals(1 to 0, boundedTransitResultSplit(1))
        assertEquals(1 to 1, boundedTransitResultSplit(2))
        assertEquals(2 to 1, boundedTransitResultSplit(3))
        assertEquals(50 to 49, boundedTransitResultSplit(99))
        assertEquals(50 to 50, boundedTransitResultSplit(100))
    }

    @Test
    fun hugeRequestsCannotOverflowOrRemoveTheSqliteLimit() {
        listOf(101, 1_000_000, Int.MAX_VALUE).forEach { requested ->
            val (lines, trips) = boundedTransitResultSplit(requested)
            assertEquals(50, lines)
            assertEquals(50, trips)
            assertTrue(lines > 0 && trips > 0)
            assertTrue(lines + trips <= 100)
        }
    }
}
