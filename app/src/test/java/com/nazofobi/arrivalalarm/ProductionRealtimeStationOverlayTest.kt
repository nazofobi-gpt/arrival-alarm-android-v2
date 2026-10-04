package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun overlay(result: GermanyRealtimeOverlayResult) =
        ProductionRealtimeStationOverlay(
            fetch = {
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.NETWORK,
                    "unused",
                )
            },
            match = { _, _ -> result },
            nowEpochSeconds = { 1_000L },
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
