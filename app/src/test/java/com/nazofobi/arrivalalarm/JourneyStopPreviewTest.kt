package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyStopPreviewTest {
    @Test fun shortTripDisplaysEveryStopExactlyOnce() {
        assertEquals((0 until 16).toList(), journeyStopPreviewIndices(16, expanded = false))
        assertEquals((0 until 3).toList(), journeyStopPreviewIndices(3, expanded = false))
        assertEquals(emptyList<Int>(), journeyStopPreviewIndices(0, expanded = false))
    }

    @Test fun longTripAlwaysKeepsTheRealFinalDestination() {
        val indices = journeyStopPreviewIndices(64, expanded = false)
        assertEquals(16, indices.size)
        assertEquals((0 until 15).toList() + 63, indices)
        assertEquals(indices.size, indices.distinct().size)
    }

    @Test fun expandingRestoresAllOriginalOrderedStopIndices() {
        assertEquals((0 until 64).toList(), journeyStopPreviewIndices(64, expanded = true))
        assertEquals((0 until 17).toList(), journeyStopPreviewIndices(17, expanded = true))
    }
}
