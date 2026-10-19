package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionRealtimeStationOverlayTest {
    private val planned = Departure(
        tripId = "T1",
        line = "RE 1",
        direction = "Achim",
        scheduledEpochSeconds = 1_000L,
        platform = "1",
        routeId = "R1",
    )

    @Test
    fun freshMatchedAppliesDepartureDelayCancellationPlatformAndRelevantAlert() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip(),
                    startDate = null,
                    startTime = null,
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
                    ),
                    sourceTimestampEpochSeconds = 990L,
                ),
            ),
            serviceAlerts = listOf(
                GermanyRealtimeAlert(
                    entityId = "alert-1",
                    cause = "TECHNICAL_PROBLEM",
                    effect = "SIGNIFICANT_DELAYS",
                    header = "Verspätungen",
                    description = "Test",
                    selectors = listOf(
                        GermanyRealtimeAlertSelector(
                            agencyId = null,
                            routeId = "R1",
                            routeType = null,
                            directionId = null,
                            stopId = "A",
                            trip = null,
                            validity = GermanyRealtimeAlertSelectorValidity.VALID,
                        ),
                    ),
                    activePeriods = emptyList(),
                ),
            ),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )

        val result = overlay(matched).enrich("A", listOf(planned))

        assertEquals(StationRealtimeState.FRESH_MATCHED, result.realtimeState)
        assertEquals(1_120L, result.departures.single().realtimeEpochSeconds)
        assertEquals("5", result.departures.single().platform)
        assertEquals(true, result.departures.single().cancelled)
        assertEquals(listOf("alert-1"), result.alerts.map { it.id })
    }

    @Test
    fun loopStopWithTwoVisitsDoesNotUseAnArbitraryRealtimeUpdate() {
        fun update(sequence: Int, skipped: Boolean) = GermanyRealtimeMatchedStopUpdate(
            stopSequence = sequence,
            scheduledStopId = "A",
            realtimeStopId = "A",
            assignedStopId = null,
            platformCode = if (skipped) "9" else "5",
            arrivalDelaySeconds = null,
            departureDelaySeconds = 120,
            arrivalTimeEpochSeconds = null,
            departureTimeEpochSeconds = null,
            scheduleRelationship = if (skipped) "SKIPPED" else "SCHEDULED",
            pickupType = null,
            dropOffType = null,
            stopHeadsign = null,
        )

        fun result(updates: List<GermanyRealtimeMatchedStopUpdate>, cancelled: Boolean) =
            overlay(
                GermanyRealtimeOverlayResult.Matched(
                    tripOverlays = listOf(
                        GermanyRealtimeTripOverlay(
                            staticTrip = trip(),
                            startDate = null,
                            startTime = null,
                            cancelled = cancelled,
                            scheduleRelationship = if (cancelled) "CANCELED" else "SCHEDULED",
                            stopUpdates = updates,
                            sourceTimestampEpochSeconds = 990L,
                        ),
                    ),
                    serviceAlerts = emptyList(),
                    freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
                    ageSeconds = 10L,
                    provenance = provenance(),
                ),
            ).enrich("A", listOf(planned))

        val twoVisits = listOf(update(1, skipped = true), update(5, skipped = false))
        for (ordered in listOf(twoVisits, twoVisits.reversed())) {
            val ambiguous = result(ordered, cancelled = false)
            assertEquals(StationRealtimeState.FRESH_NO_MATCH, ambiguous.realtimeState)
            assertEquals(listOf(planned), ambiguous.departures)

            // A trip-wide cancellation remains valid even without a known stop_sequence.
            val globallyCancelled = result(ordered, cancelled = true)
            assertEquals(StationRealtimeState.FRESH_MATCHED, globallyCancelled.realtimeState)
            assertTrue(globallyCancelled.departures.single().cancelled)
            assertEquals("1", globallyCancelled.departures.single().platform)
            assertNull(globallyCancelled.departures.single().realtimeEpochSeconds)
        }

        // A sole matching station update is still safe with other stops in the trip.
        val unambiguous = result(
            listOf(update(1, skipped = false).copy(scheduledStopId = "B"), update(5, skipped = true)),
            cancelled = false,
        )
        assertEquals(StationRealtimeState.FRESH_MATCHED, unambiguous.realtimeState)
        assertTrue(unambiguous.departures.single().cancelled)
        assertEquals("9", unambiguous.departures.single().platform)
    }

    @Test
    fun reusedTripIdRealtimeDoesNotCancelAnotherRoute() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip(),
                    startDate = null,
                    startTime = null,
                    cancelled = true,
                    scheduleRelationship = "CANCELED",
                    stopUpdates = emptyList(),
                    sourceTimestampEpochSeconds = 990L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )
        val otherRoute = planned.copy(routeId = "R2", line = "RE 2")
        val result = overlay(matched).enrich("A", listOf(otherRoute, planned))

        assertEquals(StationRealtimeState.FRESH_MATCHED, result.realtimeState)
        assertFalse(result.departures[0].cancelled)
        assertEquals("1", result.departures[0].platform)
        assertTrue(result.departures[1].cancelled)
    }

    @Test
    fun unknownScheduledRouteIdentityNeverAcceptsSameTripIdRealtime() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip(),
                    startDate = null,
                    startTime = null,
                    cancelled = true,
                    scheduleRelationship = "CANCELED",
                    stopUpdates = emptyList(),
                    sourceTimestampEpochSeconds = 990L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )

        listOf<String?>(null, "", " ", "R2").forEach { routeId ->
            val unknown = planned.copy(routeId = routeId)
            val result = overlay(matched).enrich("A", listOf(unknown))
            assertEquals(StationRealtimeState.FRESH_NO_MATCH, result.realtimeState)
            assertEquals(listOf(unknown), result.departures)
        }

        val verified = overlay(matched).enrich("A", listOf(planned))
        assertEquals(StationRealtimeState.FRESH_MATCHED, verified.realtimeState)
        assertTrue(verified.departures.single().cancelled)
    }

    @Test
    fun skippedStopIsUnavailableOnlyAtThatStation() {
        val skipped = GermanyRealtimeMatchedStopUpdate(
            stopSequence = 1,
            scheduledStopId = "A",
            realtimeStopId = "A",
            assignedStopId = null,
            platformCode = null,
            arrivalDelaySeconds = null,
            departureDelaySeconds = 90,
            arrivalTimeEpochSeconds = null,
            departureTimeEpochSeconds = 1_140L,
            scheduleRelationship = "SKIPPED",
            pickupType = null,
            dropOffType = null,
            stopHeadsign = null,
        )
        val next = skipped.copy(
            stopSequence = 2,
            scheduledStopId = "B",
            realtimeStopId = "B",
            departureDelaySeconds = 30,
            departureTimeEpochSeconds = 1_030L,
            scheduleRelationship = "SCHEDULED",
        )
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip(),
                    startDate = null,
                    startTime = null,
                    cancelled = false,
                    scheduleRelationship = "SCHEDULED",
                    stopUpdates = listOf(skipped, next),
                    sourceTimestampEpochSeconds = 990L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )

        val stationA = overlay(matched).enrich("A", listOf(planned))
        val stationB = overlay(matched).enrich("B", listOf(planned))
        assertTrue(stationA.departures.single().cancelled)
        assertNull(stationA.departures.single().realtimeEpochSeconds)
        assertFalse(stationB.departures.single().cancelled)
        assertEquals(1_030L, stationB.departures.single().realtimeEpochSeconds)
    }

    @Test
    fun invalidStationRealtimeTimestampsKeepScheduledBoard() {
        val template = GermanyRealtimeMatchedStopUpdate(
            stopSequence = 1,
            scheduledStopId = "A",
            realtimeStopId = "A",
            assignedStopId = null,
            platformCode = null,
            arrivalDelaySeconds = null,
            departureDelaySeconds = null,
            arrivalTimeEpochSeconds = null,
            departureTimeEpochSeconds = null,
            scheduleRelationship = "SCHEDULED",
            pickupType = null,
            dropOffType = null,
            stopHeadsign = null,
        )

        fun projectedDeparture(absoluteSeconds: Long?, delaySeconds: Int?): Long? {
            val matched = GermanyRealtimeOverlayResult.Matched(
                tripOverlays = listOf(
                    GermanyRealtimeTripOverlay(
                        staticTrip = trip(),
                        startDate = null,
                        startTime = null,
                        cancelled = false,
                        scheduleRelationship = "SCHEDULED",
                        stopUpdates = listOf(
                            template.copy(
                                departureTimeEpochSeconds = absoluteSeconds,
                                departureDelaySeconds = delaySeconds,
                            ),
                        ),
                        sourceTimestampEpochSeconds = 990L,
                    ),
                ),
                serviceAlerts = emptyList(),
                freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
                ageSeconds = 10L,
                provenance = provenance(),
            )
            return overlay(matched).enrich("A", listOf(planned))
                .departures.single().realtimeEpochSeconds
        }

        // An explicit malformed absolute time must not be replaced by a delay.
        assertNull(projectedDeparture(0L, 120))
        assertNull(projectedDeparture(-1L, null))
        assertNull(projectedDeparture(Long.MAX_VALUE, null))
        assertNull(projectedDeparture(null, -1_200))

        // Valid positive estimates and early-running services remain supported.
        assertEquals(1_030L, projectedDeparture(1_030L, -1_200))
        assertEquals(1_120L, projectedDeparture(null, 120))
        assertEquals(880L, projectedDeparture(null, -120))
    }

    @Test
    fun frequencyTripRealtimeFailsClosedWithoutExactInstanceIdentity() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip(),
                    startDate = "20261004",
                    startTime = "08:00:00",
                    cancelled = false,
                    scheduleRelationship = "SCHEDULED",
                    stopUpdates = listOf(
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 1,
                            scheduledStopId = "A",
                            realtimeStopId = "A",
                            assignedStopId = null,
                            platformCode = "9",
                            arrivalDelaySeconds = null,
                            departureDelaySeconds = 300,
                            arrivalTimeEpochSeconds = null,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                    ),
                    sourceTimestampEpochSeconds = 990L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )

        val result = overlay(matched, isFrequencyTrip = { true })
            .enrich("A", listOf(planned))

        assertEquals(StationRealtimeState.FRESH_NO_MATCH, result.realtimeState)
        assertNull(result.departures.single().realtimeEpochSeconds)
        assertEquals("1", result.departures.single().platform)
    }

    @Test
    fun frequencyRealtimeAppliesOnlyToMatchingStartDateAndStartTimeInstance() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = listOf(
                GermanyRealtimeTripOverlay(
                    staticTrip = trip(),
                    startDate = "20261004",
                    startTime = "08:00:00",
                    cancelled = false,
                    scheduleRelationship = "SCHEDULED",
                    stopUpdates = listOf(
                        GermanyRealtimeMatchedStopUpdate(
                            stopSequence = 1,
                            scheduledStopId = "A",
                            realtimeStopId = "A",
                            assignedStopId = null,
                            platformCode = "9",
                            arrivalDelaySeconds = null,
                            departureDelaySeconds = 300,
                            arrivalTimeEpochSeconds = null,
                            departureTimeEpochSeconds = null,
                            scheduleRelationship = "SCHEDULED",
                            pickupType = null,
                            dropOffType = null,
                            stopHeadsign = null,
                        ),
                    ),
                    sourceTimestampEpochSeconds = 990L,
                ),
            ),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )
        val first = planned.copy(
            realtimeTripStartDate = "20261004",
            realtimeTripStartTime = "08:00:00",
        )
        val second = planned.copy(
            scheduledEpochSeconds = 1_600L,
            realtimeTripStartDate = "20261004",
            realtimeTripStartTime = "08:10:00",
        )

        val result = overlay(matched, isFrequencyTrip = { true })
            .enrich("A", listOf(first, second))

        assertEquals(1_300L, result.departures[0].realtimeEpochSeconds)
        assertEquals("9", result.departures[0].platform)
        assertNull(result.departures[1].realtimeEpochSeconds)
        assertEquals("1", result.departures[1].platform)
    }

    @Test
    fun tripScopedStationAlertDoesNotLeakAcrossReusedTripIdRoutesOrUnknownDirections() {
        val trip = GermanyRealtimeTripSelector(
            tripId = "T1",
            routeId = "R1",
            directionId = null,
            startTime = null,
            startDate = null,
            scheduleRelationship = null,
            validity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
        )
        val selector = GermanyRealtimeAlertSelector(
            agencyId = null,
            routeId = null,
            routeType = null,
            directionId = null,
            stopId = "A",
            trip = trip,
            validity = GermanyRealtimeAlertSelectorValidity.VALID,
        )

        assertTrue(overlay(alertResult(selector.copy(trip = trip.copy(routeId = "R2"))))
            .enrich("A", listOf(planned)).alerts.isEmpty())
        assertTrue(overlay(alertResult(selector.copy(routeId = "R2")))
            .enrich("A", listOf(planned)).alerts.isEmpty())
        assertTrue(overlay(alertResult(selector.copy(directionId = 1)))
            .enrich("A", listOf(planned)).alerts.isEmpty())
        assertTrue(overlay(alertResult(selector.copy(trip = trip.copy(directionId = 1))))
            .enrich("A", listOf(planned)).alerts.isEmpty())

        val matched = overlay(alertResult(selector)).enrich("A", listOf(planned))
        assertEquals(listOf("scoped-alert"), matched.alerts.map { it.id })
    }

    @Test
    fun tripScopedStationAlertUsesExactFrequencyInstanceWhenSupplied() {
        val trip = GermanyRealtimeTripSelector(
            tripId = "T1",
            routeId = "R1",
            directionId = null,
            startTime = "08:00:00",
            startDate = "20261004",
            scheduleRelationship = null,
            validity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
        )
        val selector = GermanyRealtimeAlertSelector(
            agencyId = null,
            routeId = null,
            routeType = null,
            directionId = null,
            stopId = "A",
            trip = trip,
            validity = GermanyRealtimeAlertSelectorValidity.VALID,
        )
        val selected = planned.copy(
            realtimeTripStartDate = "20261004",
            realtimeTripStartTime = "08:00:00",
        )
        assertTrue(overlay(alertResult(selector))
            .enrich("A", listOf(planned)).alerts.isEmpty())
        assertTrue(overlay(alertResult(selector))
            .enrich("A", listOf(selected.copy(realtimeTripStartDate = "20261005"))).alerts.isEmpty())
        assertTrue(overlay(alertResult(selector))
            .enrich("A", listOf(selected.copy(realtimeTripStartTime = "08:10:00"))).alerts.isEmpty())
        assertEquals(
            listOf("scoped-alert"),
            overlay(alertResult(selector)).enrich("A", listOf(selected)).alerts.map { it.id },
        )
    }

    private fun alertResult(selector: GermanyRealtimeAlertSelector) =
        GermanyRealtimeOverlayResult.Matched(
            tripOverlays = emptyList(),
            serviceAlerts = listOf(
                GermanyRealtimeAlert(
                    entityId = "scoped-alert",
                    cause = null,
                    effect = "SIGNIFICANT_DELAYS",
                    header = "Scoped disruption",
                    description = null,
                    selectors = listOf(selector),
                    activePeriods = emptyList(),
                ),
            ),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )

    @Test
    fun routeAndStopAlertSelectorDoesNotLeakToAnotherStopOnSameRoute() {
        val matched = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = emptyList(),
            serviceAlerts = listOf(
                GermanyRealtimeAlert(
                    entityId = "alert-other-stop",
                    cause = "TECHNICAL_PROBLEM",
                    effect = "SIGNIFICANT_DELAYS",
                    header = "Other stop",
                    description = "Only stop B is affected",
                    selectors = listOf(
                        GermanyRealtimeAlertSelector(
                            agencyId = null,
                            routeId = "R1",
                            routeType = null,
                            directionId = null,
                            stopId = "B",
                            trip = null,
                            validity = GermanyRealtimeAlertSelectorValidity.VALID,
                        ),
                    ),
                    activePeriods = emptyList(),
                ),
            ),
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 10L,
            provenance = provenance(),
        )

        val result = overlay(matched).enrich("A", listOf(planned))

        assertEquals(emptyList<ServiceAlert>(), result.alerts)
        assertEquals(StationRealtimeState.FRESH_NO_MATCH, result.realtimeState)
    }

    @Test
    fun staleNoMatchAndUnavailablePreservePlannedDeparture() {
        val stale = GermanyRealtimeOverlayResult.Matched(
            tripOverlays = emptyList(),
            serviceAlerts = emptyList(),
            freshness = GermanyRealtimeOverlayFreshnessState.STALE,
            ageSeconds = 600L,
            provenance = provenance(),
        )
        val staleResult = overlay(stale).enrich("A", listOf(planned))
        assertEquals(StationRealtimeState.STALE, staleResult.realtimeState)
        assertNull(staleResult.departures.single().realtimeEpochSeconds)
        assertFalse(staleResult.departures.single().cancelled)
        assertEquals("1", staleResult.departures.single().platform)

        val noMatch = GermanyRealtimeOverlayResult.NoMatch(
            freshness = GermanyRealtimeOverlayFreshnessState.FRESH,
            ageSeconds = 5L,
            provenance = provenance(),
        )
        val noMatchResult = overlay(noMatch).enrich("A", listOf(planned))
        assertEquals(StationRealtimeState.FRESH_NO_MATCH, noMatchResult.realtimeState)
        assertNull(noMatchResult.departures.single().realtimeEpochSeconds)

        val unavailable = GermanyRealtimeOverlayResult.Unavailable(
            GermanyRealtimeUnavailableReason.NETWORK,
            "offline",
        )
        val unavailableResult = overlay(unavailable).enrich("A", listOf(planned))
        assertEquals(StationRealtimeState.UNAVAILABLE, unavailableResult.realtimeState)
        assertNull(unavailableResult.departures.single().realtimeEpochSeconds)
        assertEquals("1", unavailableResult.departures.single().platform)
    }

    @Test
    fun matcherExceptionFallsBackToStaticDepartures() {
        val result = ProductionRealtimeStationOverlay(
            fetch = {
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "offline",
                )
            },
            match = { _, _ ->
                throw IllegalStateException("provider?token=secret&lat=52.1")
            },
        ).enrich("A", listOf(planned))

        assertEquals(StationRealtimeState.UNAVAILABLE, result.realtimeState)
        assertEquals(listOf(planned), result.departures)
        assertEquals(emptyList<ServiceAlert>(), result.alerts)
    }

    @Test(expected = AssertionError::class)
    fun fatalProviderErrorIsNotSilencedAsNetworkUnavailable() {
        ProductionRealtimeStationOverlay(
            fetch = { throw AssertionError("fatal VM-level failure") },
            match = { _, _ -> error("unreachable") },
        ).enrich("A", listOf(planned))
    }

    @Test
    fun preInterruptedStationLookupSkipsFetchAndMatcher() {
        var fetchCount = 0
        var matchCount = 0
        var caught = false
        try {
            Thread.currentThread().interrupt()
            ProductionRealtimeStationOverlay(
                fetch = {
                    fetchCount++
                    GermanyRealtimeFetchResult.Unavailable(
                        GermanyRealtimeUnavailableReason.NETWORK, "unused",
                    )
                },
                match = { _, _ ->
                    matchCount++
                    error("matcher must not run for cancelled lookup")
                },
            ).enrich("A", listOf(planned))
        } catch (_: InterruptedException) {
            caught = true
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        assertTrue(caught)
        assertEquals(0, fetchCount)
        assertEquals(0, matchCount)
    }

    @Test
    fun interruptionDuringStationFetchSkipsMatcher() {
        var fetchCount = 0
        var matchCount = 0
        var caught = false
        try {
            ProductionRealtimeStationOverlay(
                fetch = {
                    fetchCount++
                    Thread.currentThread().interrupt()
                    GermanyRealtimeFetchResult.Unavailable(
                        GermanyRealtimeUnavailableReason.NETWORK, "cancelled",
                    )
                },
                match = { _, _ ->
                    matchCount++
                    error("matcher must not run after fetch cancellation")
                },
            ).enrich("A", listOf(planned))
        } catch (_: InterruptedException) {
            caught = true
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        assertTrue(caught)
        assertEquals(1, fetchCount)
        assertEquals(0, matchCount)
    }

    @Test
    fun providerInterruptionPropagatesAndPreservesInterruptFlag() {
        var caught = false
        try {
            ProductionRealtimeStationOverlay(
                fetch = { throw InterruptedException("cancelled") },
                match = { _, _ -> error("unreachable") },
            ).enrich("A", listOf(planned))
        } catch (_: InterruptedException) {
            caught = true
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            // Do not leak a test-only interrupt into the rest of the JVM suite.
            Thread.interrupted()
        }
        assertTrue(caught)
    }

    private fun overlay(
        result: GermanyRealtimeOverlayResult,
        isFrequencyTrip: (String) -> Boolean = { false },
    ) =
        ProductionRealtimeStationOverlay(
            fetch = {
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "unused",
                )
            },
            match = { _, _ -> result },
            nowEpochSeconds = { 1_000L },
            isFrequencyTrip = isFrequencyTrip,
        )

    private fun trip() = GtfsScheduleTrip(
        id = "T1",
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
        feedTimestampEpochSeconds = 990L,
        fetchedAtEpochSeconds = 995L,
    )
}
