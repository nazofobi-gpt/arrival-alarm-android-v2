package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val now = 1_790_403_600L
        val progress = RouteStopProgressTracker().resolve(stops, now)

        assertTrue(progress.resolvedByProviderTime)
        assertEquals(TransitTimingBasis.REALTIME, progress.timingBasis)
        assertEquals("Diepholz", progress.previous?.name)
        assertNull(progress.current)
        assertEquals("Bremen Hbf", progress.next?.name)
    }

    @Test fun dwellWindowExposesActualCurrentStop() {
        val now = 1_790_403_330L
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

    @Test fun staleRealtimeTimesFallBackToScheduledStopSequence() {
        val observations = listOf(
            RouteStop(
                id = "a", name = "Lohne",
                departure = "2026-09-26T08:40:00+02:00",
                plannedDeparture = "2026-09-26T08:00:00+02:00",
            ),
            RouteStop(
                id = "b", name = "Diepholz",
                arrival = "2026-09-26T08:55:00+02:00",
                plannedArrival = "2026-09-26T08:15:00+02:00",
            ),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val trusted = RouteStopProgressTracker().resolve(observations, now)
        val fallback = RouteStopProgressTracker().resolve(observations, now, trustRealtime = false)

        assertEquals("Lohne", trusted.next?.name)
        assertNull(trusted.previous)
        assertEquals(TransitTimingBasis.REALTIME, trusted.timingBasis)
        assertEquals("Lohne", fallback.previous?.name)
        assertEquals("Diepholz", fallback.next?.name)
        assertEquals(TransitTimingBasis.SCHEDULED, fallback.timingBasis)
    }

    @Test fun realtimeWithoutScheduledFallbackFailsUnknownWhenNotTrusted() {
        val actualOnly = listOf(
            RouteStop(id = "trip", name = "Unknown schedule", arrival = "2026-09-26T08:10:00+02:00"),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(actualOnly, now, trustRealtime = false)
        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.current)
        assertNull(progress.next)
    }

    @Test fun invertedMixedRealtimeWindowFallsBackToCoherentSchedule() {
        val observations = listOf(
            RouteStop(
                id = "b",
                name = "Diepholz",
                arrival = "2026-09-26T08:40:00+02:00",
                plannedArrival = "2026-09-26T08:13:00+02:00",
                plannedDeparture = "2026-09-26T08:14:00+02:00",
            ),
        )
        val now = "2026-09-26T08:13:30+02:00".toTransitEpochSecondsOrNull()!!

        val progress = RouteStopProgressTracker().resolve(observations, now)

        assertEquals("Diepholz", progress.current?.name)
        assertEquals(TransitTimingBasis.SCHEDULED, progress.timingBasis)
        assertTrue(progress.resolvedByProviderTime)
    }

    @Test fun invertedWindowWithoutCoherentScheduleFailsUnknown() {
        val observations = listOf(
            RouteStop(
                id = "b",
                name = "Diepholz",
                arrival = "2026-09-26T08:40:00+02:00",
                departure = "2026-09-26T08:14:00+02:00",
            ),
        )
        val now = "2026-09-26T08:30:00+02:00".toTransitEpochSecondsOrNull()!!

        val progress = RouteStopProgressTracker().resolve(observations, now)

        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.previous)
        assertNull(progress.current)
        assertNull(progress.next)
    }

    @Test fun reversedStopTimelineDoesNotInventPreviousOrNext() {
        val inconsistent = listOf(
            RouteStop(id = "a", name = "First", departure = "2026-09-26T08:20:00+02:00"),
            RouteStop(id = "b", name = "Second", arrival = "2026-09-26T08:10:00+02:00"),
        )
        val now = "2026-09-26T08:15:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(inconsistent, now)
        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.previous)
        assertNull(progress.current)
        assertNull(progress.next)
    }

    @Test fun overlappingStopDwellWindowsDoNotInventVehiclePosition() {
        val overlapping = listOf(
            RouteStop(
                id = "a", name = "First",
                arrival = "2026-09-26T08:00:00+02:00",
                departure = "2026-09-26T08:15:00+02:00",
            ),
            RouteStop(
                id = "b", name = "Second",
                arrival = "2026-09-26T08:10:00+02:00",
                departure = "2026-09-26T08:20:00+02:00",
            ),
        )
        val now = "2026-09-26T08:12:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(overlapping, now)
        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.current)
    }

    @Test fun equalStopBoundaryAndUntimedIntermediateRemainUsable() {
        val coherent = listOf(
            RouteStop(id = "a", name = "First", departure = "2026-09-26T08:10:00+02:00"),
            RouteStop(id = "untimed", name = "Intermediate"),
            RouteStop(id = "c", name = "Last", arrival = "2026-09-26T08:10:00+02:00"),
        )
        val now = "2026-09-26T08:05:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(coherent, now)
        assertTrue(progress.resolvedByProviderTime)
        assertEquals("First", progress.next?.name)
    }

    @Test fun realtimeOnlyDepartureDoesNotAppearScheduled() {
        val departure = "2026-09-26T08:10:00+02:00"
        val actualOnly = listOf(RouteStop(id = "a", name = "Lohne", departure = departure))
        val now = departure.toTransitEpochSecondsOrNull()!!

        val live = RouteStopProgressTracker().resolve(actualOnly, now, trustRealtime = true)
        assertTrue(live.resolvedByProviderTime)
        assertEquals("Lohne", live.current?.name)
        assertEquals(TransitTimingBasis.REALTIME, live.timingBasis)

        val offline = RouteStopProgressTracker().resolve(actualOnly, now, trustRealtime = false)
        assertFalse(offline.resolvedByProviderTime)
        assertNull(offline.current)
        assertEquals(TransitTimingBasis.SCHEDULED, offline.timingBasis)
    }

    @Test fun realtimeOnlyArrivalKeepsLiveBasisBetweenStops() {
        val observations = listOf(
            RouteStop(id = "a", name = "Lohne", plannedDeparture = "2026-09-26T08:00:00+02:00"),
            RouteStop(id = "b", name = "Diepholz", arrival = "2026-09-26T08:15:00+02:00"),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(observations, now)

        assertTrue(progress.resolvedByProviderTime)
        assertEquals(TransitTimingBasis.REALTIME, progress.timingBasis)
        assertEquals("Lohne", progress.previous?.name)
        assertEquals("Diepholz", progress.next?.name)
    }

    @Test fun onlyFreshMatchedOnlineUncancelledRealtimeIsTrusted() {
        assertTrue(isTrustedRealtimeForJourney(RouteRealtimeState.FRESH_MATCHED, false))
        assertFalse(isTrustedRealtimeForJourney(RouteRealtimeState.STALE, false))
        assertFalse(isTrustedRealtimeForJourney(RouteRealtimeState.UNAVAILABLE, false))
        assertFalse(isTrustedRealtimeForJourney(RouteRealtimeState.FRESH_NO_MATCH, false))
        assertFalse(isTrustedRealtimeForJourney(null, false))
        assertFalse(isTrustedRealtimeForJourney(RouteRealtimeState.FRESH_MATCHED, true))
        assertFalse(
            isTrustedRealtimeForJourney(
                RouteRealtimeState.FRESH_MATCHED, false, cancelledTripIds = listOf("trip-1"),
            )
        )
    }
}
