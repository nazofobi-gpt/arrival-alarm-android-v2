package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun destinationCountdownDifferentiatesVerifiedRealtimeAndScheduledFallback() {
        val now = "2026-10-25T00:40:00Z".toTransitEpochSecondsOrNull()!!
        val actual = "2026-10-25T02:10:00+01:00"
        val scheduled = "2026-10-25T02:08:00+01:00"
        assertEquals(DestinationArrivalCountdown(30L, true),
            destinationArrivalCountdown(actual, scheduled, true, now))
        assertEquals(DestinationArrivalCountdown(28L, false),
            destinationArrivalCountdown(actual, scheduled, false, now))
        assertEquals(DestinationArrivalCountdown(28L, false),
            destinationArrivalCountdown("malformed", scheduled, true, now))
        assertEquals(DestinationArrivalCountdown(1L, true),
            destinationArrivalCountdown("2026-10-25T00:40:01Z", null, true, now))
    }

    @Test fun destinationCountdownRejectsUntrustedAndImpossibleTimeEvidence() {
        val now = "2026-10-25T00:40:00Z".toTransitEpochSecondsOrNull()!!
        listOf(null, "", "08:45", "2026-10-25T02:10:00+99:00",
            "2026-10-25T02:10:00+01:00junk", "2026-10-25T00:40:00Z",
            "2026-10-24T23:40:00Z", "2026-10-28T00:40:00Z").forEach {
            assertNull("Invalid or out-of-window arrival: $it",
                destinationArrivalCountdown(it, null, true, now))
        }
        assertNull(destinationArrivalCountdown("2026-10-25T02:10:00+01:00",
            null, false, now))
        assertNull(destinationArrivalCountdown("2026-10-25T02:10:00+01:00",
            null, true, 0L))
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

    @Test fun walkingWindowRiskUsesOnlyValidScheduledClocks() {
        val departure = "2026-09-26T08:00:00+02:00"
        val tenMinutesPrior = "2026-09-26T07:50:00+02:00".toTransitEpochSecondsOrNull()!!
        val fiveMinutesPrior = "2026-09-26T07:55:00+02:00".toTransitEpochSecondsOrNull()!!
        val departed = "2026-09-26T08:01:00+02:00".toTransitEpochSecondsOrNull()!!
        assertFalse(plannedWalkingExceedsBoardingWindow(departure, 301, tenMinutesPrior))
        assertTrue(plannedWalkingExceedsBoardingWindow(departure, 301, fiveMinutesPrior))
        assertFalse(plannedWalkingExceedsBoardingWindow(departure, 300, fiveMinutesPrior))
        assertFalse(plannedWalkingExceedsBoardingWindow(departure, 301, departed))
        assertFalse(plannedWalkingExceedsBoardingWindow(null, 301, fiveMinutesPrior))
        assertFalse(plannedWalkingExceedsBoardingWindow("invalid", 301, fiveMinutesPrior))
        assertFalse(plannedWalkingExceedsBoardingWindow(departure, 0, fiveMinutesPrior))
        assertFalse(plannedWalkingExceedsBoardingWindow(departure, 301, 0))
    }

    @Test fun intermediateStopNameMustBeSafeBeforeShowingOrSpeakingEta() {
        assertTrue(isActionableTransitLabel("Diepholz"))
        assertTrue(isActionableTransitLabel("  Diepholz  "))
        listOf<String?>(null, "", "   ", "\u200BDiepholz", "Die\npholz",
            "\u007fDiepholz", "X".repeat(121)).forEach { bad ->
            assertFalse("Unsafe next stop must not be announced: $bad",
                isActionableTransitLabel(bad))
        }
    }

    @Test fun boardingIdentityRequiresImminentArmedAndSourceNamedTrip() {
        assertTrue(boardingCheckEligible(JourneyPhase.ARMED, 10L, "RE 9", "Bremen Hbf", "Lohne"))
        assertTrue(boardingCheckEligible(JourneyPhase.ARMED, 15L, "RE 9", "Bremen Hbf", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, 16L, "RE 9", "Bremen Hbf", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, 0L, "RE 9", "Bremen Hbf", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, null, "RE 9", "Bremen Hbf", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARRIVED, 10L, "RE 9", "Bremen Hbf", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.DESTINATION_SELECTED, 10L, "RE 9", "Bremen Hbf", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, 10L, "", "Bremen Hbf", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, 10L, "RE 9", "", "Lohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, 10L, "RE 9", "Bremen Hbf", null))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, 10L, "RE 9", "Bremen Hbf", "\u200BLohne"))
        assertFalse(boardingCheckEligible(JourneyPhase.ARMED, 10L, "RE 9", "Bremen Hbf", "X".repeat(121)))
    }

}
