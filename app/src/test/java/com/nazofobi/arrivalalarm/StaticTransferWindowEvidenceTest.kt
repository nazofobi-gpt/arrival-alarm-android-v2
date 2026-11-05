package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class StaticTransferWindowEvidenceTest {
    private val t = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli()
    private val first = StaticTransitLeg("t1", "r1", "a", "x", t, t + 20 * 60_000L)
    private val second = StaticTransitLeg("t2", "r2", "y", "b", t + 25 * 60_000L, t + 50 * 60_000L)
    private val names = mapOf("x" to "Bremen Hbf", "y" to "Platform Süd")

    @Test fun onlyBoundedUnspoofedGtfsPlatformLabelsAreShown() {
        assertEquals("7 B", boundedGtfsPlatformCode(" 7 B "))
        assertEquals("2", boundedGtfsPlatformCode("2"))
        assertNull(boundedGtfsPlatformCode(null))
        assertNull(boundedGtfsPlatformCode("   "))
        assertNull(boundedGtfsPlatformCode("x".repeat(25)))
        assertNull(boundedGtfsPlatformCode("4\n5"))
        assertNull(boundedGtfsPlatformCode("4\u202e5"))
    }

    @Test fun displaysBothSourceStopsAndFiveMinuteScheduledGap() {
        val result = plannedStaticTransferWindows(listOf(first, second)) { names[it] }
        assertEquals(1, result.size)
        assertEquals("x", result.single().arrivalStopId)
        assertEquals("y", result.single().departureStopId)
        assertEquals("Bremen Hbf", result.single().arrivalStopName)
        assertEquals("Platform Süd", result.single().departureStopName)
        assertEquals(5, result.single().plannedGapMinutes)
        assertEquals(second.departureEpochMillis, result.single().plannedDepartureEpochMillis)
        assertEquals(first.arrivalEpochMillis, result.single().plannedArrivalEpochMillis)
    }

    @Test fun roundsUpSecondsWithoutReadingAmbiguousDstLocalClocks() {
        val result = plannedStaticTransferWindows(
            listOf(first, second.copy(departureEpochMillis = first.arrivalEpochMillis + 61_000L)),
        ) { names[it] }
        assertEquals(2, result.single().plannedGapMinutes)
    }

    @Test fun missingNamedStopOrContradictoryOrUnboundedClockFailsClosed() {
        assertTrue(plannedStaticTransferWindows(listOf(first, second)) { null }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first, second.copy(
            departureEpochMillis = first.arrivalEpochMillis - 1L,
        ))) { names[it] }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first, second.copy(
            departureEpochMillis = first.arrivalEpochMillis + 4 * 60 * 60_000L,
        ))) { names[it] }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first, second.copy(
            departureEpochMillis = Long.MAX_VALUE,
        ))) { names[it] }.isEmpty())
    }

    @Test fun departureCountdownShowsOnlyFutureSourceBackedNearTermUtcInstants() {
        val departure = second.departureEpochMillis
        assertEquals(2, plannedTransferDepartureCountdownMinutes(departure, departure - 61_000L))
        assertEquals(1, plannedTransferDepartureCountdownMinutes(departure, departure - 1L))
        assertEquals(180, plannedTransferDepartureCountdownMinutes(departure, departure - 10_800_000L))
        assertEquals(null, plannedTransferDepartureCountdownMinutes(departure, departure - 10_800_001L))
        assertEquals(null, plannedTransferDepartureCountdownMinutes(departure, departure))
        assertEquals(null, plannedTransferDepartureCountdownMinutes(departure, departure + 1L))
        assertEquals(null, plannedTransferDepartureCountdownMinutes(0L, departure - 30_000L))
        assertEquals(null, plannedTransferDepartureCountdownMinutes(Long.MAX_VALUE, 1L))
        // Explicit epoch instants remain unambiguous across Europe/Berlin DST.
        val dst = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli()
        assertEquals(5, plannedTransferDepartureCountdownMinutes(dst, dst - 5 * 60_000L))
    }

    @Test fun scheduledDepartureUsesAgencyZoneAndDisambiguatesDstOverlap() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val beforeFallback = Instant.parse("2026-10-25T00:30:00Z").toEpochMilli()
        val afterFallback = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()
        assertEquals("25.10.2026 02:30 +02:00", plannedTransferDepartureLocalTime(beforeFallback, berlin))
        assertEquals("25.10.2026 02:30 +01:00", plannedTransferDepartureLocalTime(afterFallback, berlin))
        assertEquals(null, plannedTransferDepartureLocalTime(0L, berlin))
    }

    @Test fun passedDepartureShowsOnlyRecentScheduledHintNotLiveMissedConnection() {
        val departure = second.departureEpochMillis
        assertFalse(plannedTransferDepartureRecentlyPassed(departure, departure))
        assertTrue(plannedTransferDepartureRecentlyPassed(departure, departure + 1L))
        assertTrue(plannedTransferDepartureRecentlyPassed(departure, departure + 10_800_000L))
        assertFalse(plannedTransferDepartureRecentlyPassed(departure, departure + 10_800_001L))
        assertFalse(plannedTransferDepartureRecentlyPassed(departure, departure - 1L))
        assertFalse(plannedTransferDepartureRecentlyPassed(0L, departure + 1L))
        assertFalse(plannedTransferDepartureRecentlyPassed(Long.MAX_VALUE, 1L))
    }

    @Test fun transferWalkMapNeedsTwoDistinctSourceStopsAndValidCoordinates() {
        val walk = plannedTransferWalkingCoordinates(
            "X", "Y", 53.075, 8.808, 53.079, 8.813,
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=53.075,8.808" +
                "&destination=53.079,8.813&travelmode=walking",
            plannedTransferWalkingDirectionsUrl(walk!!),
        )
        assertNull(plannedTransferWalkingCoordinates("X", "X", 53.075, 8.808, 53.079, 8.813))
        assertNull(plannedTransferWalkingCoordinates("X", "Y", null, 8.808, 53.079, 8.813))
        assertNull(plannedTransferWalkingCoordinates("X", "Y", Double.NaN, 8.808, 53.079, 8.813))
        assertNull(plannedTransferWalkingCoordinates("X", "Y", 0.0, 0.0, 53.079, 8.813))
        assertNull(plannedTransferWalkingCoordinates("X", "Y", 53.075, 8.808, 200.0, 8.813))
        assertNull(plannedTransferWalkingDirectionsUrl(
            RouteTransferWalkingCoordinates(53.075, 8.808, Double.POSITIVE_INFINITY, 8.813),
        ))
    }

    @Test fun refusesToShowPartialTransferEvidenceOnMultipleLegs() {
        val third = StaticTransitLeg("t3", "r3", "c", "d",
            second.arrivalEpochMillis - 1L, second.arrivalEpochMillis + 60_000L)
        assertTrue(plannedStaticTransferWindows(listOf(first, second, third)) { names[it] }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first)) { names[it] }.isEmpty())
    }

    @Test fun warnsOnlyWhenStraightLineAtBriskSpeedExceedsGap() {
        val far = RouteTransferWalkingCoordinates(53.075, 8.808, 53.095, 8.808)
        val scheduledArrival = t + 20 * 60_000L
        val base = RouteTransferWindowEvidence(
            "X", "Y", "Arrival", "Boarding", 5,
            plannedArrivalEpochMillis = scheduledArrival,
            plannedDepartureEpochMillis = scheduledArrival + 300_000L,
            walkingCoordinates = far,
        )
        assertTrue(plannedTransferStraightLineWalkingRisk(base))
        assertTrue(plannedTransferStraightLineDistanceMeters(base) in 2_200..2_250)
        // A wider scheduled interval is NOT called safe; only the warning disappears.
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(
            plannedGapMinutes = 30,
            plannedDepartureEpochMillis = scheduledArrival + 30 * 60_000L,
        )))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(walkingCoordinates = null)))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(plannedGapMinutes = 0)))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(departureStopId = "X")))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(
            walkingCoordinates = far.copy(departureLatitude = Double.NaN),
        )))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(
            walkingCoordinates = RouteTransferWalkingCoordinates(53.075, 8.808, 53.0751, 8.808),
        )))

        // 4m01s displays as 5min. A ~445m walk at 1.7m/s exceeds 241s,
        // even though it fits within the rounded 300s label.
        val close = RouteTransferWalkingCoordinates(53.075, 8.808, 53.079, 8.808)
        val partialMinute = base.copy(
            plannedDepartureEpochMillis = scheduledArrival + 241_000L,
            walkingCoordinates = close,
        )
        assertTrue(plannedTransferStraightLineWalkingRisk(partialMinute))
        assertFalse(plannedTransferStraightLineWalkingRisk(partialMinute.copy(
            plannedDepartureEpochMillis = scheduledArrival + 300_000L,
        )))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(
            plannedArrivalEpochMillis = 0L,
        )))
        assertNull(plannedTransferStraightLineDistanceMeters(base.copy(
            plannedArrivalEpochMillis = 0L,
        )))
        assertNull(plannedTransferStraightLineDistanceMeters(base.copy(
            plannedGapMinutes = 6,
        )))
        assertNull(plannedTransferStraightLineDistanceMeters(base.copy(
            walkingCoordinates = far.copy(departureLatitude = Double.NaN),
        )))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(
            plannedDepartureEpochMillis = Long.MAX_VALUE,
        )))
        assertFalse(plannedTransferStraightLineWalkingRisk(base.copy(
            plannedGapMinutes = 6,
        )))
    }
}
