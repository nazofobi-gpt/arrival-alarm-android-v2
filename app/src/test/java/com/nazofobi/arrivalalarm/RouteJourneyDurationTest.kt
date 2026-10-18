package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteJourneyDurationTest {
    private fun option(
        departure: String = "23:50",
        arrival: String = "00:15",
        departureMillis: Long? = null,
        arrivalMillis: Long? = null,
    ) = RouteOption(
        id = "overnight-trip",
        origin = MapPoint(0.0, 0.0, "Origin"),
        destination = MapPoint(0.0, 0.0, "Destination"),
        line = "RE",
        direction = "Destination",
        departure = departure,
        arrival = arrival,
        walkingMinutes = 0,
        transfers = 0,
        departureEpochMillis = departureMillis,
        arrivalEpochMillis = arrivalMillis,
    )

    @Test fun datedJourneyLongerThanOneDayUsesExactElapsedTime() {
        assertEquals(
            1_465,
            routeDurationMinutes(
                option(departureMillis = 0L, arrivalMillis = 87_900_000L),
            ),
        )
    }

    @Test fun clockOnlyLegacyFallbackPreservesOvernightBehavior() {
        assertEquals(25, routeDurationMinutes(option()))
    }

    @Test fun incompleteOrReversedScheduleNeverInventsDuration() {
        assertNull(routeDurationMinutes(option(departureMillis = 1_000L)))
        assertNull(routeDurationMinutes(option(departureMillis = 2_000L, arrivalMillis = 1_000L)))
        assertNull(routeDurationMinutes(option(departure = "Planlı", arrival = "Planlı")))
    }

    @Test fun malformedClockOnlyLabelsNeverCreateFalseDurations() {
        val invalidClocks = listOf("25:00", "11:60", "09-30", "9:00", "09:00:99", "09:00bogus")
        for (clock in invalidClocks) {
            assertNull("Invalid clock: $clock", routeDurationMinutes(option(departure = clock, arrival = "10:00")))
        }
    }

    @Test fun isoClockWithSecondsPreservesLegacyOvernightFallback() {
        assertEquals(
            35,
            routeDurationMinutes(option(departure = "2026-10-11T23:50:15", arrival = "2026-10-12T00:25:59")),
        )
    }

    @Test fun exactDurationTakesPrecedenceOverMisleadingClockLabels() {
        assertEquals(
            90,
            routeDurationMinutes(
                option(departure = "08:00", arrival = "08:30", departureMillis = 0L, arrivalMillis = 5_400_000L),
            ),
        )
    }

    @Test fun overflowingTimestampSubtractionFailsClosed() {
        assertNull(
            routeDurationMinutes(
                option(departureMillis = Long.MIN_VALUE, arrivalMillis = Long.MAX_VALUE),
            ),
        )
    }

    @Test fun durationBeyondIntMinutesFailsClosed() {
        val excessiveMillis = (Int.MAX_VALUE.toLong() + 1L) * 60_000L
        assertNull(
            routeDurationMinutes(
                option(departureMillis = 0L, arrivalMillis = excessiveMillis),
            ),
        )
    }

    @Test fun undatedInvalidIsoClockFailsClosed() {
        assertNull(routeDurationMinutes(option(departure = "2026-10-11T25:10:00", arrival = "2026-10-12T00:15:00")))
    }
}
