package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTripClockTest {
    @Test fun validIsoAndGtfsClocksKeepTheirDisplayedTime() {
        assertEquals("08:45", routeStopClock("2026-09-26T08:45:00+02:00"))
        assertEquals("08:45", routeStopClock("2026-09-26T08:45:00Z"))
        assertEquals("08:45", routeStopClock("08:45"))
        assertEquals("08:45", routeStopClock("08:45:59"))
        assertEquals("01:05", routeStopClock("1:05:07"))
        assertEquals("25:05", routeStopClock("25:05:00"))
        assertEquals("47:59", routeStopClock("47:59"))
    }

    @Test fun malformedTrailingProviderDataNeverBecomesAFalseEta() {
        listOf(
            "08:45garbage",
            "08:45:00suffix",
            "08:45+02:00",
            "2026-09-26T08:45:00+02:00junk",
            "not-a-dateT08:45:00Z",
            "2026-09-26T08:45:00+99:00",
            "2026-02-30T08:45:00Z",
            "2026-09-26T25:45:00Z",
        ).forEach { assertNull("Expected invalid full provider clock: $it", routeStopClock(it)) }
    }

    @Test fun badMinutesSecondsAndExcessServiceHoursAreRejected() {
        listOf(
            "48:00",
            "99:00:00",
            "12:60",
            "12:05:90",
            "-1:05",
            "12:05:00:00",
            "",
            "  ",
        ).forEach { assertNull("Expected invalid GTFS clock: $it", routeStopClock(it)) }
        assertNull(routeStopClock(null))
    }
}
