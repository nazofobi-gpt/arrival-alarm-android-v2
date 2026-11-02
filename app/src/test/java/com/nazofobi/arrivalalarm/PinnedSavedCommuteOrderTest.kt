package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PinnedSavedCommuteOrderTest {
    private fun preset(id: String) = SavedRoutePreset(
        id = id,
        name = "Saved $id",
        originLatitude = 52.665,
        originLongitude = 8.237,
        originLabel = "Lohne",
        destinationLatitude = 53.083,
        destinationLongitude = 8.813,
        destinationLabel = "Bremen",
        directOnlyRoutes = false,
        fewerTransfersFirst = true,
        shorterWalkingFirst = true,
        atMostOneTransfer = false,
        savedAtEpochMillis = 1_000L,
    )

    @Test fun selectedPinMovesToTopWithoutModifyingOriginalListOrObjects() {
        val first = preset("a")
        val second = preset("b")
        val third = preset("c")
        val source = listOf(first, second, third)
        val reordered = prioritizeSavedCommute(source, "c")
        assertEquals(listOf(third, first, second), reordered)
        assertEquals(listOf(first, second, third), source)
        assertSame(third, reordered.first())
    }

    @Test fun unknownOrBlankPinLeavesOrderUnchanged() {
        val source = listOf(preset("a"), preset("b"))
        assertSame(source, prioritizeSavedCommute(source, null))
        assertSame(source, prioritizeSavedCommute(source, ""))
        assertSame(source, prioritizeSavedCommute(source, "missing"))
        assertSame(source, prioritizeSavedCommute(source, "a"))
    }

    @Test fun emptyListRemainsEmpty() {
        assertEquals(emptyList<SavedRoutePreset>(), prioritizeSavedCommute(emptyList(), "missing"))
    }
}
