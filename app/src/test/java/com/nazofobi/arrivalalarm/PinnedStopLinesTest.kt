package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedStopLinesTest {
    @Test fun onlyConcreteStopAndRouteIdsCanBePinned() {
        assertNull(pinnedStopLineKey("", "route", "gtfs-full"))
        assertNull(pinnedStopLineKey(" ", "route", "gtfs-full"))
        assertNull(pinnedStopLineKey("stop", null, "gtfs-full"))
        assertNull(pinnedStopLineKey("stop", "", "gtfs-full"))
        assertNull(pinnedStopLineKey("stop", "  ", "gtfs-full"))
        // The journey screen uses coord:... when no concrete GTFS stop is known.
        assertNull(pinnedStopLineKey("coord:52.664,8.237", "route", "gtfs-full"))
    }

    @Test fun theSameRouteAtTwoStopsDoesNotShareAPin() {
        assertNotEquals(
            pinnedStopLineKey("Bremen Hbf", "RE9", "gtfs-full"),
            pinnedStopLineKey("Lohne", "RE9", "gtfs-full"),
        )
        assertNotEquals(
            pinnedStopLineKey("Bremen Hbf", "RE9", "gtfs-full"),
            pinnedStopLineKey("Bremen Hbf", "RE8", "gtfs-full"),
        )
    }

    @Test fun lengthPrefixesPreventAmbiguousConcatenation() {
        assertNotEquals(pinnedStopLineKey("a", "bc", "gtfs-full"), pinnedStopLineKey("ab", "c", "gtfs-full"))
        assertNotEquals(pinnedStopLineKey("1:2", "3", "gtfs-full"), pinnedStopLineKey("1", "2:3", "gtfs-full"))
        assertEquals(
            pinnedStopLineKey("Lohne", "route:1", "gtfs-full"),
            pinnedStopLineKey("Lohne", "route:1", "gtfs-full"),
        )
    }
    @Test fun theSameRawGtfsIdentifiersFromDifferentProvidersNeverShareAPin() {
        assertNotEquals(
            pinnedStopLineKey("stop-1", "route-9", "provider-a"),
            pinnedStopLineKey("stop-1", "route-9", "provider-b"),
        )
        assertNull(pinnedStopLineKey("stop-1", "route-9", null))
        assertNull(pinnedStopLineKey("stop-1", "route-9", " "))
    }
}
