package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStopProgressTest {
    @Test fun unknownMinusZeroOffsetCannotBeUsedForStopProgress() {
        val unknownOffset = "2026-09-26T08:10:00-00:00"
        // RFC 3339 distinguishes unknown -00:00 from known UTC +00:00/Z.
        assertNull(unknownOffset.toTransitEpochSecondsOrNull())
        assertNull("2026-09-26T08:10:00-0000".toTransitEpochSecondsOrNull())
        assertEquals(
            "2026-09-26T08:10:00Z".toTransitEpochSecondsOrNull(),
            "2026-09-26T08:10:00+00:00".toTransitEpochSecondsOrNull(),
        )

        val now = "2026-09-26T08:10:00Z".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(
            listOf(RouteStop(id = "unverified", name = "Unknown", plannedDeparture = unknownOffset)),
            now,
            trustRealtime = false,
        )
        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.current)
        assertNull(progress.next)
        assertNull(progress.remainingStops)
    }

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

    @Test fun adjacentScheduledStopWindowsDoNotInventCurrentStopAtSharedSecond() {
        val touching = listOf(
            RouteStop(
                id = "a", name = "Lohne",
                plannedArrival = "2026-09-26T08:00:00+02:00",
                plannedDeparture = "2026-09-26T08:10:00+02:00",
            ),
            RouteStop(
                id = "b", name = "Diepholz",
                plannedArrival = "2026-09-26T08:10:00+02:00",
                plannedDeparture = "2026-09-26T08:20:00+02:00",
            ),
        )
        val boundary = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val tracker = RouteStopProgressTracker()

        val ambiguous = tracker.resolve(touching, boundary, trustRealtime = false)
        assertFalse(ambiguous.resolvedByProviderTime)
        assertNull(ambiguous.current)
        assertNull(ambiguous.previous)
        assertNull(ambiguous.next)
        assertNull(ambiguous.remainingStops)

        assertEquals("Lohne", tracker.resolve(touching, boundary - 1, false).current?.name)
        assertEquals("Diepholz", tracker.resolve(touching, boundary + 1, false).current?.name)
    }

    @Test fun ambiguousRealtimeBoundaryFallsBackToDistinctPlannedStopTimes() {
        val observations = listOf(
            RouteStop(
                id = "a", name = "Lohne",
                departure = "2026-09-26T08:10:00+02:00",
                plannedArrival = "2026-09-26T07:55:00+02:00",
                plannedDeparture = "2026-09-26T08:00:00+02:00",
            ),
            RouteStop(
                id = "b", name = "Diepholz",
                arrival = "2026-09-26T08:10:00+02:00",
                plannedArrival = "2026-09-26T08:20:00+02:00",
                plannedDeparture = "2026-09-26T08:30:00+02:00",
            ),
        )
        val boundary = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!

        val progress = RouteStopProgressTracker().resolve(observations, boundary)
        assertTrue(progress.resolvedByProviderTime)
        assertEquals(TransitTimingBasis.SCHEDULED, progress.timingBasis)
        assertEquals("Lohne", progress.previous?.name)
        assertNull(progress.current)
        assertEquals("Diepholz", progress.next?.name)
        assertEquals(1, progress.remainingStops)
    }

    @Test fun betweenStopsDoesNotInventCurrentStop() {
        val now = 1_790_403_600L
        val progress = RouteStopProgressTracker().resolve(stops, now)

        assertTrue(progress.resolvedByProviderTime)
        assertEquals(TransitTimingBasis.REALTIME, progress.timingBasis)
        assertEquals("Diepholz", progress.previous?.name)
        assertNull(progress.current)
        assertEquals("Bremen Hbf", progress.next?.name)
        assertEquals(1, progress.remainingStops)
    }

    @Test fun dwellWindowExposesActualCurrentStop() {
        val now = 1_790_403_330L
        val progress = RouteStopProgressTracker().resolve(stops, now)

        assertEquals("Lohne", progress.previous?.name)
        assertEquals("Diepholz", progress.current?.name)
        assertEquals("Bremen Hbf", progress.next?.name)
        assertEquals(1, progress.remainingStops)
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
        assertNull(progress.remainingStops)
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

    @Test fun conflictingRealtimeStopOrderFallsBackToCoherentSchedule() {
        val observations = listOf(
            RouteStop(
                id = "a", name = "Lohne",
                departure = "2026-09-26T08:40:00+02:00",
                plannedDeparture = "2026-09-26T08:00:00+02:00",
            ),
            RouteStop(
                id = "b", name = "Diepholz",
                arrival = "2026-09-26T08:25:00+02:00",
                plannedArrival = "2026-09-26T08:20:00+02:00",
            ),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!

        val progress = RouteStopProgressTracker().resolve(observations, now)

        assertTrue(progress.resolvedByProviderTime)
        assertEquals(TransitTimingBasis.SCHEDULED, progress.timingBasis)
        assertEquals("Lohne", progress.previous?.name)
        assertNull(progress.current)
        assertEquals("Diepholz", progress.next?.name)
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

    @Test fun impossibleUtcOffsetsCannotCreatePhantomStopProgress() {
        // Real UTC civil offsets cannot exceed fourteen hours; +14 permits only :00.
        for (invalid in listOf("+14:01", "-14:01", "+15:00", "-23:00")) {
            val timestamp = "2026-09-26T08:10:00$invalid"
            assertNull("Invalid offset $invalid must not be accepted", timestamp.toTransitEpochSecondsOrNull())
            val route = listOf(RouteStop(id = "a", name = "Unverified", plannedDeparture = timestamp))
            val progress = RouteStopProgressTracker().resolve(route, 1_000L, trustRealtime = false)
            assertFalse(progress.resolvedByProviderTime)
            assertNull(progress.current)
            assertNull(progress.next)
        }
        assertEquals(
            "2026-09-25T18:10:00Z".toTransitEpochSecondsOrNull(),
            "2026-09-26T08:10:00+14:00".toTransitEpochSecondsOrNull(),
        )
        assertEquals(
            "2026-09-26T20:10:00Z".toTransitEpochSecondsOrNull(),
            "2026-09-26T08:10:00-12:00".toTransitEpochSecondsOrNull(),
        )
    }

    @Test fun negativeCivilUtcOffsetBoundsFailClosedOrUseVerifiedSchedule() {
        val validNegativeEdge = "2026-09-26T08:10:00-12:00"
        val validUtcEquivalent = "2026-09-26T20:10:00Z"
        assertEquals(validUtcEquivalent.toTransitEpochSecondsOrNull(),
            validNegativeEdge.toTransitEpochSecondsOrNull())

        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        for (invalid in listOf("-12:01", "-13:00", "-13:30", "-14:00")) {
            val malformed = "2026-09-26T08:10:00" + invalid
            assertNull("Provider offset " + invalid + " must be rejected",
                malformed.toTransitEpochSecondsOrNull())

            val onlyMalformed = RouteStopProgressTracker().resolve(
                listOf(RouteStop(id = "a", name = "Unverified", departure = malformed)),
                now,
            )
            assertFalse(onlyMalformed.resolvedByProviderTime)
            assertNull(onlyMalformed.current)
            assertNull(onlyMalformed.next)

            val withSchedule = RouteStopProgressTracker().resolve(
                listOf(RouteStop(
                    id = "a", name = "Lohne",
                    departure = malformed,
                    plannedDeparture = "2026-09-26T08:00:00+02:00",
                )),
                now,
            )
            assertTrue(withSchedule.resolvedByProviderTime)
            assertEquals(TransitTimingBasis.SCHEDULED, withSchedule.timingBasis)
            assertEquals("Lohne", withSchedule.previous?.name)
            assertNull(withSchedule.current)
        }
    }

    @Test fun untimedIntermediateStopCannotBeSkippedAsNext() {
        val observations = listOf(
            RouteStop(id = "a", name = "First", plannedDeparture = "2026-09-26T08:00:00+02:00"),
            RouteStop(id = "b", name = "Untimed intermediate"),
            RouteStop(id = "c", name = "Third", plannedArrival = "2026-09-26T08:20:00+02:00"),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(observations, now, trustRealtime = false)

        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.previous)
        assertNull(progress.current)
        assertNull(progress.next)
    }

    @Test fun untimedPrefixCannotBeHiddenByLaterTimedStop() {
        val observations = listOf(
            RouteStop(id = "a", name = "Untimed first"),
            RouteStop(id = "b", name = "Second", plannedArrival = "2026-09-26T08:20:00+02:00"),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(observations, now, trustRealtime = false)

        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.next)
    }

    @Test fun untimedSuffixCannotBeHiddenByEarlierTimedStop() {
        val observations = listOf(
            RouteStop(id = "a", name = "First", plannedDeparture = "2026-09-26T08:00:00+02:00"),
            RouteStop(id = "b", name = "Untimed last"),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(observations, now, trustRealtime = false)

        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.previous)
    }

    @Test fun remainingStopsAreKnownBeforeFirstAndAfterLastTimedStop() {
        val tracker = RouteStopProgressTracker()
        val before = "2026-09-26T07:50:00+02:00".toTransitEpochSecondsOrNull()!!
        val after = "2026-09-26T09:00:00+02:00".toTransitEpochSecondsOrNull()!!

        val waiting = tracker.resolve(stops, before)
        assertTrue(waiting.resolvedByProviderTime)
        assertNull(waiting.current)
        assertEquals("Lohne", waiting.next?.name)
        assertEquals(3, waiting.remainingStops)

        val finished = tracker.resolve(stops, after)
        assertTrue(finished.resolvedByProviderTime)
        assertNull(finished.next)
        assertEquals(0, finished.remainingStops)
    }

    @Test fun remainingStopsUseOrderedOccurrencesNotRepeatedStopIds() {
        val loop = listOf(
            RouteStop(id = "a", name = "Loop", plannedDeparture = "2026-09-26T08:00:00+02:00"),
            RouteStop(id = "b", name = "Middle", plannedDeparture = "2026-09-26T08:15:00+02:00"),
            RouteStop(id = "a", name = "Loop", plannedArrival = "2026-09-26T08:30:00+02:00"),
        )
        val now = "2026-09-26T08:20:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(loop, now, trustRealtime = false)

        assertTrue(progress.resolvedByProviderTime)
        assertNull(progress.current)
        assertEquals("Middle", progress.previous?.name)
        assertEquals("Loop", progress.next?.name)
        assertEquals(1, progress.remainingStops)
    }

    @Test fun untimedGapLeavesRemainingStopsUnknown() {
        val route = listOf(
            RouteStop(id = "a", name = "Known", plannedDeparture = "2026-09-26T08:00:00+02:00"),
            RouteStop(id = "b", name = "Untimed"),
            RouteStop(id = "c", name = "Known", plannedArrival = "2026-09-26T08:30:00+02:00"),
        )
        val now = "2026-09-26T08:10:00+02:00".toTransitEpochSecondsOrNull()!!
        val progress = RouteStopProgressTracker().resolve(route, now, trustRealtime = false)

        assertFalse(progress.resolvedByProviderTime)
        assertNull(progress.remainingStops)
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

    @Test fun unsetOrResetDeviceClockCannotInventScheduledStopProgress() {
        val route = listOf(
            RouteStop(
                id = "b", name = "Diepholz",
                plannedArrival = "2026-09-26T08:10:00+02:00",
                plannedDeparture = "2026-09-26T08:11:00+02:00",
            ),
        )
        for (untrustedNow in listOf(Long.MIN_VALUE, -1L, 0L, 1_000L,
                946_684_799L, Long.MAX_VALUE)) {
            val result = RouteStopProgressTracker().resolve(route, untrustedNow, trustRealtime = false)
            assertFalse("Clock $untrustedNow must fail closed", result.resolvedByProviderTime)
            assertNull(result.previous)
            assertNull(result.current)
            assertNull(result.next)
            assertNull(result.remainingStops)
        }
    }

    @Test fun firstSupportedDeviceClockInstantStillResolvesVerifiedSchedule() {
        val route = listOf(RouteStop(
            id = "b", name = "Verified",
            plannedArrival = "2000-01-01T00:00:00Z",
            plannedDeparture = "2000-01-01T00:01:00Z",
        ))
        val result = RouteStopProgressTracker().resolve(
            route, 946_684_800L, trustRealtime = false,
        )
        assertTrue(result.resolvedByProviderTime)
        assertEquals("Verified", result.current?.name)
        assertEquals(0, result.remainingStops)
    }
}
