package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionRealtimeJourneyOverlayTest {
    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val date = GtfsServiceDate(2026, 10, 4)

    @Test
    fun freshMatchedOverlayAppliesOnlyMatchedTripTimingPlatformAndCancellation() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip("T1"),
                    startDate = "20261004",
                    startTime = "08:00:00",
                    cancelled = true,
                    scheduleRelationship = "CANCELED",
                    stopUpdates = listOf(
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 1,
                            scheduledStopId = "A",
                            realtimeStopId = "A",
                            assignedStopId = null,
                            platformCode = "5",
                            arrivalDelaySeconds = null,
                            departureDelaySeconds = 120,
                            arrivalTimeEpochSeconds = null,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 2,
                            scheduledStopId = "B",
                            realtimeStopId = "B",
                            assignedStopId = null,
                            platformCode = "7",
                            arrivalDelaySeconds = null,
                            departureDelaySeconds = null,
                            arrivalTimeEpochSeconds = at("08:32:00") / 1_000L,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                    ),
                    sourceTimestampEpochSeconds = at("07:59:00") / 1_000L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 30,
            provenance = provenance(),
        )

        val result = overlay(matched).enrich(listOf(option())).single()

        assertEquals(RouteRealtimeState.FRESH_MATCHED, result.realtime?.state)
        assertEquals(listOf("T1"), result.realtime?.matchedTripIds)
        assertEquals(listOf("T1"), result.realtime?.cancelledTripIds)
        assertEquals("2026-10-04T08:02:00+02:00", result.stops.first().departure)
        assertEquals("5", result.stops.first().platform)
        assertEquals("2026-10-04T08:32:00+02:00", result.stops.last().arrival)
        assertEquals("7", result.stops.last().platform)
        assertEquals("1", result.stops.first().plannedPlatform)
        assertEquals("2", result.stops.last().plannedPlatform)
    }

    @Test
    fun blankRealtimePlatformPreservesKnownPlatformWhileNonblankIsNormalized() {
        fun matchedPlatform(platform: String) = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip("T1"),
                    startDate = "20261004",
                    startTime = "08:00:00",
                    cancelled = false,
                    scheduleRelationship = "SCHEDULED",
                    stopUpdates = listOf(
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 2,
                            scheduledStopId = "B",
                            realtimeStopId = "B",
                            assignedStopId = null,
                            platformCode = platform,
                            arrivalDelaySeconds = null,
                            departureDelaySeconds = null,
                            arrivalTimeEpochSeconds = at("08:32:00") / 1_000L,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                    ),
                    sourceTimestampEpochSeconds = at("07:59:00") / 1_000L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 30,
            provenance = provenance(),
        )
        val known = option().let { original ->
            original.copy(stops = original.stops.dropLast(1) +
                original.stops.last().copy(platform = "6"))
        }

        for (blank in listOf("", " ", "\t")) {
            val actual = overlay(matchedPlatform(blank)).enrich(listOf(known)).single()
            assertEquals("blank=$blank", "6", actual.stops.last().platform)
            assertEquals("2026-10-04T08:32:00+02:00", actual.stops.last().arrival)
        }
        val nonblank = overlay(matchedPlatform(" 8 ")).enrich(listOf(known)).single()
        assertEquals("8", nonblank.stops.last().platform)
    }

    @Test
    fun ambiguousLoopVisitsCannotInventRouteStopTimesOrPlatforms() {
        fun update(sequence: Int, stopId: String, platform: String) =
            GermanyRealtimeMatchedStopUpdate(
                stopSequence = sequence,
                scheduledStopId = stopId,
                realtimeStopId = stopId,
                assignedStopId = null,
                platformCode = platform,
                arrivalDelaySeconds = 120,
                departureDelaySeconds = 120,
                arrivalTimeEpochSeconds = null,
                departureTimeEpochSeconds = null,
                scheduleRelationship = "SCHEDULED",
                pickupType = null,
                dropOffType = null,
                stopHeadsign = null,
            )

        fun matched(updates: List<GermanyRealtimeMatchedStopUpdate>) =
            GermanyRealtimeOverlayResult.Matched(
                tripOverlays = listOf(
                    GermanyRealtimeTripOverlay(
                        staticTrip = trip("T1"),
                        startDate = "20261004",
                        startTime = null,
                        cancelled = false,
                        scheduleRelationship = "SCHEDULED",
                        stopUpdates = updates,
                        sourceTimestampEpochSeconds = at("07:59:00") / 1_000L,
                    ),
                ),
                serviceAlerts = emptyList(),
                freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
                ageSeconds = 30,
                provenance = provenance(),
            )

        // Multiple realtime visits to A must not pick whichever update came first.
        val visits = listOf(update(1, "A", "8"), update(5, "A", "9"), update(2, "B", "4"))
        for (ordered in listOf(visits, visits.reversed())) {
            val result = overlay(matched(ordered)).enrich(listOf(option())).single()
            assertNull(result.stops.first().departure)
            assertNull(result.stops.first().platform)
            assertEquals("1", result.stops.first().plannedPlatform)
            assertEquals("2026-10-04T08:32:00+02:00", result.stops.last().arrival)
            assertEquals("4", result.stops.last().platform)
        }

        // Even one provider update cannot identify a visit when the planned
        // route itself contains the same stop_id more than once.
        val loopRoute = option().let { original ->
            original.copy(stops = original.stops + original.stops.first().copy(name = "Lohne return"))
        }
        val loop = overlay(matched(listOf(update(5, "A", "9"), update(2, "B", "4"))))
            .enrich(listOf(loopRoute)).single()
        assertNull(loop.stops[0].departure)
        assertNull(loop.stops[0].platform)
        assertNull(loop.stops[2].departure)
        assertNull(loop.stops[2].platform)
        assertEquals("2026-10-04T08:32:00+02:00", loop.stops[1].arrival)
        assertEquals("4", loop.stops[1].platform)
    }

    @Test
    fun reversedProviderOrderUsesSelectedLegOrderAtTransferStop() {
        val selected = option().copy(
            tripIds = listOf("T1", "T2"),
            transfers = 1,
            stops = listOf(
                option().stops.first(),
                RouteStop(
                    id = "B",
                    name = "Transfer",
                    plannedArrival = "2026-10-04T08:15:00+02:00",
                    plannedDeparture = "2026-10-04T08:17:00+02:00",
                    plannedPlatform = "1",
                ),
                RouteStop(
                    id = "C",
                    name = "Destination",
                    plannedArrival = "2026-10-04T08:45:00+02:00",
                    plannedPlatform = "3",
                ),
            ),
        )

        fun transferUpdate(arrival: String, departure: String, platform: String) =
            GermanyRealtimeMatchedStopUpdate(
                stopSequence = 2,
                scheduledStopId = "B",
                realtimeStopId = "B",
                assignedStopId = null,
                platformCode = platform,
                arrivalDelaySeconds = null,
                departureDelaySeconds = null,
                arrivalTimeEpochSeconds = at(arrival) / 1_000L,
                departureTimeEpochSeconds = at(departure) / 1_000L,
                scheduleRelationship = "SCHEDULED",
                pickupType = null,
                dropOffType = null,
                stopHeadsign = null,
            )

        fun tripOverlay(id: String, stop: GermanyRealtimeMatchedStopUpdate) =
            GermanyRealtimeTripOverlay(
                staticTrip = trip(id),
                startDate = "20261004",
                startTime = null,
                cancelled = false,
                scheduleRelationship = "SCHEDULED",
                stopUpdates = listOf(stop),
                sourceTimestampEpochSeconds = at("07:59:00") / 1_000L,
            )

        // GTFS-RT contains the departing T2 update before the arriving T1.
        // Arrival must still come from T1 and departure from T2.
        val reversedFeed = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                tripOverlay("T2", transferUpdate("08:09:00", "08:19:00", "4")),
                tripOverlay("T1", transferUpdate("08:16:00", "08:39:00", "2")),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 30,
            provenance = provenance(),
        )

        val result = overlay(reversedFeed).enrich(listOf(selected)).single()
        val transfer = result.stops[1]

        assertEquals("2026-10-04T08:16:00+02:00", transfer.arrival)
        assertEquals("2026-10-04T08:19:00+02:00", transfer.departure)
        assertEquals("4", transfer.platform)
        assertEquals(listOf("T1", "T2"), result.realtime?.matchedTripIds)
    }

    @Test
    fun invalidAbsoluteRealtimeEpochsUseScheduledDelayInsteadOfBogusDates() {
        for (invalidEpoch in listOf(
            0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
            1L, 946_684_799L, 253_402_300_800L,
        )) {
            val matched = GermanyRealtimeOverlayResult.Matched(
                tripOverlays = listOf(
                    GermanyRealtimeTripOverlay(
                        staticTrip = trip("T1"),
                        startDate = "20261004",
                        startTime = "08:00:00",
                        cancelled = false,
                        scheduleRelationship = "SCHEDULED",
                        stopUpdates = listOf(
                            GermanyRealtimeMatchedStopUpdate(
                                stopSequence = 2,
                                scheduledStopId = "B",
                                realtimeStopId = "B",
                                assignedStopId = null,
                                platformCode = null,
                                arrivalDelaySeconds = 90,
                                departureDelaySeconds = null,
                                arrivalTimeEpochSeconds = invalidEpoch,
                                departureTimeEpochSeconds = null,
                                scheduleRelationship = "SCHEDULED",
                                pickupType = null,
                                dropOffType = null,
                                stopHeadsign = null,
                            ),
                        ),
                        sourceTimestampEpochSeconds = at("07:59:00") / 1_000L,
                    ),
                ),
                serviceAlerts = emptyList(),
                freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
                ageSeconds = 30,
                provenance = provenance(),
            )

            val result = overlay(matched).enrich(listOf(option())).single()
            assertEquals(
                "epoch=$invalidEpoch",
                "2026-10-04T08:31:30+02:00",
                result.stops.last().arrival,
            )
            assertEquals(
                "2026-10-04T08:30:00+02:00",
                result.stops.last().plannedArrival,
            )
        }
    }

    @Test
    fun scheduledDelayFallbackRejectsTrailingGarbageWithoutLosingValidIsoTimes() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip("T1"),
                    startDate = "20261004",
                    startTime = "08:00:00",
                    cancelled = false,
                    scheduleRelationship = "SCHEDULED",
                    stopUpdates = listOf(
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 2,
                            scheduledStopId = "B",
                            realtimeStopId = "B",
                            assignedStopId = null,
                            platformCode = null,
                            arrivalDelaySeconds = 90,
                            departureDelaySeconds = null,
                            arrivalTimeEpochSeconds = null,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                    ),
                    sourceTimestampEpochSeconds = at("07:59:00") / 1_000L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 30,
            provenance = provenance(),
        )
        fun withPlannedTime(value: String) = option().let { route ->
            route.copy(
                stops = route.stops.dropLast(1) + route.stops.last().copy(plannedArrival = value),
            )
        }

        val valid = withPlannedTime("2026-10-04T08:30:00+02:00")
        val malformed = withPlannedTime("2026-10-04T08:30:00+0200trailing")
        val results = overlay(matched).enrich(listOf(valid, malformed))
        assertEquals("2026-10-04T08:31:30+02:00", results[0].stops.last().arrival)
        assertNull(results[1].stops.last().arrival)
        assertEquals("2026-10-04T08:30:00+0200trailing", results[1].stops.last().plannedArrival)
    }

    @Test
    fun frequencyTripRealtimeFailsClosedWithoutRouteInstanceIdentity() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip("T1"),
                    startDate = "20261004",
                    startTime = "08:00:00",
                    cancelled = false,
                    scheduleRelationship = "SCHEDULED",
                    stopUpdates = listOf(
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 2,
                            scheduledStopId = "B",
                            realtimeStopId = "B",
                            assignedStopId = null,
                            platformCode = "9",
                            arrivalDelaySeconds = 600,
                            departureDelaySeconds = null,
                            arrivalTimeEpochSeconds = null,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                    ),
                    sourceTimestampEpochSeconds = at("07:59:00") / 1_000L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 30,
            provenance = provenance(),
        )

        val result = overlay(matched, isFrequencyTrip = { true })
            .enrich(listOf(option()))
            .single()

        assertEquals(RouteRealtimeState.FRESH_NO_MATCH, result.realtime?.state)
        assertNull(result.stops.last().arrival)
        assertNull(result.stops.last().platform)
        assertEquals("2", result.stops.last().plannedPlatform)
    }

    @Test
    fun staleOverlayPreservesStaticTruthWithoutApplyingRealtimeDeltas() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip("T1"),
                    startDate = null,
                    startTime = null,
                    cancelled = true,
                    scheduleRelationship = "SCHEDULED",
                    stopUpdates = listOf(
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 2,
                            scheduledStopId = "B",
                            realtimeStopId = "B",
                            assignedStopId = null,
                            platformCode = "9",
                            arrivalDelaySeconds = 600,
                            departureDelaySeconds = null,
                            arrivalTimeEpochSeconds = null,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                    ),
                    sourceTimestampEpochSeconds = 1L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.STALE,
            ageSeconds = 600,
            provenance = provenance(),
        )

        val result = overlay(matched).enrich(listOf(option())).single()

        assertEquals(RouteRealtimeState.STALE, result.realtime?.state)
        assertNull(result.stops.last().arrival)
        assertNull(result.stops.last().platform)
        assertEquals("2", result.stops.last().plannedPlatform)
        assertEquals(emptyList<String>(), result.realtime?.cancelledTripIds)
    }

    @Test
    fun noMatchAndUnavailableRemainTypedAndLeaveStaticStopsUntouched() {
        val noMatch = GermanyRealtimeOverlayResult.NoMatch(
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10,
            provenance = provenance(),
        )
        val noMatchResult = overlay(noMatch).enrich(listOf(option())).single()
        assertEquals(RouteRealtimeState.FRESH_NO_MATCH, noMatchResult.realtime?.state)
        assertNull(noMatchResult.stops.first().departure)

        val unavailable = GermanyRealtimeOverlayResult.Unavailable(
            reason = GermanyRealtimeUnavailableReason.NETWORK,
            detail = "https://provider.example/?token=SECRET&lat=52.665",
        )
        val unavailableResult = overlay(unavailable).enrich(listOf(option())).single()
        assertEquals(RouteRealtimeState.UNAVAILABLE, unavailableResult.realtime?.state)
        assertEquals("NETWORK", unavailableResult.realtime?.unavailableReason)
        assertNull(unavailableResult.stops.last().arrival)
    }

    @Test
    fun thrownProviderErrorsAreRedactedFromRouteReadiness() {
        val realtime = ProductionRealtimeJourneyOverlay(
            fetch = {
                throw IllegalStateException(
                    "Bearer SECRET_TOKEN https://provider.example/?lat=52.665&lon=8.237"
                )
            },
            match = { _, _ -> error("matcher should not run") },
            nowEpochSeconds = { at("08:00:00") / 1_000L },
        )

        val result = realtime.enrich(listOf(option())).single()

        assertEquals(RouteRealtimeState.UNAVAILABLE, result.realtime?.state)
        assertEquals("NETWORK", result.realtime?.unavailableReason)
        assertNull(result.stops.first().departure)
    }

    @Test(expected = AssertionError::class)
    fun fatalMatcherErrorsPropagateInsteadOfBeingMaskedAsNetworkFailures() {
        val realtime = ProductionRealtimeJourneyOverlay(
            fetch = {
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "unused",
                )
            },
            match = { _, _ -> throw AssertionError("fatal matcher state") },
            nowEpochSeconds = { at("08:00:00") / 1_000L },
        )

        realtime.enrich(listOf(option()))
    }

    @Test
    fun preInterruptedRealtimeWorkDoesNotDispatchProviderRequests() {
        var fetchCount = 0
        var matchCount = 0
        val realtime = ProductionRealtimeJourneyOverlay(
            fetch = {
                fetchCount++
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "unused",
                )
            },
            match = { _, _ ->
                matchCount++
                GermanyRealtimeOverlayResult.Unavailable(
                    reason = GermanyRealtimeUnavailableReason.NETWORK,
                    detail = null,
                )
            },
            nowEpochSeconds = { at("08:00:00") / 1_000L },
        )

        var interrupted = false
        try {
            Thread.currentThread().interrupt()
            try {
                realtime.enrich(listOf(option()))
            } catch (_: InterruptedException) {
                interrupted = true
                assertTrue(Thread.currentThread().isInterrupted)
            }
        } finally {
            // A cancelled test must not taint later JUnit tests.
            Thread.interrupted()
        }

        assertTrue(interrupted)
        assertEquals(0, fetchCount)
        assertEquals(0, matchCount)
    }

    @Test
    fun cancelledDuringFetchSkipsMatcherAndPreservesInterruption() {
        var fetchCount = 0
        var matchCount = 0
        val realtime = ProductionRealtimeJourneyOverlay(
            fetch = {
                fetchCount++
                Thread.currentThread().interrupt()
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "cancelled",
                )
            },
            match = { _, _ ->
                matchCount++
                error("match must not run after cancellation")
            },
            nowEpochSeconds = { at("08:00:00") / 1_000L },
        )

        var interrupted = false
        try {
            try {
                realtime.enrich(listOf(option()))
            } catch (_: InterruptedException) {
                interrupted = true
                assertTrue(Thread.currentThread().isInterrupted)
            }
        } finally {
            Thread.interrupted()
        }

        assertTrue(interrupted)
        assertEquals(1, fetchCount)
        assertEquals(0, matchCount)
    }

    @Test
    fun cancelledDuringMatcherSkipsRealtimeStateUpdateAndPreservesInterruption() {
        var fetchCount = 0
        var matchCount = 0
        val realtime = ProductionRealtimeJourneyOverlay(
            fetch = {
                fetchCount++
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "unused",
                )
            },
            match = { _, _ ->
                matchCount++
                Thread.currentThread().interrupt()
                GermanyRealtimeOverlayResult.Unavailable(
                    reason = GermanyRealtimeUnavailableReason.NETWORK,
                    detail = null,
                )
            },
            nowEpochSeconds = { at("08:00:00") / 1_000L },
        )

        var interrupted = false
        try {
            try {
                realtime.enrich(listOf(option()))
            } catch (_: InterruptedException) {
                interrupted = true
                assertTrue(Thread.currentThread().isInterrupted)
            }
        } finally {
            // Do not leak the interrupt flag into the JUnit runner.
            Thread.interrupted()
        }
        assertTrue(interrupted)
        assertEquals(1, fetchCount)
        assertEquals(1, matchCount)
    }

    @Test
    fun interruptedRealtimeFetchPreservesThreadCancellation() {
        val realtime = ProductionRealtimeJourneyOverlay(
            fetch = { throw InterruptedException("request cancelled") },
            match = { _, _ -> error("matcher should not run") },
            nowEpochSeconds = { at("08:00:00") / 1_000L },
        )

        var interruptionPropagated = false
        try {
            realtime.enrich(listOf(option()))
        } catch (_: InterruptedException) {
            interruptionPropagated = true
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            // Do not leak the test's interruption flag into the JUnit runner.
            Thread.interrupted()
        }
        assertTrue(interruptionPropagated)
    }

    private fun overlay(
        result: GermanyRealtimeOverlayResult,
        isFrequencyTrip: (String) -> Boolean = { false },
    ) =
        ProductionRealtimeJourneyOverlay(
            fetch = {
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "unused",
                )
            },
            match = { _, _ -> result },
            nowEpochSeconds = { at("08:00:00") / 1_000L },
            agencyTimeZone = zone,
            isFrequencyTrip = isFrequencyTrip,
        )

    private fun option() = RouteOption(
        id = "route",
        origin = MapPoint(52.665, 8.237, "Lohne"),
        destination = MapPoint(52.700, 8.400, "Achim"),
        line = "RE 1",
        direction = "Achim",
        departure = "08:00",
        arrival = "08:30",
        walkingMinutes = 0,
        transfers = 0,
        tripIds = listOf("T1"),
        stops = listOf(
            RouteStop(
                id = "A",
                name = "Lohne",
                plannedDeparture = "2026-10-04T08:00:00+02:00",
                plannedPlatform = "1",
            ),
            RouteStop(
                id = "B",
                name = "Achim",
                plannedArrival = "2026-10-04T08:30:00+02:00",
                plannedPlatform = "2",
            ),
        ),
    )

    private fun trip(id: String) = GtfsScheduleTrip(
        id = id,
        routeId = "R1",
        serviceId = "S",
        headsign = "Achim",
        directionId = 0,
        shapeId = null,
        wheelchairAccessible = null,
        bikesAllowed = null,
    )

    private fun provenance() = GermanyRealtimeOverlayProvenance(
        source = GermanyGtfsRealtimeProvenance.PROVIDER,
        license = GermanyGtfsRealtimeProvenance.LICENSE,
        gtfsRealtimeVersion = "2.0",
        feedVersion = "test",
        feedTimestampEpochSeconds = at("07:59:30") / 1_000L,
        fetchedAtEpochSeconds = at("07:59:35") / 1_000L,
    )

    private fun at(raw: String): Long =
        GtfsServiceTime.parse(raw).resolve(date, zone).epochMillis
}
