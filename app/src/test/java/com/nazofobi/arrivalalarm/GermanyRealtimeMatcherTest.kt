package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanyRealtimeMatcherTest {
    private val date = GtfsServiceDate(2026, 10, 4)

    @Test
    fun exactTripUsesStopSequenceAndRetainsAssignedPlatformAndProvenance() {
        val trip = trip("T1", "R1", "S", direction = 0)
        val data = FakeRealtimeStaticData(
            trips = mapOf("T1" to trip),
            routes = mapOf("R1" to route("R1", "agency", 3)),
            stops = mapOf(
                "scheduled-stop" to stop("scheduled-stop", "1", "station-1"),
                "assigned-stop" to stop("assigned-stop", "2", "station-1"),
            ),
            stopTimes = mapOf(
                "T1" to listOf(
                    st("T1", 1, "origin", "08:00:00"),
                    st("T1", 2, "scheduled-stop", "08:10:00"),
                ),
            ),
        )
        val update = GermanyRealtimeTripUpdate(
            entityId = "u1",
            tripId = "T1",
            routeId = "R1",
            startDate = null,
            scheduleRelationship = "SCHEDULED",
            cancelled = false,
            stops = listOf(
                GermanyRealtimeStopUpdate(
                    stopSequence = 2,
                    stopId = "assigned-stop",
                    arrivalDelaySeconds = 90,
                    departureDelaySeconds = 120,
                    arrivalTimeEpochSeconds = 1_800_000_000L,
                    departureTimeEpochSeconds = 1_800_000_030L,
                    assignedStopId = "assigned-stop",
                    pickupType = "REGULAR",
                    dropOffType = "REGULAR",
                    stopHeadsign = "Zentrum",
                ),
            ),
            directionId = 0,
            selectorValidity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
            updateTimestampEpochSeconds = 1_799_999_999L,
        )

        val result = matcher(data).match(snapshot(tripUpdates = listOf(update)), 1_800_000_010L)
        val matched = result as GermanyRealtimeOverlayResult.Matched
        val overlay = matched.tripOverlays.single()
        assertEquals("T1", overlay.staticTrip.id)
        assertEquals(1_799_999_999L, overlay.sourceTimestampEpochSeconds)
        val stop = overlay.stopUpdates.single()
        assertEquals(2, stop.stopSequence)
        assertEquals("scheduled-stop", stop.scheduledStopId)
        assertEquals("assigned-stop", stop.realtimeStopId)
        assertEquals("assigned-stop", stop.assignedStopId)
        assertEquals("2", stop.platformCode)
        assertEquals(120, stop.departureDelaySeconds)
        assertEquals("Zentrum", stop.stopHeadsign)
        assertEquals("feed-v1", matched.provenance.feedVersion)
    }

    @Test
    fun sequencedStopReassignmentRequiresSameStaticParentStationAndUniqueSequence() {
        val trip = trip("T1", "R1", "S", direction = 0)
        val data = FakeRealtimeStaticData(
            trips = mapOf("T1" to trip),
            routes = mapOf("R1" to route("R1", "agency", 3)),
            stops = mapOf(
                "scheduled-stop" to stop("scheduled-stop", "1", "station-1"),
                "other-stop" to stop("other-stop", "9", "station-1"),
                "cross-station-stop" to stop("cross-station-stop", "3", "station-2"),
                "orphan-stop" to stop("orphan-stop", "4"),
            ),
            stopTimes = mapOf(
                "T1" to listOf(
                    st("T1", 1, "origin", "08:00:00"),
                    st("T1", 2, "scheduled-stop", "08:10:00"),
                ),
            ),
        )
        val realtimeStop = GermanyRealtimeStopUpdate(
            stopSequence = 2,
            stopId = "other-stop",
            arrivalDelaySeconds = null,
            departureDelaySeconds = 120,
            arrivalTimeEpochSeconds = null,
            departureTimeEpochSeconds = 1_800_000_120L,
        )
        val tripUpdate = update(tripId = "T1", routeId = "R1", directionId = 0)
            .copy(stops = listOf(realtimeStop))
        fun matchedStops(changed: GermanyRealtimeStopUpdate) =
            (matcher(data).match(
                snapshot(tripUpdates = listOf(tripUpdate.copy(stops = listOf(changed)))),
                1_800_000_010L,
            ) as GermanyRealtimeOverlayResult.Matched).tripOverlays.single().stopUpdates

        val stopIdReassignment = matchedStops(realtimeStop).single()
        assertEquals("scheduled-stop", stopIdReassignment.scheduledStopId)
        assertEquals("other-stop", stopIdReassignment.realtimeStopId)
        assertEquals(null, stopIdReassignment.assignedStopId)
        assertEquals("9", stopIdReassignment.platformCode)

        assertTrue(matchedStops(realtimeStop.copy(stopId = "cross-station-stop")).isEmpty())
        assertTrue(matchedStops(realtimeStop.copy(stopId = "orphan-stop")).isEmpty())
        assertTrue(matchedStops(realtimeStop.copy(stopId = "unknown-stop")).isEmpty())
        assertTrue(matchedStops(realtimeStop.copy(stopSequence = null)).isEmpty())
        assertEquals("scheduled-stop", matchedStops(
            realtimeStop.copy(stopId = "scheduled-stop"),
        ).single().scheduledStopId)
        assertEquals("scheduled-stop", matchedStops(
            realtimeStop.copy(stopId = null),
        ).single().scheduledStopId)

        val assigned = matchedStops(
            realtimeStop.copy(assignedStopId = "other-stop"),
        ).single()
        assertEquals("scheduled-stop", assigned.scheduledStopId)
        assertEquals("other-stop", assigned.assignedStopId)
        assertEquals("9", assigned.platformCode)

        val assignedWithoutStopId = matchedStops(
            realtimeStop.copy(stopId = null, assignedStopId = "other-stop"),
        ).single()
        assertEquals("other-stop", assignedWithoutStopId.assignedStopId)
        assertEquals("9", assignedWithoutStopId.platformCode)

        assertTrue(matchedStops(
            realtimeStop.copy(stopId = "cross-station-stop", assignedStopId = "cross-station-stop"),
        ).isEmpty())
        assertTrue(matchedStops(
            realtimeStop.copy(assignedStopId = "cross-station-stop"),
        ).isEmpty())

        val duplicateSequenceResult = matcher(data).match(
            snapshot(tripUpdates = listOf(tripUpdate.copy(stops = listOf(
                realtimeStop,
                realtimeStop.copy(stopId = "scheduled-stop"),
            )))),
            1_800_000_010L,
        ) as GermanyRealtimeOverlayResult.Matched
        assertTrue(duplicateSequenceResult.tripOverlays.single().stopUpdates.none {
            it.realtimeStopId == "other-stop"
        })
    }

    @Test
    fun frequencyTripRequiresCompleteInstanceIdentityAndValidHeadwayInstance() {
        val trip = trip("F1", "R1", "S", direction = 0)
        val data = FakeRealtimeStaticData(
            trips = mapOf("F1" to trip),
            routes = mapOf("R1" to route("R1", "agency", 3)),
            stopTimes = mapOf(
                "F1" to listOf(
                    st("F1", 1, "A", "08:00:00"),
                    st("F1", 2, "B", "08:15:00"),
                ),
            ),
            frequencies = mapOf(
                "F1" to listOf(GtfsScheduleFrequency("F1", "08:00:00", "09:00:00", 600, 1)),
            ),
        )
        val incomplete = update(
            tripId = "F1",
            routeId = "R1",
            directionId = 0,
            startTime = null,
            startDate = null,
        )
        assertTrue(
            matcher(data).match(snapshot(tripUpdates = listOf(incomplete)), 1_800_000_010L)
                is GermanyRealtimeOverlayResult.NoMatch,
        )

        val exactInstance = update(
            tripId = "F1",
            routeId = "R1",
            directionId = 0,
            startTime = "08:20:00",
            startDate = "20261004",
        )
        val result = matcher(data).match(
            snapshot(tripUpdates = listOf(exactInstance)),
            1_800_000_010L,
        )
        assertEquals(
            "F1",
            (result as GermanyRealtimeOverlayResult.Matched).tripOverlays.single().staticTrip.id,
        )

        val offHeadway = exactInstance.copy(startTime = "08:21:00")
        assertTrue(
            matcher(data).match(snapshot(tripUpdates = listOf(offHeadway)), 1_800_000_010L)
                is GermanyRealtimeOverlayResult.NoMatch,
        )
    }

    @Test
    fun idlessScheduledSelectorResolvesOnlyOneUniqueStaticTrip() {
        val u1 = trip("U1", "R", "S", direction = 1)
        val u2 = trip("U2", "R", "S", direction = 1)
        val data = FakeRealtimeStaticData(
            trips = mapOf("U1" to u1, "U2" to u2),
            routeDirectionTrips = mapOf(("R" to 1) to listOf(u1, u2)),
            routes = mapOf("R" to route("R", "agency", 2)),
            stopTimes = mapOf(
                "U1" to listOf(st("U1", 1, "A", "25:10:00"), st("U1", 2, "B", "25:20:00")),
                "U2" to listOf(st("U2", 1, "A", "25:30:00"), st("U2", 2, "B", "25:40:00")),
            ),
        )
        val selector = update(
            tripId = null,
            routeId = "R",
            directionId = 1,
            startTime = "25:10:00",
            startDate = "20261004",
            validity = GermanyRealtimeTripSelectorValidity.VALID_IDLESS_SCHEDULED,
        )
        val matched = matcher(data).match(
            snapshot(tripUpdates = listOf(selector)),
            1_800_000_010L,
        ) as GermanyRealtimeOverlayResult.Matched
        assertEquals("U1", matched.tripOverlays.single().staticTrip.id)

        val ambiguous = data.copy(
            stopTimes = data.stopTimes + (
                "U2" to listOf(st("U2", 1, "A", "25:10:00"), st("U2", 2, "B", "25:40:00"))
            ),
        )
        assertTrue(
            matcher(ambiguous).match(snapshot(tripUpdates = listOf(selector)), 1_800_000_010L)
                is GermanyRealtimeOverlayResult.NoMatch,
        )
    }

    @Test
    fun alertSelectorConjunctionIsValidatedWithoutFlatteningAlternatives() {
        val trip = trip("T1", "R1", "S", direction = 1)
        val data = FakeRealtimeStaticData(
            trips = mapOf("T1" to trip),
            routes = mapOf("R1" to route("R1", "agency-1", 3)),
            stops = mapOf("S1" to stop("S1", "4")),
            stopTimes = mapOf(
                "T1" to listOf(st("T1", 1, "S1", "08:00:00"), st("T1", 2, "S2", "08:10:00")),
            ),
        )
        val tripSelector = GermanyRealtimeTripSelector(
            tripId = "T1",
            routeId = "R1",
            directionId = 1,
            startTime = null,
            startDate = null,
            scheduleRelationship = "SCHEDULED",
            validity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
        )
        val valid = GermanyRealtimeAlertSelector(
            agencyId = "agency-1",
            routeId = "R1",
            routeType = 3,
            directionId = 1,
            stopId = "S1",
            trip = tripSelector,
            validity = GermanyRealtimeAlertSelectorValidity.VALID,
        )
        val wrongAgency = valid.copy(agencyId = "other")
        val validAlert = alert("a-valid", listOf(valid))
        val invalidAlert = alert("a-invalid", listOf(wrongAgency))

        val result = matcher(data).match(
            snapshot(serviceAlerts = listOf(validAlert, invalidAlert)),
            1_800_000_010L,
        ) as GermanyRealtimeOverlayResult.Matched

        assertEquals(listOf("a-valid"), result.serviceAlerts.map { it.entityId })
        assertEquals("R1", result.serviceAlerts.single().selectors.single().routeId)
        assertEquals("S1", result.serviceAlerts.single().selectors.single().stopId)
    }

    @Test
    fun alertSelectorDimensionsMustResolveConjunctivelyAgainstStaticGraph() {
        val t1 = trip("T1", "R1", "S", direction = 1)
        val t2 = trip("T2", "R2", "S", direction = 0)
        val data = FakeRealtimeStaticData(
            trips = mapOf("T1" to t1, "T2" to t2),
            routes = mapOf(
                "R1" to route("R1", "agency-1", 3),
                "R2" to route("R2", "agency-2", 2),
            ),
            stops = mapOf(
                "S1" to stop("S1", "1"),
                "S2" to stop("S2", "2"),
            ),
            stopTimes = mapOf(
                "T1" to listOf(st("T1", 1, "S1", "08:00:00")),
                "T2" to listOf(st("T2", 1, "S2", "08:00:00")),
            ),
            routeDirectionTrips = mapOf(
                ("R1" to 1) to listOf(t1),
                ("R2" to 0) to listOf(t2),
            ),
        )

        val validRouteStop = GermanyRealtimeAlertSelector(
            agencyId = null,
            routeId = "R1",
            routeType = null,
            directionId = null,
            stopId = "S1",
            trip = null,
            validity = GermanyRealtimeAlertSelectorValidity.VALID,
        )
        val mismatchedRouteStop = validRouteStop.copy(stopId = "S2")
        val validAgencyType = GermanyRealtimeAlertSelector(
            agencyId = "agency-1",
            routeId = null,
            routeType = 3,
            directionId = null,
            stopId = null,
            trip = null,
            validity = GermanyRealtimeAlertSelectorValidity.VALID,
        )
        val mismatchedAgencyType = validAgencyType.copy(routeType = 2)
        val directionOnly = GermanyRealtimeAlertSelector(
            agencyId = null,
            routeId = null,
            routeType = null,
            directionId = 1,
            stopId = null,
            trip = null,
            validity = GermanyRealtimeAlertSelectorValidity.VALID,
        )

        val result = matcher(data).match(
            snapshot(
                serviceAlerts = listOf(
                    alert("valid-route-stop", listOf(validRouteStop)),
                    alert("bad-route-stop", listOf(mismatchedRouteStop)),
                    alert("valid-agency-type", listOf(validAgencyType)),
                    alert("bad-agency-type", listOf(mismatchedAgencyType)),
                    alert("direction-only", listOf(directionOnly)),
                ),
            ),
            1_800_000_010L,
        ) as GermanyRealtimeOverlayResult.Matched

        assertEquals(
            listOf("valid-route-stop", "valid-agency-type"),
            result.serviceAlerts.map { it.entityId },
        )
    }

    @Test
    fun expiredAlertActivePeriodIsNotApplied() {
        val data = FakeRealtimeStaticData(
            routes = mapOf("R1" to route("R1", "agency-1", 3)),
        )
        val selector = GermanyRealtimeAlertSelector(
            agencyId = null,
            routeId = "R1",
            routeType = null,
            directionId = null,
            stopId = null,
            trip = null,
            validity = GermanyRealtimeAlertSelectorValidity.VALID,
        )
        val expired = alert("expired", listOf(selector)).copy(
            activePeriods = listOf(
                GermanyRealtimeActivePeriod(
                    startEpochSeconds = 1_799_999_000L,
                    endEpochSeconds = 1_800_000_000L,
                ),
            ),
        )
        val openEnded = alert("open-ended", listOf(selector)).copy(
            activePeriods = listOf(
                GermanyRealtimeActivePeriod(
                    startEpochSeconds = 1_800_000_000L,
                    endEpochSeconds = null,
                ),
            ),
        )

        val result = matcher(data).match(
            snapshot(serviceAlerts = listOf(expired, openEnded)),
            1_800_000_010L,
        ) as GermanyRealtimeOverlayResult.Matched

        assertEquals(listOf("open-ended"), result.serviceAlerts.map { it.entityId })
    }

    @Test
    fun staleUnavailableAndNoMatchAreExplicit() {
        val noStaticMatch = matcher(FakeRealtimeStaticData()).match(
            snapshot(
                feedTimestamp = 1_000L,
                tripUpdates = listOf(update(tripId = "missing", routeId = null, directionId = null)),
            ),
            1_500L,
        )
        val noMatch = noStaticMatch as GermanyRealtimeOverlayResult.NoMatch
        assertEquals(GermanyRealtimeOverlayFreshnessState.STALE, noMatch.freshness)
        assertEquals(500L, noMatch.ageSeconds)

        val unavailable = matcher(FakeRealtimeStaticData()).match(
            GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.NETWORK,
                "offline",
            ),
            1_500L,
        )
        assertTrue(unavailable is GermanyRealtimeOverlayResult.Unavailable)
        assertEquals(
            GermanyRealtimeUnavailableReason.NETWORK,
            (unavailable as GermanyRealtimeOverlayResult.Unavailable).reason,
        )
    }

    @Test
    fun providerClockAcceptsAtMostThirtySecondsOfFutureSkew() {
        val now = 1_800_000_010L
        val matcher = matcher(FakeRealtimeStaticData())
        val atLimit = matcher.match(snapshot(feedTimestamp = now + 30L), now)
            as GermanyRealtimeOverlayResult.NoMatch
        val beyondLimit = matcher.match(snapshot(feedTimestamp = now + 31L), now)
            as GermanyRealtimeOverlayResult.NoMatch

        assertEquals(GermanyRealtimeOverlayFreshnessState.FRESH, atLimit.freshness)
        assertEquals(0L, atLimit.ageSeconds)
        assertEquals(GermanyRealtimeOverlayFreshnessState.STALE, beyondLimit.freshness)
    }

    @Test
    fun missingFeedTimestampUsesFetchedAtWithTheSameFutureSkewLimit() {
        val now = 1_800_000_010L
        val matcher = matcher(FakeRealtimeStaticData())
        val atLimit = snapshot().copy(
            feedTimestampEpochSeconds = null,
            fetchedAtEpochSeconds = now + 30L,
        )
        val beyondLimit = atLimit.copy(fetchedAtEpochSeconds = now + 31L)

        assertEquals(
            GermanyRealtimeOverlayFreshnessState.FRESH,
            (matcher.match(atLimit, now) as GermanyRealtimeOverlayResult.NoMatch).freshness,
        )
        assertEquals(
            GermanyRealtimeOverlayFreshnessState.STALE,
            (matcher.match(beyondLimit, now) as GermanyRealtimeOverlayResult.NoMatch).freshness,
        )
    }

    @Test
    fun invalidProviderEpochsAndNonpositiveDeviceClocksAreNeverFresh() {
        val now = 1_800_000_010L
        val matcher = matcher(FakeRealtimeStaticData())
        for (invalidEpoch in listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val result = matcher.match(snapshot(feedTimestamp = invalidEpoch), now)
                as GermanyRealtimeOverlayResult.NoMatch
            assertEquals(
                "provider epoch=$invalidEpoch",
                GermanyRealtimeOverlayFreshnessState.STALE,
                result.freshness,
            )
        }
        for (invalidNow in listOf(0L, -1L)) {
            val result = matcher.match(snapshot(feedTimestamp = 1L), invalidNow)
                as GermanyRealtimeOverlayResult.NoMatch
            assertEquals(
                "device clock=$invalidNow",
                GermanyRealtimeOverlayFreshnessState.STALE,
                result.freshness,
            )
        }
    }

    private fun matcher(data: GermanyRealtimeStaticData) =
        GermanyRealtimeMatcher(data, freshWindowSeconds = 120)

    private fun snapshot(
        feedTimestamp: Long = 1_800_000_000L,
        tripUpdates: List<GermanyRealtimeTripUpdate> = emptyList(),
        serviceAlerts: List<GermanyRealtimeAlert> = emptyList(),
    ) = GermanyRealtimeSnapshot(
        source = GermanyGtfsRealtimeProvenance.PROVIDER,
        license = GermanyGtfsRealtimeProvenance.LICENSE,
        feedTimestampEpochSeconds = feedTimestamp,
        fetchedAtEpochSeconds = feedTimestamp + 5,
        tripUpdates = tripUpdates,
        serviceAlerts = serviceAlerts,
        gtfsRealtimeVersion = "2.0",
        incrementality = "FULL_DATASET",
        feedVersion = "feed-v1",
    )

    private fun update(
        tripId: String?,
        routeId: String?,
        directionId: Int?,
        startTime: String? = null,
        startDate: String? = null,
        validity: GermanyRealtimeTripSelectorValidity =
            GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
    ) = GermanyRealtimeTripUpdate(
        entityId = "update-${tripId ?: routeId}",
        tripId = tripId,
        routeId = routeId,
        startDate = startDate,
        scheduleRelationship = "SCHEDULED",
        cancelled = false,
        stops = emptyList(),
        directionId = directionId,
        startTime = startTime,
        selectorValidity = validity,
        updateTimestampEpochSeconds = 1_800_000_001L,
    )

    private fun alert(
        id: String,
        selectors: List<GermanyRealtimeAlertSelector>,
    ) = GermanyRealtimeAlert(
        entityId = id,
        cause = null,
        effect = null,
        header = id,
        description = null,
        selectors = selectors,
        activePeriods = emptyList(),
        severityLevel = "WARNING",
    )

    private fun trip(
        id: String,
        routeId: String,
        serviceId: String,
        direction: Int?,
    ) = GtfsScheduleTrip(
        id = id,
        routeId = routeId,
        serviceId = serviceId,
        headsign = null,
        directionId = direction,
        shapeId = null,
        wheelchairAccessible = null,
        bikesAllowed = null,
    )

    private fun route(id: String, agencyId: String?, routeType: Int?) =
        GtfsScheduleRoute(id, agencyId, id, null, routeType)

    private fun stop(
        id: String,
        platform: String?,
        parentStationId: String? = null,
    ) = GtfsScheduleStop(id, id, 52.0, 8.0, parentStationId, 0, platform, null)

    private fun st(
        tripId: String,
        sequence: Int,
        stopId: String,
        time: String,
    ) = GtfsScheduleStopTime(
        tripId,
        sequence,
        stopId,
        time,
        time,
        null,
        null,
        null,
        null,
    )

    private data class FakeRealtimeStaticData(
        val trips: Map<String, GtfsScheduleTrip> = emptyMap(),
        val routes: Map<String, GtfsScheduleRoute> = emptyMap(),
        val stops: Map<String, GtfsScheduleStop> = emptyMap(),
        val stopTimes: Map<String, List<GtfsScheduleStopTime>> = emptyMap(),
        val frequencies: Map<String, List<GtfsScheduleFrequency>> = emptyMap(),
        val routeDirectionTrips: Map<Pair<String, Int>, List<GtfsScheduleTrip>> = emptyMap(),
        val inactiveServices: Set<String> = emptySet(),
        val activeDate: GtfsServiceDate = GtfsServiceDate(2026, 10, 4),
    ) : GermanyRealtimeStaticData {
        override fun trip(tripId: String) = trips[tripId]

        override fun route(routeId: String) = routes[routeId]

        override fun stop(stopId: String) = stops[stopId]

        override fun stopTimesForTrip(tripId: String, limit: Int) =
            stopTimes[tripId].orEmpty().take(limit)

        override fun frequenciesForTrip(tripId: String, limit: Int) =
            frequencies[tripId].orEmpty().take(limit)

        override fun tripsForRouteDirection(
            routeId: String,
            directionId: Int,
            limit: Int,
        ) = routeDirectionTrips[routeId to directionId].orEmpty().take(limit)

        override fun routesMatching(
            agencyId: String?,
            routeType: Int?,
            limit: Int,
        ) = routes.values
            .asSequence()
            .filter { route -> agencyId == null || route.agencyId == agencyId }
            .filter { route -> routeType == null || route.routeType == routeType }
            .sortedBy { it.id }
            .take(limit)
            .toList()

        override fun tripsServingStop(
            stopId: String,
            limit: Int,
        ) = trips.values
            .asSequence()
            .filter { trip -> stopTimes[trip.id].orEmpty().any { it.stopId == stopId } }
            .sortedBy { it.id }
            .take(limit)
            .toList()

        override fun isServiceActive(
            serviceId: String,
            serviceDate: GtfsServiceDate,
        ) = serviceId !in inactiveServices && serviceDate == activeDate
    }
}
