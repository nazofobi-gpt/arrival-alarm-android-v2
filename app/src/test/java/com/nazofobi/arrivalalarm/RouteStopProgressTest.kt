package com.nazofobi.arrivalalarm

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStopProgressTest {
    private val stops = listOf(
        RouteStop(
            id = "a",
            name = "Lohne",
            departure = "2026-09-26T08:00:00+02:00",
            plannedDeparture = "2026-09-26T07:58:00+02:00",
        ),
        RouteStop(
            id = "b",
            name = "Diepholz",
            arrival = "2026-09-26T08:15:00+02:00",
            departure = "2026-09-26T08:16:00+02:00",
            plannedArrival = "2026-09-26T08:13:00+02:00",
            plannedDeparture = "2026-09-26T08:14:00+02:00",
        ),
        RouteStop(
            id = "c",
            name = "Bremen Hbf",
            arrival = "2026-09-26T08:45:00+02:00",
            plannedArrival = "2026-09-26T08:43:00+02:00",
        ),
    )

    @Test fun betweenStopsDoesNotInventCurrentStop() {
        val now = OffsetDateTime.parse("2026-09-26T08:20:00+02:00").toEpochSecond()
        val progress = RouteStopProgressTracker().resolve(stops, now)

        assertTrue(progress.resolvedByProviderTime)
        assertEquals(TransitTimingBasis.REALTIME, progress.timingBasis)
        assertEquals("Diepholz", progress.previous?.name)
        assertNull(progress.current)
        assertEquals("Bremen Hbf", progress.next?.name)
    }

    @Test fun dwellWindowExposesActualCurrentStop() {
        val now = OffsetDateTime.parse("2026-09-26T08:15:30+02:00").toEpochSecond()
        val progress = RouteStopProgressTracker().resolve(stops, now)

        assertEquals("Lohne", progress.previous?.name)
        assertEquals("Diepholz", progress.current?.name)
        assertEquals("Bremen Hbf", progress.next?.name)
    }

    @Test fun missingProviderTimesFailsUnknownInsteadOfGuessing() {
        val progress = RouteStopProgressTracker().resolve(
            listOf(RouteStop(id = "x", name = "Unknown")),
            1_000L,
        )

        assertEquals(false, progress.resolvedByProviderTime)
        assertNull(progress.previous)
        assertNull(progress.current)
        assertNull(progress.next)
    }
}
