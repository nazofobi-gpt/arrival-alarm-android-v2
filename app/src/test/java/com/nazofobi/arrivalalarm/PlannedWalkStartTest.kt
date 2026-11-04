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
