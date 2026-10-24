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
    private fun stop(sequence: Int, id: String): GtfsTripDetailStop {
        // Fixtures represent a chronological journey, not simultaneous stops.
        val minute = when (sequence) { 2 -> 0; 7, 50 -> 10; 13, 200 -> 20; else -> 0 }
        val arrivalMinute = minute.toString().padStart(2, '0')
        val departureMinute = (minute + 1).toString().padStart(2, '0')
        return GtfsTripDetailStop(
            stopSequence = sequence, stopId = id, name = id,
            parentStationId = null, platformCode = null, wheelchairBoarding = null,
            scheduledArrival = "08:${arrivalMinute}:00", scheduledDeparture = "08:${departureMinute}:00",
            latitude = 52.0, longitude = 8.0,
            pickupType = null, dropOffType = null,
        )
    }
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

    @Test fun boardingStopCannotBeAnArrivalAlarmTarget() {
        assertEquals(
            TripStopRoleProblem.WRONG_STOP_ORDER,
            rejected(detail, TripStopRoles(2, 13, 2)),
        )
        // A later stop within the journey remains a valid early alarm target.
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

    @Test fun missingScheduleClockCannotBecomeConfirmedStopRole() {
        val untimed = stop(7, "Transfer").copy(
            scheduledArrival = null, scheduledDeparture = null,
        )
        val missingBoarding = detail.copy(stops = listOf(
            stop(2, "Lohne").copy(scheduledArrival = null, scheduledDeparture = null),
            stop(7, "Transfer"), stop(13, "Achim"),
        ))
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(missingBoarding, TripStopRoles(2, 13, 13)),
        )
        val missingAlarm = detail.copy(stops = listOf(
            stop(2, "Lohne"), untimed, stop(13, "Achim"),
        ))
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(missingAlarm, TripStopRoles(2, 13, 7)),
        )
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(
                detail.copy(stops = detail.stops.dropLast(1) +
                    stop(13, "Achim").copy(scheduledArrival = null, scheduledDeparture = null)),
                TripStopRoles(2, 13, 13),
            ),
        )
        // A non-timepoint stop which is not a selected role can remain in the itinerary.
        assertTrue(
            TripStopRoleValidator.validate(missingAlarm, TripStopRoles(2, 13, 13))
                is TripStopRoleResult.Accepted,
        )
    }

    @Test fun invalidScheduleClockFailsClosedButOvernightServiceTimeIsAllowed() {
        val broken = detail.copy(stops = detail.stops.dropLast(1) +
            stop(13, "Achim").copy(scheduledArrival = "invalid", scheduledDeparture = "08:01:00"))
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(broken, TripStopRoles(2, 13, 13)),
        )
        val overnight = detail.copy(stops = detail.stops.mapIndexed { index, stop ->
            stop.copy(
                scheduledArrival = "24:${(10 + 10 * index).toString().padStart(2, '0')}:00",
                scheduledDeparture = "24:${(11 + 10 * index).toString().padStart(2, '0')}:00",
            )
        })
        assertTrue(
            TripStopRoleValidator.validate(overnight, TripStopRoles(2, 13, 13))
                is TripStopRoleResult.Accepted,
        )
    }


    @Test fun invertedProviderStopWindowNeverAuthorizesBoardingOrAlarm() {
        // A callback-provided detail may bypass the normal resolver chronology checks.
        // Consent still cannot use any selected role from an impossible dwell interval.
        for (sequence in listOf(2, 7, 13)) {
            val inverted = detail.copy(stops = detail.stops.map { stop ->
                if (stop.stopSequence == sequence) {
                    stop.copy(scheduledArrival = "08:20:00", scheduledDeparture = "08:10:00")
                } else stop
            })
            assertEquals(
                "Inverted clock at selected stop " + sequence,
                TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
                rejected(inverted, TripStopRoles(2, 13, 7)),
            )
        }

        // GTFS uses service-day seconds, not a naive 00:00 clock reset.
        val overnight = detail.copy(stops = listOf(
            stop(2, "Lohne").copy(scheduledArrival = "23:50:00", scheduledDeparture = "23:51:00"),
            stop(7, "Transfer").copy(scheduledArrival = "23:59:00", scheduledDeparture = "24:02:00"),
            stop(13, "Achim").copy(scheduledArrival = "24:10:00", scheduledDeparture = "24:11:00"),
        ))
        assertTrue(
            TripStopRoleValidator.validate(overnight, TripStopRoles(2, 13, 7))
                is TripStopRoleResult.Accepted,
        )
    }

    @Test fun selectedStopRoleChronologyRejectsImpossibleTrip() {
        // A valid dwell at each individual stop does not prove that the selected
        // boarding -> alarm -> destination sequence is chronologically feasible.
        val alarmBeforeBoarding = detail.copy(stops = detail.stops.map {
            if (it.stopSequence == 7) {
                it.copy(scheduledArrival = "07:58:00", scheduledDeparture = "07:59:00")
            } else it
        })
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(alarmBeforeBoarding, TripStopRoles(2, 13, 7)),
        )
        val destinationBeforeAlarm = detail.copy(stops = detail.stops.map {
            if (it.stopSequence == 13) {
                it.copy(scheduledArrival = "08:09:00", scheduledDeparture = "08:10:00")
            } else it
        })
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(destinationBeforeAlarm, TripStopRoles(2, 13, 7)),
        )
        // Equal service-second boundaries remain valid, including alarm=destination.
        val touching = detail.copy(stops = detail.stops.map {
            if (it.stopSequence == 7) {
                it.copy(scheduledArrival = "08:01:00", scheduledDeparture = "08:02:00")
            } else it
        })
        assertTrue(
            TripStopRoleValidator.validate(touching, TripStopRoles(2, 13, 7))
                is TripStopRoleResult.Accepted,
        )
    }

    @Test fun roleSpecificClocksRejectOppositeDirectionOnlySchedule() {
        val onlyArrivalAtBoarding = detail.copy(stops = detail.stops.map {
            if (it.stopSequence == 2) it.copy(scheduledDeparture = null) else it
        })
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(onlyArrivalAtBoarding, TripStopRoles(2, 13, 13)),
        )
        val onlyDepartureAtDestination = detail.copy(stops = detail.stops.map {
            if (it.stopSequence == 13) it.copy(scheduledArrival = null) else it
        })
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(onlyDepartureAtDestination, TripStopRoles(2, 13, 13)),
        )
        val onlyDepartureAtAlarm = detail.copy(stops = detail.stops.map {
            if (it.stopSequence == 7) it.copy(scheduledArrival = null) else it
        })
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(onlyDepartureAtAlarm, TripStopRoles(2, 13, 7)),
        )
    }

    @Test fun appropriateSingleDirectionClockStillConfirmsExplicitRoles() {
        val directionOnly = detail.copy(stops = listOf(
            stop(2, "Lohne").copy(scheduledArrival = null),
            stop(7, "Transfer").copy(scheduledDeparture = null),
            stop(13, "Achim").copy(scheduledDeparture = null),
        ))
        assertTrue(
            TripStopRoleValidator.validate(directionOnly, TripStopRoles(2, 13, 7))
                is TripStopRoleResult.Accepted,
        )
    }


    @Test fun plannedPhysicalStopCountUsesOccurrenceIndexesNotGtfsSequenceGap() {
        val loop = detail.copy(stops = listOf(
            stop(2, "Loop"), stop(50, "Loop"), stop(200, "Achim"),
        ))
        val earlyAlarm = GtfsSelectedTrip(
            identity,
            GtfsSelectedStopRef(2, "Loop"),
            GtfsSelectedStopRef(200, "Achim"),
            GtfsSelectedStopRef(50, "Loop"),
        )
        // Second visit to the same stopId is a distinct, physical GTFS stop occurrence.
        assertEquals(1, plannedPhysicalStopsFromBoardingToAlarm(earlyAlarm, loop))
        val destinationAlarm = GtfsSelectedTrip(
            identity,
            GtfsSelectedStopRef(2, "Loop"),
            GtfsSelectedStopRef(200, "Achim"),
            GtfsSelectedStopRef(200, "Achim"),
        )
        assertEquals(2, plannedPhysicalStopsFromBoardingToAlarm(destinationAlarm, loop))
    }

    @Test fun plannedCountFailsClosedOnIdentityRoleAndScheduleMismatch() {
        val chosen = GtfsSelectedTrip(
            identity,
            GtfsSelectedStopRef(2, "Lohne"),
            GtfsSelectedStopRef(13, "Achim"),
            GtfsSelectedStopRef(7, "Transfer"),
        )
        assertEquals(1, plannedPhysicalStopsFromBoardingToAlarm(chosen, detail))
        val badRole = GtfsSelectedTrip(
            identity, chosen.boarding, chosen.destination,
            GtfsSelectedStopRef(7, "wrong-physical-stop"),
        )
        assertEquals(null, plannedPhysicalStopsFromBoardingToAlarm(badRole, detail))
        val wrongIdentity = GtfsSelectedTrip(
            GtfsTripInstanceIdentity(
                tripId = "other-trip",
                serviceDate = GtfsServiceDate(2026, 10, 11),
                serviceId = "service-1", routeId = "route-2", directionId = 1,
            ),
            chosen.boarding, chosen.destination, chosen.alarmTarget,
        )
        assertEquals(null, plannedPhysicalStopsFromBoardingToAlarm(wrongIdentity, detail))
        val invalidAlarmClock = detail.copy(stops = detail.stops.map {
            if (it.stopSequence == 7) it.copy(scheduledArrival = null) else it
        })
        assertEquals(null, plannedPhysicalStopsFromBoardingToAlarm(chosen, invalidAlarmClock))
    }


    @Test fun unselectedIntermediateStopCannotReverseSelectedServiceDay() {
        val earlyIntermediate = detail.copy(stops = detail.stops.map { stop ->
            if (stop.stopSequence == 7) stop.copy(
                scheduledArrival = "07:59:00", scheduledDeparture = "08:00:00",
            ) else stop
        })
        // Boarding 08:01 and destination 08:20 appear correct independently.
        // An unrelated intermediate clock must not authorize an impossible trip.
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(earlyIntermediate, TripStopRoles(2, 13, 13)),
        )

        val lateIntermediate = detail.copy(stops = detail.stops.map { stop ->
            if (stop.stopSequence == 7) stop.copy(
                scheduledArrival = "08:25:00", scheduledDeparture = "08:26:00",
            ) else stop
        })
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(lateIntermediate, TripStopRoles(2, 13, 13)),
        )

        val malformedIntermediate = detail.copy(stops = detail.stops.map { stop ->
            if (stop.stopSequence == 7) stop.copy(
                scheduledArrival = "invalid", scheduledDeparture = "08:11:00",
            ) else stop
        })
        assertEquals(
            TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME,
            rejected(malformedIntermediate, TripStopRoles(2, 13, 13)),
        )

        // A non-timepoint intermediate stop is not a reason to reject an
        // otherwise valid, ordered service-day trip.
        val untimedIntermediate = detail.copy(stops = detail.stops.map { stop ->
            if (stop.stopSequence == 7) stop.copy(
                scheduledArrival = null, scheduledDeparture = null,
            ) else stop
        })
        assertTrue(
            TripStopRoleValidator.validate(untimedIntermediate, TripStopRoles(2, 13, 13))
                is TripStopRoleResult.Accepted,
        )
    }

}
