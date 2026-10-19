package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStopRoleValidatorTest {
    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-7",
        serviceDate = GtfsServiceDate(2026, 10, 11),
        serviceId = "service-1",
        routeId = "route-2",
        directionId = 1,
    )
    private fun stop(sequence: Int, id: String) = GtfsTripDetailStop(
        stopSequence = sequence, stopId = id, name = id,
        parentStationId = null, platformCode = null, wheelchairBoarding = null,
        scheduledArrival = "08:00:00", scheduledDeparture = "08:01:00",
        latitude = 52.0, longitude = 8.0,
        pickupType = null, dropOffType = null,
    )
    private val detail = GtfsTripDetail(
        identity, "Achim",
        listOf(stop(2, "Lohne"), stop(7, "Transfer"), stop(13, "Achim")),
    )
    private fun rejected(detail: GtfsTripDetail, roles: TripStopRoles) =
        (TripStopRoleValidator.validate(detail, roles) as TripStopRoleResult.Rejected).problem

    @Test fun exactTripAndAlarmStopInsideTravelledSegment() {
        val selected = TripStopRoleValidator.validate(detail, TripStopRoles(2, 13, 7))
        assertTrue(selected is TripStopRoleResult.Accepted)
        val roles = (selected as TripStopRoleResult.Accepted).roles
        assertEquals(identity, roles.identity)
        assertEquals("Transfer", roles.alarmTarget.stopId)
    }

    @Test fun loopTripIsSelectedBySequenceNotAmbiguousStopId() {
        val loop = detail.copy(
            stops = listOf(stop(2, "Lohne"), stop(7, "Lohne"), stop(13, "Achim"))
        )
        val accepted = TripStopRoleValidator.validate(loop, TripStopRoles(7, 13, 13))
        assertEquals(
            7,
            (accepted as TripStopRoleResult.Accepted).roles.boarding.stopSequence,
        )
    }

    @Test fun missingAndReversedRolesCannotChooseAnotherTripStop() {
        assertEquals(
            TripStopRoleProblem.BOARDING_STOP_NOT_FOUND,
            rejected(detail, TripStopRoles(3, 13, 7)),
        )
        assertEquals(
            TripStopRoleProblem.WRONG_STOP_ORDER,
            rejected(detail, TripStopRoles(13, 2, 7)),
        )
        assertEquals(
            TripStopRoleProblem.WRONG_STOP_ORDER,
            rejected(detail, TripStopRoles(7, 13, 2)),
        )
    }

    @Test fun alarmCannotTargetBoardingStopEvenWhenDestinationIsLater() {
        // An arrival alarm armed at the departure stop could fire immediately.
        assertEquals(
            TripStopRoleProblem.WRONG_STOP_ORDER,
            rejected(detail, TripStopRoles(2, 13, 2)),
        )
        // A later intermediate stop remains an explicit and valid alarm target.
        assertTrue(
            TripStopRoleValidator.validate(detail, TripStopRoles(2, 13, 7))
                is TripStopRoleResult.Accepted,
        )
    }

    @Test fun gtfsNoPickupAndRequestOnlyDropOffAreNotSilentlyAllowed() {
        val noPickup = detail.copy(
            stops = listOf(stop(2, "Lohne").copy(pickupType = 1)) + detail.stops.drop(1)
        )
        assertEquals(
            TripStopRoleProblem.BOARDING_NOT_ALLOWED,
            rejected(noPickup, TripStopRoles(2, 13, 7)),
        )
        val requestDrop = detail.copy(
            stops = detail.stops.dropLast(1) +
                detail.stops.last().copy(dropOffType = 2)
        )
        assertEquals(
            TripStopRoleProblem.ALIGHTING_NOT_ALLOWED,
            rejected(requestDrop, TripStopRoles(2, 13, 13)),
        )
    }

    @Test fun invalidProviderOrderAndCoordinatesFailClosed() {
        assertEquals(
            TripStopRoleProblem.INVALID_STOP_SEQUENCE,
            rejected(
                detail.copy(stops = listOf(stop(2, "a"), stop(2, "b"), stop(13, "c"))),
                TripStopRoles(2, 13, 13),
            ),
        )
        assertEquals(
            TripStopRoleProblem.INVALID_STOP_SEQUENCE,
            rejected(
                detail.copy(stops = listOf(
                    stop(2, "a"), stop(7, "b").copy(latitude = Double.NaN), stop(13, "c")
                )),
                TripStopRoles(2, 13, 13),
            ),
        )
    }
}
