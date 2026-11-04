package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlannedWalkStartTest {
    private fun option(
        planned: String? = "2026-11-04T10:30:00+01:00",
        walkSeconds: Int? = 600,
        realtimeDeparture: String = "2026-11-04T10:45:00+01:00",
    ) = RouteOption(
        id = "source-trip",
        origin = MapPoint(52.67, 8.24, "Lohne"),
        destination = MapPoint(53.08, 8.81, "Bremen Hbf"),
        line = "RE", direction = "Bremen",
        departure = realtimeDeparture, arrival = "2026-11-04T11:30:00+01:00",
        walkingMinutes = 10, transfers = 0,
        stops = listOf(RouteStop(id = "stop-1", name = "Lohne", plannedDeparture = planned)),
        accessWalking = walkSeconds?.let {
            RouteWalkingLegEvidence(stopId = "stop-1", stopName = "Lohne", walkSeconds = it)
        },
    )

    @Test fun usesOnlyScheduledFirstStopTimeNotRealtime() {
        assertEquals(
            Instant.parse("2026-11-04T09:20:00Z").toEpochMilli(),
            plannedWalkStartEpochMillis(option()),
        )
    }

    @Test fun chosenBufferMovesRecommendedStartEarlierWithoutMovingDeparture() {
        val expected = Instant.parse("2026-11-04T09:15:00Z").toEpochMilli()
        assertEquals(expected, plannedWalkStartEpochMillis(option(), safetyBufferMinutes = 5))
        assertEquals(
            Instant.parse("2026-11-04T09:05:00Z").toEpochMilli(),
            plannedWalkStartEpochMillis(option(), safetyBufferMinutes = 15),
        )
    }

    @Test fun unsupportedBufferValuesFailClosed() {
        for (minutes in listOf(-5, 1, 4, 16, 100, Int.MAX_VALUE)) {
            assertNull(plannedWalkStartEpochMillis(option(), safetyBufferMinutes = minutes))
        }
    }

    @Test fun bufferUsesExplicitOffsetAcrossDstTransition() {
        assertEquals(
            Instant.parse("2026-10-25T01:15:00Z").toEpochMilli(),
            plannedWalkStartEpochMillis(
                option(planned = "2026-10-25T02:30:00+01:00"),
                safetyBufferMinutes = 5,
            ),
        )
    }

    @Test fun springDstOffsetPreservesTrueInstant() {
        assertEquals(
            Instant.parse("2026-03-29T01:20:00Z").toEpochMilli(),
            plannedWalkStartEpochMillis(option(planned = "2026-03-29T03:30:00+02:00")),
        )
    }

    @Test fun fallDstOffsetPreservesSecondOccurrence() {
        assertEquals(
            Instant.parse("2026-10-25T01:20:00Z").toEpochMilli(),
            plannedWalkStartEpochMillis(option(planned = "2026-10-25T02:30:00+01:00")),
        )
    }

    @Test fun walkingEstimateForDifferentBoardingStopFailsClosed() {
        val source = option()
        assertNull(plannedWalkStartEpochMillis(source.copy(
            accessWalking = RouteWalkingLegEvidence("other-stop", "Other", 600),
        )))
        assertNull(plannedWalkStartEpochMillis(source.copy(stops = emptyList())))
        assertNull(plannedWalkStartEpochMillis(source.copy(
            stops = listOf(source.stops.first().copy(id = "")),
        )))
    }

    @Test fun missingWalkingEvidenceFailsClosed() {
        assertNull(plannedWalkStartEpochMillis(option(walkSeconds = null)))
    }

    @Test fun unknownStopDepartureFailsClosed() {
        assertNull(plannedWalkStartEpochMillis(option(planned = null)))
    }

    @Test fun malformedAndUnknownOffsetFailClosed() {
        assertNull(plannedWalkStartEpochMillis(option(planned = "10:30")))
        assertNull(plannedWalkStartEpochMillis(option(planned = "2026-11-04T10:30:00-00:00")))
    }

    @Test fun zeroNegativeOrUnboundedWalkingEvidenceFailsClosed() {
        assertNull(plannedWalkStartEpochMillis(option(walkSeconds = 0)))
        assertNull(plannedWalkStartEpochMillis(option(walkSeconds = -1)))
        assertNull(plannedWalkStartEpochMillis(option(walkSeconds = 14401)))
    }
}

