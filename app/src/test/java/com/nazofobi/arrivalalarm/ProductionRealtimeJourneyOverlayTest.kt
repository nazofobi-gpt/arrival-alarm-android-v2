package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            detail = "offline",
        )
        val unavailableResult = overlay(unavailable).enrich(listOf(option())).single()
        assertEquals(RouteRealtimeState.UNAVAILABLE, unavailableResult.realtime?.state)
        assertEquals("NETWORK: offline", unavailableResult.realtime?.unavailableReason)
        assertNull(unavailableResult.stops.last().arrival)
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
