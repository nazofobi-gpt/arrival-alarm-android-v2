package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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

    @Test
    fun aHugeExpandedJourneyProvidesIndicesWithoutAllocatingAnIndexArray() {
        val indices = journeyStopPreviewIndices(Int.MAX_VALUE, expanded = true)
        assertEquals(Int.MAX_VALUE, indices.size)
        assertEquals(0, indices[0])
        assertEquals(123_456_789, indices[123_456_789])
        assertEquals(Int.MAX_VALUE - 1, indices.last())

        val collapsed = journeyStopPreviewIndices(Int.MAX_VALUE, expanded = false)
        assertEquals(COLLAPSED_JOURNEY_STOP_LIMIT, collapsed.size)
        assertEquals(Int.MAX_VALUE - 1, collapsed.last())
    }

    @Test
    fun expandedIndexViewRejectsOutOfRangeAccess() {
        val indices = journeyStopPreviewIndices(3, expanded = true)
        for (invalidIndex in listOf(-1, 3)) {
            try {
                indices[invalidIndex]
                fail("Index $invalidIndex should be rejected")
            } catch (_: IndexOutOfBoundsException) {
                // Matches List.get's contract for both ends of the index range.
            }
        }
    }
}