// Glanceable countdown for source-backed JNY-002: no reminder is armed.
class PlannedWalkRemainingMinutesTest {
    private fun option() = RouteOption(
        id = "trip", origin = MapPoint(52.67, 8.24, "Lohne"),
        destination = MapPoint(53.08, 8.81, "Bremen"),
        line = "RE", direction = "Bremen", departure = "10:30", arrival = "11:30",
        walkingMinutes = 10, transfers = 0,
        stops = listOf(RouteStop(id = "stop", name = "Lohne",
            plannedDeparture = "2026-11-04T10:30:00+01:00")),
        accessWalking = RouteWalkingLegEvidence("stop", "Lohne", 600),
    )

    @Test fun roundsPartialMinuteUpAndHidesAtWalkStart() {
        val o = option()
        val start = Instant.parse("2026-11-04T09:20:00Z").toEpochMilli()
        assertEquals(1L, plannedWalkMinutesRemaining(o, start - 1L))
        assertEquals(2L, plannedWalkMinutesRemaining(o, start - 60_001L))
        assertNull(plannedWalkMinutesRemaining(o, start))
        assertNull(plannedWalkMinutesRemaining(o, start + 1L))
    }

    @Test fun respectsUserSafetyBufferAndRejectsUnboundedLookAhead() {
        val o = option()
        val start = Instant.parse("2026-11-04T09:20:00Z").toEpochMilli()
        assertEquals(5L, plannedWalkMinutesRemaining(o, start - 300_000L))
        assertNull(plannedWalkMinutesRemaining(o, start - 180L * 60_000L - 1L))
        assertEquals(180L, plannedWalkMinutesRemaining(o, start - 180L * 60_000L))
        assertNull(plannedWalkMinutesRemaining(o, start - 300_000L, 5))
    }

    @Test fun missingOrMismatchedBoardingStopAndBadClockFailClosed() {
        val o = option()
        val now = Instant.parse("2026-11-04T09:18:00Z").toEpochMilli()
        assertNull(plannedWalkMinutesRemaining(o.copy(accessWalking = null), now))
        assertNull(plannedWalkMinutesRemaining(o.copy(accessWalking =
            RouteWalkingLegEvidence("another-stop", "Other", 600)), now))
        assertNull(plannedWalkMinutesRemaining(o, Long.MIN_VALUE))
    }
}

// Negative evidence and boundary tests for JNY-003: planned times, not realtime claims.
class PlannedWalkWarningTest {
    private fun option(planned: String? = "2026-11-04T10:30:00+01:00", seconds: Int? = 600) =
        RouteOption(
            id = "trip",
            origin = MapPoint(52.67, 8.24, "Lohne"),
            destination = MapPoint(53.08, 8.81, "Bremen"),
            line = "RE", direction = "Bremen", departure = "10:30", arrival = "11:00",
            walkingMinutes = 10, transfers = 0,
            stops = listOf(RouteStop(id = "stop", name = "Lohne", plannedDeparture = planned)),
            accessWalking = seconds?.let { RouteWalkingLegEvidence("stop", "Lohne", it) },
        )

    @Test fun noWarningBeforeEstimatedStart() {
        assertNull(plannedWalkWarning(option(), Instant.parse("2026-11-04T09:19:59Z").toEpochMilli()))
    }
    @Test fun warnAtEstimatedWalkStart() {
        assertEquals(
            PlannedWalkWarning.WALK_START_PASSED,
            plannedWalkWarning(option(), Instant.parse("2026-11-04T09:20:00Z").toEpochMilli()),
        )
    }
    @Test fun warnWhenPlannedDeparturePasses() {
        assertEquals(
            PlannedWalkWarning.PLANNED_DEPARTURE_PASSED,
            plannedWalkWarning(option(), Instant.parse("2026-11-04T09:30:00Z").toEpochMilli()),
        )
    }
    @Test fun absentEvidenceFailsClosed() {
        val now = Instant.parse("2026-11-04T10:00:00Z").toEpochMilli()
        assertNull(plannedWalkWarning(option(planned = null), now))
        assertNull(plannedWalkWarning(option(seconds = null), now))
        assertNull(plannedWalkWarning(option(planned = "10:30"), now))
        assertNull(plannedWalkWarning(option(seconds = 14_401), now))
    }
    @Test fun bufferAdvancesWarningButNotScheduledDeparture() {
        val now = Instant.parse("2026-11-04T09:15:00Z").toEpochMilli()
        assertEquals(PlannedWalkWarning.WALK_START_PASSED, plannedWalkWarning(option(), now, 5))
        assertNull(plannedWalkWarning(option(), now, 0))
        assertEquals(
            PlannedWalkWarning.PLANNED_DEPARTURE_PASSED,
            plannedWalkWarning(option(), Instant.parse("2026-11-04T09:30:00Z").toEpochMilli(), 5),
        )
    }

