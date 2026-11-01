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
    @Test fun plannedBoardingCountdownUsesOffsetAwareProviderInstants() {
        val now = "2026-10-25T00:40:00Z".toTransitEpochSecondsOrNull()!!
        assertEquals(50L, scheduledBoardingMinutesRemaining("2026-10-25T02:30:00+01:00", now))
        assertEquals(1L, scheduledBoardingMinutesRemaining("2026-10-25T01:40:01+01:00", now))
        assertEquals(1L, scheduledBoardingMinutesRemaining("2026-10-25T00:40:01Z", now))
        assertEquals(60L, scheduledBoardingMinutesRemaining("2026-10-25T02:40:00+01:00", now))
    }

    @Test fun pastMalformedMissingAndDistantBoardingClocksStayHidden() {
        val now = "2026-10-25T00:40:00Z".toTransitEpochSecondsOrNull()!!
        listOf(null, "", "08:40", "2026-02-30T08:00:00Z",
            "2026-10-25T02:30:00+99:00", "2026-10-25T02:30:00Zsuffix",
            "2026-10-25T00:40:00Z", "2026-10-24T23:40:00Z",
            "2026-10-27T00:40:00Z").forEach {
            assertNull("Expected no boarding countdown for $it",
                scheduledBoardingMinutesRemaining(it, now))
        }
        assertNull(scheduledBoardingMinutesRemaining("2026-10-25T02:30:00+01:00", 0L))
        assertNull(scheduledBoardingMinutesRemaining("2026-10-25T02:30:00+01:00", Long.MAX_VALUE))
    }

}
