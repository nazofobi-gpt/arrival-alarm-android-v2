package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTripAlarmArmGateTest {
    private val date = GtfsServiceDate(2026, 10, 20)
    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-7",
        serviceDate = date,
        serviceId = "service-1",
        routeId = "route-2",
        directionId = 1,
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
    private val verified = GtfsSelectionRestore.Verified(selection, detail)

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
        latitude = 52.0 + sequence * 0.001,
        longitude = 8.0 + sequence * 0.001,
    )

    private fun journey(
        phase: JourneyPhase = JourneyPhase.DESTINATION_SELECTED,
        start: GeoPoint = GeoPoint(52.002, 8.002),
        destination: GeoPoint = GeoPoint(52.007, 8.007),
    ) = JourneyUiState(
        phase = phase,
        start = start,
        destination = destination,
    )

    @Test
    fun emptySelectionKeepsManualJourneyPath() {
        assertEquals(
            SelectedTripAlarmArmDecision.ManualJourney,
            SelectedTripAlarmArmGate.evaluate(GtfsSelectionRestore.Empty, journey()),
        )
    }

    @Test
    fun verifiedSelectionAllowsJourneyTargetAtExplicitIntermediateAlarmStop() {
        val decision = SelectedTripAlarmArmGate.evaluate(verified, journey())

        assertTrue(decision is SelectedTripAlarmArmDecision.Ready)
        assertEquals(
            selection.alarmTarget.stopId,
            (decision as SelectedTripAlarmArmDecision.Ready).roles.alarmTarget.stopId,
        )
    }

    @Test
    fun journeyDestinationMustMatchAlarmTargetRatherThanTripDestination() {
        val decision = SelectedTripAlarmArmGate.evaluate(
            verified,
            journey(destination = GeoPoint(52.013, 8.013)),
        )

        assertEquals(
            SelectedTripAlarmArmProblem.JOURNEY_ALARM_TARGET_MISMATCH,
            (decision as SelectedTripAlarmArmDecision.Blocked).problem,
        )
    }

    @Test
    fun journeyOriginMustMatchConfirmedBoardingStop() {
        val decision = SelectedTripAlarmArmGate.evaluate(
            verified,
            journey(start = GeoPoint(52.003, 8.003)),
        )

        assertEquals(
            SelectedTripAlarmArmProblem.JOURNEY_ORIGIN_MISMATCH,
            (decision as SelectedTripAlarmArmDecision.Blocked).problem,
        )
    }

    @Test
    fun alarmTargetOutsideSelectedSegmentCannotBeResolved() {
        val invalid = selection.copy(alarmTarget = GtfsSelectedStopRef(15, "Beyond destination"))

        val decision = SelectedTripAlarmArmGate.resolve(
            GtfsSelectionRestore.Verified(invalid, detail),
        )

        assertEquals(
            SelectedTripAlarmArmProblem.INVALID_STOP_ROLES,
            (decision as SelectedTripAlarmArmDecision.Blocked).problem,
        )
    }

    @Test
    fun changedStopIdAtSameSequenceCannotInheritConfirmation() {
        val changed = selection.copy(alarmTarget = GtfsSelectedStopRef(7, "Different stop"))

        val decision = SelectedTripAlarmArmGate.resolve(
            GtfsSelectionRestore.Verified(changed, detail),
        )

        assertEquals(
            SelectedTripAlarmArmProblem.ALARM_TARGET_REFERENCE_MISMATCH,
            (decision as SelectedTripAlarmArmDecision.Blocked).problem,
        )
    }

    @Test
    fun changedTripIdentityCannotInheritConfirmation() {
        val changedDetail = detail.copy(identity = identity.copy(routeId = "other-route"))

        val decision = SelectedTripAlarmArmGate.resolve(
            GtfsSelectionRestore.Verified(selection, changedDetail),
        )

        assertEquals(
            SelectedTripAlarmArmProblem.IDENTITY_MISMATCH,
            (decision as SelectedTripAlarmArmDecision.Blocked).problem,
        )
    }

    @Test
    fun rejectedPersistedSelectionCannotArm() {
        val decision = SelectedTripAlarmArmGate.evaluate(
            GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED),
            journey(),
        )

        assertEquals(
            SelectedTripAlarmArmProblem.RESTORE_REJECTED,
            (decision as SelectedTripAlarmArmDecision.Blocked).problem,
        )
    }

    @Test
    fun selectedTripCannotArmFromAnUnarmedOrIncompleteJourneyPhase() {
        val decision = SelectedTripAlarmArmGate.evaluate(
            verified,
            journey(phase = JourneyPhase.START_SELECTED),
        )

        assertEquals(
            SelectedTripAlarmArmProblem.JOURNEY_NOT_READY,
            (decision as SelectedTripAlarmArmDecision.Blocked).problem,
        )
    }
}