    @Test fun explicitDstOffsetUsesActualInstant() {
        assertEquals(
            PlannedWalkWarning.WALK_START_PASSED,
            plannedWalkWarning(option(planned = "2026-10-25T02:30:00+01:00"),
                Instant.parse("2026-10-25T01:20:00Z").toEpochMilli()),
        )
    }
}

class PlannedWalkDirectionsTest {
    private fun option() = RouteOption(
        id = "source-trip", origin = MapPoint(52.67, 8.24, "Lohne"),
        destination = MapPoint(53.08, 8.81, "Bremen"), line = "RE",
        direction = "Bremen", departure = "10:30", arrival = "11:30",
        walkingMinutes = 10, transfers = 0,
        stops = listOf(RouteStop(id = "stop-1", name = "Lohne",
            latitude = 52.67, longitude = 8.24,
            plannedDeparture = "2099-11-04T10:30:00+01:00")),
        accessWalking = RouteWalkingLegEvidence("stop-1", "Lohne", 600),
    )
    @Test fun validFirstStopLaunchUrlUsesWalkingModeButNoOrigin() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=52.67%2C8.24&travelmode=walking&dir_action=navigate",
            plannedWalkDirectionsUrl(option()),
        )
    }
    @Test fun missingMismatchedOrInvalidStopFailsClosed() {
        val o = option()
        assertNull(plannedWalkDirectionsUrl(o.copy(accessWalking = null)))
        assertNull(plannedWalkDirectionsUrl(o.copy(stops = emptyList())))
        assertNull(plannedWalkDirectionsUrl(o.copy(
            accessWalking = RouteWalkingLegEvidence("other", "Other", 600))))
        assertNull(plannedWalkDirectionsUrl(o.copy(stops = listOf(o.stops[0].copy(latitude = null)))))
        assertNull(plannedWalkDirectionsUrl(o.copy(stops = listOf(o.stops[0].copy(longitude = Double.NaN)))))
        assertNull(plannedWalkDirectionsUrl(o.copy(stops = listOf(o.stops[0].copy(latitude = 92.0)))))
        assertNull(plannedWalkDirectionsUrl(o.copy(stops = listOf(o.stops[0].copy(plannedDeparture = null)))))
    }
}

/** One-shot background reminder is deliberately approximate and opt-in. */
class PlannedWalkReminderDelayTest {
    private val option = RouteOption(
        id = "trip", origin = MapPoint(52.67, 8.24, "Lohne"),
        destination = MapPoint(53.08, 8.81, "Bremen"), line = "RE",
        direction = "Bremen", departure = "10:30", arrival = "11:30",
        walkingMinutes = 10, transfers = 0,
        stops = listOf(RouteStop(id = "stop", name = "Lohne",
            plannedDeparture = "2026-11-04T10:30:00+01:00")),
        accessWalking = RouteWalkingLegEvidence("stop", "Lohne", 600),
    )

    @Test fun onlyFutureBoundedScheduledEvidenceCanQueue() {
        val start = Instant.parse("2026-11-04T09:20:00Z").toEpochMilli()
        assertEquals(60_000L, plannedWalkReminderDelayMillis(option, start - 60_000L))
        assertEquals(86_400_000L, plannedWalkReminderDelayMillis(option, start - 86_400_000L))
        assertNull(plannedWalkReminderDelayMillis(option, start))
        assertNull(plannedWalkReminderDelayMillis(option, start - 59_999L))
        assertNull(plannedWalkReminderDelayMillis(option, start - 86_400_001L))
    }

    @Test fun missingOrMismatchedGtfsFirstStopCannotSchedule() {
        val now = Instant.parse("2026-11-04T09:18:00Z").toEpochMilli()
        assertNull(plannedWalkReminderDelayMillis(option.copy(accessWalking = null), now))
        assertNull(plannedWalkReminderDelayMillis(option.copy(
            accessWalking = RouteWalkingLegEvidence("wrong", "Wrong", 600)), now))
        assertNull(plannedWalkReminderDelayMillis(option.copy(
            stops = listOf(option.stops.first().copy(plannedDeparture = "10:30"))), now))
        assertNull(plannedWalkReminderDelayMillis(option, Long.MIN_VALUE))
    }

    @Test fun explicitOffsetAndBufferControlScheduledInstantNotRealtime() {
        val start = Instant.parse("2026-11-04T09:20:00Z").toEpochMilli()
        assertEquals(600_000L, plannedWalkReminderDelayMillis(option, start - 900_000L, 5))
        assertNull(plannedWalkReminderDelayMillis(option, start - 900_000L, 100))
    }
}
