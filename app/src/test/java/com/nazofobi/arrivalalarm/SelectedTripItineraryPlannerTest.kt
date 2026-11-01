package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTripItineraryPlannerTest {
    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val date = GtfsServiceDate(2026, 10, 20)
    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-7",
        serviceDate = date,
        serviceId = "service-1",
        routeId = "route-2",
        directionId = 1,
    )
    private val route = GtfsScheduleRoute(
        id = "route-2",
        agencyId = null,
        shortName = "RB 58",
        longName = "Regionalbahn",
        routeType = 2,
    )
    private val detail = GtfsTripDetail(
        identity = identity,
        headsign = "Achim",
        stops = listOf(
            stop(2, "Lohne", "08:00:00", "08:01:00"),
            stop(7, "Transfer", "08:10:00", "08:11:00"),
            stop(13, "Achim", "08:20:00", "08:21:00"),
            stop(15, "Beyond destination", "08:30:00", "08:31:00"),
        ),
    )
    private val selection = GtfsSelectedTrip(
        identity = identity,
        boarding = GtfsSelectedStopRef(2, "Lohne"),
        destination = GtfsSelectedStopRef(13, "Achim"),
        alarmTarget = GtfsSelectedStopRef(7, "Transfer"),
    )

    private fun stop(
        sequence: Int,
        id: String,
        arrival: String?,
        departure: String?,
    ) = GtfsTripDetailStop(
        stopSequence = sequence,
        stopId = id,
        name = id,
        parentStationId = null,
        platformCode = null,
        wheelchairBoarding = null,
        scheduledArrival = arrival,
        scheduledDeparture = departure,
        latitude = 52.0,
        longitude = 8.0,
    )

    private fun planner(
        ready: Boolean = true,
        resolve: (GtfsTripInstanceIdentity) -> GtfsTripDetailResult = {
            GtfsTripDetailResult.Available(detail)
        },
        routeLookup: (String) -> GtfsScheduleRoute? = { id ->
            if (id == route.id) route else null
        },
        sourceUpdatedAt: Long = 1_793_507_777L,
    ) = SelectedTripItineraryPlanner(
        indexReady = { ready },
        resolveDetail = resolve,
        routeLookup = routeLookup,
        sourceUpdatedAtEpochSeconds = { sourceUpdatedAt },
        agencyTimeZone = zone,
    )

    @Test
    fun planPreservesExactTripAndOrderedBoardingDestinationAlarmRoles() {
        val outcome = planner().plan(selection)
        assertTrue(outcome is SelectedTripPlanOutcome.Planned)
        val planned = outcome as SelectedTripPlanOutcome.Planned

        assertEquals(selection, planned.selection)
        assertEquals(identity, planned.selection.identity)
        assertEquals(listOf("trip-7"), planned.routeOption.tripIds)
        assertEquals(listOf("Lohne", "Transfer", "Achim"), planned.routeOption.stops.map { it.name })
        assertEquals("RB 58", planned.routeOption.line)
        assertEquals("Achim", planned.routeOption.direction)
        assertEquals("08:01", planned.routeOption.departure)
        assertEquals("08:20", planned.routeOption.arrival)
        assertEquals(0, planned.routeOption.transfers)
        assertEquals(0, planned.routeOption.walkingMinutes)
        assertEquals(
            GtfsServiceTime.parse("08:01:00").resolve(date, zone).epochMillis,
            planned.routeOption.departureEpochMillis,
        )
        assertEquals(
            GtfsServiceTime.parse("08:20:00").resolve(date, zone).epochMillis,
            planned.routeOption.arrivalEpochMillis,
        )
        assertEquals(1_793_507_777L, planned.routeOption.sourceUpdatedAtEpochSeconds)
        assertTrue(planned.routeOption.stops.first().plannedDeparture!!.contains("T08:01:00"))
        assertTrue(planned.routeOption.stops.last().plannedArrival!!.contains("T08:20:00"))
    }

    @Test
    fun freshMatchedCancellationReturnsTypedConflictAndExplicitAlternative() {
        val realtime = RouteRealtimeInfo(
            state = RouteRealtimeState.FRESH_MATCHED,
            matchedTripIds = listOf(identity.tripId),
            cancelledTripIds = listOf(identity.tripId),
        )

        val outcome = planner().plan(selection, realtime)
        assertTrue(outcome is SelectedTripPlanOutcome.Conflict)
        val conflict = outcome as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.TRIP_CANCELLED, conflict.reason)
        assertEquals(
            SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP,
            conflict.alternativeAction,
        )
    }

    @Test
    fun staleCancellationCannotReplaceStaticSelectedTrip() {
        val realtime = RouteRealtimeInfo(
            state = RouteRealtimeState.STALE,
            cancelledTripIds = listOf(identity.tripId),
        )

        val outcome = planner().plan(selection, realtime)
        assertTrue(outcome is SelectedTripPlanOutcome.Planned)
        assertEquals(
            RouteRealtimeState.STALE,
            (outcome as SelectedTripPlanOutcome.Planned).routeOption.realtime?.state,
        )
    }

    @Test
    fun identityDriftRejectsBeforeLookingUpAnyAlternativeRoute() {
        val lookups = mutableListOf<String>()
        val changed = detail.copy(identity = identity.copy(routeId = "other-route"))

        val outcome = planner(
            resolve = { GtfsTripDetailResult.Available(changed) },
            routeLookup = { id -> lookups += id; route },
        ).plan(selection)

        assertTrue(outcome is SelectedTripPlanOutcome.Conflict)
        assertEquals(
            SelectedTripPlanFailure.TRIP_IDENTITY_DRIFT,
            (outcome as SelectedTripPlanOutcome.Conflict).reason,
        )
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun unavailableSelectedTripReturnsConflictWithoutGenericSearchFallback() {
        val lookups = mutableListOf<String>()
        val outcome = planner(
            resolve = { GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.TRIP_NOT_FOUND) },
            routeLookup = { id -> lookups += id; route },
        ).plan(selection)

        assertTrue(outcome is SelectedTripPlanOutcome.Conflict)
        assertEquals(
            SelectedTripPlanFailure.TRIP_NOT_FOUND,
            (outcome as SelectedTripPlanOutcome.Conflict).reason,
        )
        assertEquals(emptyList<String>(), lookups)
    }

    @Test
    fun sameOrReversedStopRolesAreRejectedWithTypedRoleProblem() {
        val sameStop = selection.copy(destination = selection.boarding)
        val sameOutcome = planner().plan(sameStop) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.INVALID_STOP_ROLES, sameOutcome.reason)
        assertEquals(TripStopRoleProblem.WRONG_STOP_ORDER, sameOutcome.roleProblem)

        val reversed = selection.copy(
            boarding = GtfsSelectedStopRef(13, "Achim"),
            destination = GtfsSelectedStopRef(2, "Lohne"),
            alarmTarget = GtfsSelectedStopRef(7, "Transfer"),
        )
        val reversedOutcome = planner().plan(reversed) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.INVALID_STOP_ROLES, reversedOutcome.reason)
        assertEquals(TripStopRoleProblem.WRONG_STOP_ORDER, reversedOutcome.roleProblem)
    }

    @Test
    fun selectedStopReferenceMustMatchBothSequenceAndStopId() {
        val changed = selection.copy(
            destination = GtfsSelectedStopRef(13, "different-stop-id"),
        )

        val outcome = planner().plan(changed) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.STOP_REFERENCE_CHANGED, outcome.reason)
        assertEquals(
            SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP,
            outcome.alternativeAction,
        )
    }

    @Test
    fun missingIndexRouteOrSourceFreshnessFailsClosed() {
        val noIndex = planner(ready = false).plan(selection) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.INDEX_NOT_READY, noIndex.reason)

        val noRoute = planner(routeLookup = { null }).plan(selection) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.ROUTE_NOT_FOUND, noRoute.reason)

        val noFreshness = planner(sourceUpdatedAt = 0L).plan(selection) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.SOURCE_FRESHNESS_MISSING, noFreshness.reason)
    }

    @Test
    fun inactiveServiceAndFrequencyTemplateRemainTypedConflicts() {
        val inactive = planner(
            resolve = { GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.SERVICE_INACTIVE) },
        ).plan(selection) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.SERVICE_INACTIVE, inactive.reason)

        val frequencySelection = selection.copy(
            identity = identity.copy(frequencyStartSeconds = 3_600),
        )
        val frequency = planner().plan(frequencySelection) as SelectedTripPlanOutcome.Conflict
        assertEquals(SelectedTripPlanFailure.FREQUENCY_INSTANCE_UNSUPPORTED, frequency.reason)
    }

    @Test
    fun repeatedStopIdUsesTheSelectedSequenceOccurrence() {
        val loopDetail = detail.copy(
            stops = listOf(
                stop(2, "Loop", "08:00:00", "08:01:00"),
                stop(7, "Middle", "08:10:00", "08:11:00"),
                stop(13, "Loop", "08:20:00", "08:21:00"),
                stop(15, "End", "08:30:00", "08:31:00"),
            ),
        )
        val loopSelection = GtfsSelectedTrip(
            identity = identity,
            boarding = GtfsSelectedStopRef(2, "Loop"),
            destination = GtfsSelectedStopRef(13, "Loop"),
            alarmTarget = GtfsSelectedStopRef(7, "Middle"),
        )

        val planned = planner(
            resolve = { GtfsTripDetailResult.Available(loopDetail) },
        ).plan(loopSelection) as SelectedTripPlanOutcome.Planned

        assertEquals(listOf("Loop", "Middle", "Loop"), planned.routeOption.stops.map { it.id })
        assertEquals(2, planned.selection.boarding.sequence)
        assertEquals(13, planned.selection.destination.sequence)
        assertEquals("Loop", planned.routeOption.origin.label)
        assertEquals("Loop", planned.routeOption.destination.label)
    }

    @Test
    fun serviceDayTimesBeyondMidnightResolveOnTheSelectedDate() {
        val overnightDate = GtfsServiceDate(2026, 10, 25)
        val overnightIdentity = identity.copy(serviceDate = overnightDate)
        val overnightDetail = GtfsTripDetail(
            identity = overnightIdentity,
            headsign = "Overnight destination",
            stops = listOf(
                stop(2, "Start", "23:55:00", "24:01:00"),
                stop(7, "Middle", "24:05:00", "24:06:00"),
                stop(13, "Destination", "24:10:00", "24:11:00"),
            ),
        )
        val overnightSelection = GtfsSelectedTrip(
            identity = overnightIdentity,
            boarding = GtfsSelectedStopRef(2, "Start"),
            destination = GtfsSelectedStopRef(13, "Destination"),
            alarmTarget = GtfsSelectedStopRef(7, "Middle"),
        )

        val planned = planner(
            resolve = { GtfsTripDetailResult.Available(overnightDetail) },
        ).plan(overnightSelection) as SelectedTripPlanOutcome.Planned

        assertEquals(
            GtfsServiceTime.parse("24:01:00").resolve(overnightDate, zone).epochMillis,
            planned.routeOption.departureEpochMillis,
        )
        assertEquals(
            GtfsServiceTime.parse("24:10:00").resolve(overnightDate, zone).epochMillis,
            planned.routeOption.arrivalEpochMillis,
        )
        assertEquals("00:01", planned.routeOption.departure)
        assertEquals("00:10", planned.routeOption.arrival)
    }
}
