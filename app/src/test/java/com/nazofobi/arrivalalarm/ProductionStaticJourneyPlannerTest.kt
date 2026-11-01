package com.nazofobi.arrivalalarm

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionStaticJourneyPlannerTest {
    @Test
    fun nonpositiveStaticJourneyLimitNeverSynthesizesARequestedOption() {
        for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            assertEquals(0, boundedStaticJourneyOptionLimit(invalid))
        }
        assertEquals(1, boundedStaticJourneyOptionLimit(1))
        assertEquals(3, boundedStaticJourneyOptionLimit(3))
        assertEquals(6, boundedStaticJourneyOptionLimit(6))
        assertEquals(6, boundedStaticJourneyOptionLimit(Int.MAX_VALUE))
    }

    @Test
    fun accessAndEgressWalkingRemainDistinctAndTotalDoesNotOverflow() {
        val data = FakeRouteOptionData(
            routes = emptyMap(), trips = emptyMap(), stops = emptyMap(), stopTimes = emptyMap(),
        )
        val huge = StaticTransitJourney(
            id = "bounded-walk",
            access = StaticRouterAccess("origin-stop", Int.MAX_VALUE),
            egress = StaticRouterAccess("destination-stop", Int.MAX_VALUE),
            legs = emptyList(),
            arrivalEpochMillis = at("08:00:00"),
        )
        val mapped = StaticRouteOptionMapper(data, zone).map(
            huge, MapPoint(0.0, 0.0, "Start"), MapPoint(0.0, 0.0, "Ziel"), date, 4L,
        )
        assertEquals(71_582_789, mapped.walkingMinutes)
        assertEquals("origin-stop", mapped.accessWalking?.stopName)
        assertEquals("destination-stop", mapped.egressWalking?.stopName)
        assertEquals(Int.MAX_VALUE, mapped.accessWalking?.walkSeconds)
        assertEquals(Int.MAX_VALUE, mapped.egressWalking?.walkSeconds)

        val none = huge.copy(
            access = StaticRouterAccess("origin-stop"),
            egress = StaticRouterAccess("destination-stop"),
        )
        val withoutWalk = StaticRouteOptionMapper(data, zone).map(
            none, MapPoint(0.0, 0.0, "Start"), MapPoint(0.0, 0.0, "Ziel"), date, 4L,
        )
        assertEquals(0, withoutWalk.walkingMinutes)
        assertNull(withoutWalk.accessWalking)
        assertNull(withoutWalk.egressWalking)
    }

    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val date = GtfsServiceDate(2026, 10, 4)

    @Test
    fun mapperPreservesStableGraphIdentityStopsAndScheduledTimes() {
        val data = FakeRouteOptionData(
            routes = mapOf(
                "R1" to GtfsScheduleRoute("R1", "A1", "RE 1", null, 2),
                "R2" to GtfsScheduleRoute("R2", "A2", "IC 2", null, 2),
            ),
            trips = mapOf(
                "T1" to trip("T1", "R1", "Bremen"),
                "T2" to trip("T2", "R2", "Berlin"),
            ),
            stops = mapOf(
                "A" to stop("A", "Lohne"),
                "X" to stop("X", "Bremen Hbf"),
                "B" to stop("B", "Berlin Hbf"),
            ),
            stopTimes = mapOf(
                "T1" to listOf(
                    st("T1", 1, "A", "08:00:00", "08:00:00"),
                    st("T1", 2, "X", "08:20:00", "08:20:00"),
                ),
                "T2" to listOf(
                    st("T2", 1, "X", "08:25:00", "08:25:00"),
                    st("T2", 2, "B", "08:45:00", "08:45:00"),
                ),
            ),
        )
        val journey = StaticTransitJourney(
            id = "stable-static-id",
            access = StaticRouterAccess("A", 60),
            egress = StaticRouterAccess("B", 120),
            legs = listOf(
                StaticTransitLeg("T1", "R1", "A", "X", at("08:00:00"), at("08:20:00")),
                StaticTransitLeg("T2", "R2", "X", "B", at("08:25:00"), at("08:45:00")),
            ),
            arrivalEpochMillis = at("08:47:00"),
        )

        val option = StaticRouteOptionMapper(data, zone).map(
            journey = journey,
            origin = MapPoint(52.66, 8.23, "Lohne"),
            destination = MapPoint(52.52, 13.40, "Berlin"),
            serviceDate = date,
            sourceUpdatedAtEpochSeconds = 1234L,
        )

        assertEquals("stable-static-id", option.id)
        assertEquals("RE 1 → IC 2", option.line)
        assertEquals("Berlin", option.direction)
        assertEquals("08:00", option.departure)
        assertEquals("08:47", option.arrival)
        assertEquals(journey.legs.first().departureEpochMillis, option.departureEpochMillis)
        assertEquals(journey.arrivalEpochMillis, option.arrivalEpochMillis)
        assertEquals(3, option.walkingMinutes)
        assertEquals("A", option.accessWalking?.stopId)
        assertEquals("Lohne", option.accessWalking?.stopName)
        assertEquals(60, option.accessWalking?.walkSeconds)
        assertEquals("B", option.egressWalking?.stopId)
        assertEquals("Berlin Hbf", option.egressWalking?.stopName)
        assertEquals(120, option.egressWalking?.walkSeconds)
        assertEquals(1, option.transfers)
        assertEquals(listOf("T1", "T2"), option.tripIds)
        assertEquals(listOf("A", "X", "B"), option.stops.map { it.id })
        assertEquals("Lohne", option.stops[0].name)
        assertEquals("Bremen Hbf", option.stops[1].name)
        assertEquals("Berlin Hbf", option.stops[2].name)
        assertEquals("2026-10-04T08:20:00+02:00", option.stops[1].plannedArrival)
        assertEquals("2026-10-04T08:25:00+02:00", option.stops[1].plannedDeparture)
        assertNull(option.stops[1].arrival)
        assertNull(option.stops[1].departure)
        assertEquals(1234L, option.sourceUpdatedAtEpochSeconds)
    }

    @Test
    fun frequencyShiftIsAppliedToEveryMappedStop() {
        val data = FakeRouteOptionData(
            routes = mapOf("R" to GtfsScheduleRoute("R", null, "S", null, 3)),
            trips = mapOf("F" to trip("F", "R", "Ziel")),
            stops = mapOf("A" to stop("A", "A"), "B" to stop("B", "B")),
            stopTimes = mapOf(
                "F" to listOf(
                    st("F", 1, "A", "00:00:00", "00:00:00"),
                    st("F", 2, "B", "00:10:00", "00:10:00"),
                ),
            ),
        )
        val shift = 9 * 60 * 60 * 1000L
        val journey = StaticTransitJourney(
            id = "frequency-instance",
            access = StaticRouterAccess("A"),
            egress = StaticRouterAccess("B"),
            legs = listOf(
                StaticTransitLeg(
                    tripId = "F",
                    routeId = "R",
                    fromStopId = "A",
                    toStopId = "B",
                    departureEpochMillis = at("00:00:00") + shift,
                    arrivalEpochMillis = at("00:10:00") + shift,
                    frequencyBased = true,
                    frequencyInstanceStartSeconds = 9 * 3600,
                ),
            ),
            arrivalEpochMillis = at("00:10:00") + shift,
        )

        val option = StaticRouteOptionMapper(data, zone).map(
            journey,
            MapPoint(0.0, 0.0, "A"),
            MapPoint(0.0, 0.0, "B"),
            date,
            1L,
        )

        assertEquals("09:00", option.departure)
        assertEquals("2026-10-04T09:00:00+02:00", option.stops.first().plannedDeparture)
        assertEquals("2026-10-04T09:10:00+02:00", option.stops.last().plannedArrival)
    }

    @Test
    fun longCircularRouteWithRepeatedStopsSelectsBoundedEarliestPair() {
        // 2,048 stop_times is the production mapper cap. Alternating A/B
        // used to produce over a million comparisons in the nested scan.
        val times = (0 until 2_048).map { minute ->
            val time = "%02d:%02d:00".format(Locale.ROOT, minute / 60, minute % 60)
            st("LOOP", minute + 1, if (minute % 2 == 0) "A" else "B", time, time)
        }
        val data = FakeRouteOptionData(
            routes = mapOf("R" to GtfsScheduleRoute("R", null, "Ring", null, 3)),
            trips = mapOf("LOOP" to trip("LOOP", "R", "Ring")),
            stops = mapOf("A" to stop("A", "Board"), "B" to stop("B", "Alight")),
            stopTimes = mapOf("LOOP" to times),
        )
        val journey = StaticTransitJourney(
            id = "loop",
            access = StaticRouterAccess("A"),
            egress = StaticRouterAccess("B"),
            legs = listOf(
                StaticTransitLeg("LOOP", "R", "A", "B", at("00:00:00"), at("00:01:00")),
            ),
            arrivalEpochMillis = at("00:01:00"),
        )

        val option = StaticRouteOptionMapper(data, zone).map(
            journey, MapPoint(0.0, 0.0, "Board"), MapPoint(0.0, 0.0, "Alight"), date, 1L,
        )

        assertEquals(listOf("A", "B"), option.stops.map { it.id })
        assertEquals("2026-10-04T00:01:00+02:00", option.stops.last().plannedArrival)
    }

    @Test
    fun equallyTimedRepeatedStopPairsPreserveEarliestBoardingAndAlightingRows() {
        val data = FakeRouteOptionData(
            routes = mapOf("R" to GtfsScheduleRoute("R", null, "Ring", null, 3)),
            trips = mapOf("LOOP" to trip("LOOP", "R", "Ring")),
            stops = listOf("A", "X", "B", "Y").associateWith { stop(it, it) },
            stopTimes = mapOf(
                "LOOP" to listOf(
                    st("LOOP", 1, "A", "08:00:00", "08:00:00"),
                    st("LOOP", 2, "X", "08:04:00", "08:04:00"),
                    st("LOOP", 3, "B", "08:10:00", "08:10:00"),
                    st("LOOP", 4, "A", "08:20:00", "08:20:00"),
                    st("LOOP", 5, "Y", "08:24:00", "08:24:00"),
                    st("LOOP", 6, "B", "08:30:00", "08:30:00"),
                ),
            ),
        )
        val journey = StaticTransitJourney(
            id = "tie",
            access = StaticRouterAccess("A"),
            egress = StaticRouterAccess("B"),
            legs = listOf(
                StaticTransitLeg("LOOP", "R", "A", "B", at("08:00:00"), at("08:10:00")),
            ),
            arrivalEpochMillis = at("08:10:00"),
        )

        val option = StaticRouteOptionMapper(data, zone).map(
            journey, MapPoint(0.0, 0.0, "A"), MapPoint(0.0, 0.0, "B"), date, 1L,
        )

        assertEquals(listOf("A", "X", "B"), option.stops.map { it.id })
    }

    @Test
    fun repeatedLoopStopsReuseMetadataLookupsIncludingMissingStops() {
        val times = (0 until 128).map { minute ->
            val time = "%02d:%02d:00".format(Locale.ROOT, minute / 60, minute % 60)
            st("LOOP", minute + 1, if (minute % 2 == 0) "A" else "B", time, time)
        }
        val data = FakeRouteOptionData(
            routes = mapOf("R" to GtfsScheduleRoute("R", null, "Ring", null, 3)),
            trips = mapOf("LOOP" to trip("LOOP", "R", "Ring")),
            // B is deliberately absent: missing metadata must be memoized too.
            stops = mapOf("A" to stop("A", "Board")),
            stopTimes = mapOf("LOOP" to times),
        )
        val journey = StaticTransitJourney(
            id = "metadata-cache",
            access = StaticRouterAccess("A"),
            egress = StaticRouterAccess("B"),
            legs = listOf(
                StaticTransitLeg("LOOP", "R", "A", "B", at("00:00:00"), at("02:07:00")),
            ),
            arrivalEpochMillis = at("02:07:00"),
        )

        val option = StaticRouteOptionMapper(data, zone).map(
            journey, MapPoint(0.0, 0.0, "A"), MapPoint(0.0, 0.0, "B"), date, 1L,
        )

        assertEquals(times.map { it.stopId }, option.stops.map { it.id })
        assertEquals("B", option.stops.last().name)
        assertEquals(mapOf("A" to 1, "B" to 1), data.stopLookupCounts)
    }

    @Test
    fun routingDateCandidatesIncludeTomorrowAcrossDaylightSavingChange() {
        val saturday = GtfsServiceDate(2026, 10, 24)
        val beforeDst = GtfsServiceTime.parse("23:50:00").resolve(saturday, zone).epochMillis
        assertEquals(
            listOf(
                GtfsServiceDate(2026, 10, 23),
                saturday,
                GtfsServiceDate(2026, 10, 25),
            ),
            staticRoutingServiceDates(beforeDst, zone),
        )

        val sunday = GtfsServiceDate(2026, 10, 25)
        val afterDst = GtfsServiceTime.parse("23:50:00").resolve(sunday, zone).epochMillis
        assertEquals(
            listOf(saturday, sunday, GtfsServiceDate(2026, 10, 26)),
            staticRoutingServiceDates(afterDst, zone),
        )
    }

    @Test
    fun selectedTripConstraintMapsTheExactRepeatedStopOccurrence() {
        val identity = selectedTripIdentity()
        val constraint = SelectedTripConstraint(
            identity = identity,
            boarding = GtfsSelectedStopRef(30, "A"),
            destination = GtfsSelectedStopRef(40, "C"),
        )
        val outcome = planSelectedTripFromDetail(
            detail = selectedTripDetail(identity),
            constraint = constraint,
            mapper = selectedTripMapper(),
            sourceUpdatedAtEpochSeconds = 1234L,
        )

        assertTrue(outcome is SelectedTripPlanningOutcome.Planned)
        val option = (outcome as SelectedTripPlanningOutcome.Planned).option
        assertEquals(selectedTripRouteOptionId(constraint), option.id)
        assertEquals(constraint, option.selectedTripConstraint)
        assertEquals(listOf("LOOP"), option.tripIds)
        assertEquals(listOf(30, 40), option.stops.map { it.stopSequence })
        assertEquals(listOf("A", "C"), option.stops.map { it.id })
        assertEquals("Return", option.origin.label)
        assertEquals("Destination", option.destination.label)
        assertEquals(at("08:00:00"), option.departureEpochMillis)
        assertEquals(at("08:30:00"), option.arrivalEpochMillis)
        assertEquals(0, option.transfers)
        assertEquals(0, option.walkingMinutes)
        assertTrue(routeOptionCarriesSelectedTripConstraint(option))
    }

    @Test
    fun selectedTripIdentityDriftAndStopReferenceDriftReturnTypedConflicts() {
        val identity = selectedTripIdentity()
        val constraint = SelectedTripConstraint(
            identity, GtfsSelectedStopRef(30, "A"), GtfsSelectedStopRef(40, "C"),
        )
        val mapper = selectedTripMapper()

        val drifted = planSelectedTripFromDetail(
            selectedTripDetail(identity.copy(routeId = "different-route")),
            constraint, mapper, 1234L,
        ) as SelectedTripPlanningOutcome.Conflict
        assertEquals(SelectedTripConflictReason.IDENTITY_DRIFT, drifted.conflict.reason)
        assertEquals(
            SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP,
            drifted.conflict.alternativeAction,
        )

        val wrongStopRef = planSelectedTripFromDetail(
            selectedTripDetail(identity),
            constraint.copy(boarding = GtfsSelectedStopRef(30, "B")),
            mapper, 1234L,
        ) as SelectedTripPlanningOutcome.Conflict
        assertEquals(
            SelectedTripConflictReason.STOP_REFERENCE_MISMATCH,
            wrongStopRef.conflict.reason,
        )
    }

    @Test
    fun infeasibleOrUntimedSelectedStopRolesReturnTypedConflicts() {
        val identity = selectedTripIdentity()
        val constraint = SelectedTripConstraint(
            identity, GtfsSelectedStopRef(30, "A"), GtfsSelectedStopRef(40, "C"),
        )
        val detail = selectedTripDetail(identity)
        val forbiddenBoarding = detail.copy(stops = detail.stops.map { stop ->
            if (stop.stopSequence == 30) stop.copy(pickupType = 1) else stop
        })
        val rejectedBoarding = planSelectedTripFromDetail(
            forbiddenBoarding, constraint, selectedTripMapper(), 1234L,
        ) as SelectedTripPlanningOutcome.Conflict
        assertEquals(SelectedTripConflictReason.INVALID_STOP_ROLES, rejectedBoarding.conflict.reason)
        assertEquals(TripStopRoleProblem.BOARDING_NOT_ALLOWED, rejectedBoarding.conflict.roleProblem)

        val untimedDestination = detail.copy(stops = detail.stops.map { stop ->
            if (stop.stopSequence == 40) stop.copy(scheduledArrival = null) else stop
        })
        val missingTime = planSelectedTripFromDetail(
            untimedDestination, constraint, selectedTripMapper(), 1234L,
        ) as SelectedTripPlanningOutcome.Conflict
        assertEquals(SelectedTripConflictReason.INVALID_STOP_ROLES, missingTime.conflict.reason)
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            missingTime.conflict.roleProblem,
        )

        val reversed = planSelectedTripFromDetail(
            detail,
            constraint.copy(
                boarding = GtfsSelectedStopRef(40, "C"),
                destination = GtfsSelectedStopRef(30, "A"),
            ),
            selectedTripMapper(),
            1234L,
        ) as SelectedTripPlanningOutcome.Conflict
        assertEquals(SelectedTripConflictReason.INVALID_STOP_ROLES, reversed.conflict.reason)
    }

    @Test
    fun missingSelectedTripRouteEvidenceAndFrequencyTemplatesFailClosed() {
        val identity = selectedTripIdentity()
        val constraint = SelectedTripConstraint(
            identity, GtfsSelectedStopRef(30, "A"), GtfsSelectedStopRef(40, "C"),
        )
        val missingRoute = planSelectedTripFromDetail(
            selectedTripDetail(identity), constraint, selectedTripMapper(routePresent = false), 1234L,
        ) as SelectedTripPlanningOutcome.Conflict
        assertEquals(
            SelectedTripConflictReason.MISSING_SCHEDULE_EVIDENCE,
            missingRoute.conflict.reason,
        )

        val frequencyConstraint = constraint.copy(
            identity = identity.copy(frequencyStartSeconds = 28_800),
        )
        val unsupportedFrequency = planSelectedTripFromDetail(
            selectedTripDetail(identity.copy(frequencyStartSeconds = 28_800)),
            frequencyConstraint,
            selectedTripMapper(),
            1234L,
        ) as SelectedTripPlanningOutcome.Conflict
        assertEquals(
            SelectedTripConflictReason.FREQUENCY_INSTANCE_UNSUPPORTED,
            unsupportedFrequency.conflict.reason,
        )
    }

    @Test
    fun freshMatchedCancellationRequiresAnExplicitAlternative() {
        val constraint = SelectedTripConstraint(
            selectedTripIdentity(), GtfsSelectedStopRef(30, "A"), GtfsSelectedStopRef(40, "C"),
        )
        val cancelled = RouteRealtimeInfo(
            state = RouteRealtimeState.FRESH_MATCHED,
            matchedTripIds = listOf("LOOP"),
            cancelledTripIds = listOf("LOOP"),
        )
        val conflict = requireNotNull(selectedTripCancellationConflict(constraint, cancelled))
        assertEquals(SelectedTripConflictReason.CANCELLED, conflict.reason)
        assertEquals(SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP, conflict.alternativeAction)

        for (state in listOf(
            RouteRealtimeState.FRESH_NO_MATCH,
            RouteRealtimeState.STALE,
            RouteRealtimeState.UNAVAILABLE,
        )) {
            assertNull(selectedTripCancellationConflict(constraint, cancelled.copy(state = state)))
        }
        assertNull(selectedTripCancellationConflict(
            constraint, cancelled.copy(matchedTripIds = emptyList()),
        ))
    }

    @Test
    fun resolverFailuresMapToTypedSelectedTripConflicts() {
        assertEquals(
            SelectedTripConflictReason.IDENTITY_DRIFT,
            selectedTripConflictReason(GtfsTripDetailFailure.TRIP_CHANGED),
        )
        assertEquals(
            SelectedTripConflictReason.SERVICE_INACTIVE,
            selectedTripConflictReason(GtfsTripDetailFailure.SERVICE_INACTIVE),
        )
        assertEquals(
            SelectedTripConflictReason.FREQUENCY_INSTANCE_UNSUPPORTED,
            selectedTripConflictReason(GtfsTripDetailFailure.FREQUENCY_INSTANCE_UNSUPPORTED),
        )
        assertEquals(
            SelectedTripConflictReason.MISSING_SCHEDULE_EVIDENCE,
            selectedTripConflictReason(GtfsTripDetailFailure.INVALID_STOP_TIME),
        )
        assertEquals(
            SelectedTripConflictReason.DATA_UNAVAILABLE,
            selectedTripConflictReason(GtfsTripDetailFailure.DATA_READ_FAILED),
        )
    }

    private fun selectedTripIdentity() = GtfsTripInstanceIdentity(
        tripId = "LOOP",
        serviceDate = date,
        serviceId = "S",
        routeId = "R",
        directionId = 0,
    )

    private fun selectedTripDetail(identity: GtfsTripInstanceIdentity) = GtfsTripDetail(
        identity = identity,
        headsign = "Central",
        stops = listOf(
            GtfsTripDetailStop(
                10, "A", "Start", null, null, null,
                "07:00:00", "07:00:00", 52.0, 8.0, 0, 0,
            ),
            GtfsTripDetailStop(
                20, "B", "Intermediate", null, null, null,
                "07:10:00", "07:10:00", 52.1, 8.1, 0, 0,
            ),
            GtfsTripDetailStop(
                30, "A", "Return", null, null, null,
                "08:00:00", "08:00:00", 52.0, 8.0, 0, 0,
            ),
            GtfsTripDetailStop(
                40, "C", "Destination", null, null, null,
                "08:30:00", "08:30:00", 52.2, 8.2, 0, 0,
            ),
        ),
    )

    private fun selectedTripMapper(routePresent: Boolean = true): StaticRouteOptionMapper {
        val routes = if (routePresent) {
            mapOf("R" to GtfsScheduleRoute("R", "A1", "RE 9", "Regional", 2))
        } else {
            emptyMap()
        }
        val trips = mapOf("LOOP" to trip("LOOP", "R", "Central"))
        return StaticRouteOptionMapper(
            FakeRouteOptionData(routes, trips, emptyMap(), emptyMap()),
            zone,
        )
    }

    private fun at(raw: String): Long =
        GtfsServiceTime.parse(raw).resolve(date, zone).epochMillis

    private fun trip(id: String, routeId: String, headsign: String) =
        GtfsScheduleTrip(id, routeId, "S", headsign, 0, null, null, null)

    private fun stop(id: String, name: String) =
        GtfsScheduleStop(id, name, 0.0, 0.0, null, 0, null, null)

    private fun st(
        tripId: String,
        sequence: Int,
        stopId: String,
        arrival: String,
        departure: String,
    ) = GtfsScheduleStopTime(
        tripId,
        sequence,
        stopId,
        arrival,
        departure,
        null,
        null,
        null,
        1,
    )

    private class FakeRouteOptionData(
        private val routes: Map<String, GtfsScheduleRoute>,
        private val trips: Map<String, GtfsScheduleTrip>,
        private val stops: Map<String, GtfsScheduleStop>,
        private val stopTimes: Map<String, List<GtfsScheduleStopTime>>,
    ) : StaticRouteOptionData {
        override fun route(routeId: String) = routes[routeId]
        override fun trip(tripId: String) = trips[tripId]
        val stopLookupCounts = mutableMapOf<String, Int>()

        override fun stop(stopId: String): GtfsScheduleStop? {
            stopLookupCounts[stopId] = (stopLookupCounts[stopId] ?: 0) + 1
            return stops[stopId]
        }
        override fun stopTimesForTrip(tripId: String, limit: Int) =
            stopTimes[tripId].orEmpty().take(limit)
    }
}
