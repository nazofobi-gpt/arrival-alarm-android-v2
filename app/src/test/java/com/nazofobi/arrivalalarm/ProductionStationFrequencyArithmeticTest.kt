package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionStationFrequencyArithmeticTest {
    @Test fun oversizedHeadwayDoesNotWrapBackIntoBoardWindow() {
        val start = 21_600L
        val end = 28_800L
        val first = firstFrequencyBoardAtOrAfter(
            startSeconds = start,
            earliestSeconds = start + 1L,
            headwaySeconds = Int.MAX_VALUE,
        )

        assertEquals(start + Int.MAX_VALUE.toLong(), first)
        assertTrue(first > Int.MAX_VALUE.toLong())
        assertTrue(first >= end)
    }

    @Test fun ordinaryHeadwaysPreserveExactAndCeilingBoundaries() {
        val start = 21_600L
        assertEquals(start, firstFrequencyBoardAtOrAfter(start, start, 600))
        assertEquals(22_200L, firstFrequencyBoardAtOrAfter(start, start + 1L, 600))
        assertEquals(22_200L, firstFrequencyBoardAtOrAfter(start, 22_200L, 600))
        assertEquals(22_800L, firstFrequencyBoardAtOrAfter(start, 22_201L, 600))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroHeadwayIsRejected() {
        firstFrequencyBoardAtOrAfter(21_600L, 21_601L, 0)
    }
}
