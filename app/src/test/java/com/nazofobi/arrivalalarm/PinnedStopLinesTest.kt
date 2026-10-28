package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedStopLinesTest {
    @Test fun onlyConcreteStopAndRouteIdsCanBePinned() {
        assertNull(pinnedStopLineKey("", "route"))
        assertNull(pinnedStopLineKey(" ", "route"))
        assertNull(pinnedStopLineKey("stop", null))
        assertNull(pinnedStopLineKey("stop", ""))
        assertNull(pinnedStopLineKey("stop", "  "))
    }

    @Test fun theSameRouteAtTwoStopsDoesNotShareAPin() {
        assertNotEquals(
            pinnedStopLineKey("Bremen Hbf", "RE9"),
            pinnedStopLineKey("Lohne", "RE9"),
        )
        assertNotEquals(
            pinnedStopLineKey("Bremen Hbf", "RE9"),
            pinnedStopLineKey("Bremen Hbf", "RE8"),
        )
    }

    @Test fun lengthPrefixesPreventAmbiguousConcatenation() {
        assertNotEquals(pinnedStopLineKey("a", "bc"), pinnedStopLineKey("ab", "c"))
        assertNotEquals(pinnedStopLineKey("1:2", "3"), pinnedStopLineKey("1", "2:3"))
        assertEquals(
            pinnedStopLineKey("Lohne", "route:1"),
            pinnedStopLineKey("Lohne", "route:1"),
        )
    }
}
