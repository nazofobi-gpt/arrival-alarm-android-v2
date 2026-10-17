package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyStopPreviewTest {
    @Test
    fun zeroStopsNeverCreatesSyntheticIndices() {
        assertEquals(emptyList<Int>(), journeyStopPreviewIndices(0, expanded = false))
        assertEquals(emptyList<Int>(), journeyStopPreviewIndices(0, expanded = true))
        assertEquals(emptyList<Int>(), journeyStopPreviewIndices(-1, expanded = false))
    }

    @Test
    fun journeysAtOrBelowLimitKeepAllOriginalStops() {
        assertEquals((0 until 16).toList(), journeyStopPreviewIndices(16, expanded = false))
        assertEquals((0 until 15).toList(), journeyStopPreviewIndices(15, expanded = false))
    }

    @Test
    fun collapsedLongJourneyKeepsActualDestinationWithoutDuplicatingStops() {
        assertEquals(
            (0 until 15).toList() + 16,
            journeyStopPreviewIndices(17, expanded = false),
        )
        val preview = journeyStopPreviewIndices(64, expanded = false)
        assertEquals(16, preview.size)
        assertEquals((0 until 15).toList() + 63, preview)
        assertEquals(preview.size, preview.toSet().size)
        assertEquals(48, preview.last() - preview[preview.lastIndex - 1] - 1)
    }

    @Test
    fun expandingShowsEveryOriginalStopInOrder() {
        assertEquals((0 until 17).toList(), journeyStopPreviewIndices(17, expanded = true))
        assertEquals((0 until 64).toList(), journeyStopPreviewIndices(64, expanded = true))
    }
}
