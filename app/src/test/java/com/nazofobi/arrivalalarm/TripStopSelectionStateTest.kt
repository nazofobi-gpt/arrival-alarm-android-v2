package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStopSelectionStateTest {
    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-7",
        serviceDate = GtfsServiceDate(2026, 10, 20),
        serviceId = "service-1",
        routeId = "route-2",
        directionId = 1,
    )

    private fun stop(sequence: Int, id: String): GtfsTripDetailStop {
        val minute = when (sequence) {
            2 -> 0
            7 -> 10
            13 -> 20
            15 -> 30
            else -> error("Unexpected fixture stop sequence: $sequence")
        }
        val arrival = minute.toString().padStart(2, '0')
        val departure = (minute + 1).toString().padStart(2, '0')
        return GtfsTripDetailStop(
            stopSequence = sequence,
            stopId = id,
            name = id,
            parentStationId = null,
            platformCode = null,
            wheelchairBoarding = null,
            scheduledArrival = "08:$arrival:00",
            scheduledDeparture = "08:$departure:00",
            latitude = 52.0,
            longitude = 8.0,
        )
    }

    private val detail = GtfsTripDetail(
        identity = identity,
        headsign = "Achim",
        stops = listOf(
            stop(2, "Lohne"),
            stop(7, "Transfer"),
            stop(13, "Achim"),
            stop(15, "Beyond destination"),
        ),
    )

    private fun initial(
        trip: GtfsTripDetail = detail,
        boardingSequence: Int = 2,
    ): TripStopSelectionState = TripStopSelectionReducer.initial(
        trip,
        trip.stops.single { it.stopSequence == boardingSequence },
    )

    @Test
    fun destinationMustBeStrictlyLaterAndBelongToTheSelectedTrip() {
        val sameStop = TripStopSelectionReducer.reduce(
            initial(),
            TripStopSelectionAction.SelectDestination(2),
        )
        assertEquals(TripStopRoleProblem.WRONG_STOP_ORDER, sameStop.roleProblem)
        assertTrue(TripStopSelectionReducer.draft(sameStop) is GtfsTripConsentDraft.Rejected)

        val reversed = TripStopSelectionReducer.reduce(
            initial(boardingSequence = 7),
            TripStopSelectionAction.SelectDestination(2),
        )
        assertEquals(TripStopRoleProblem.WRONG_STOP_ORDER, reversed.roleProblem)

        val outsideTrip = TripStopSelectionReducer.reduce(
            initial(),
            TripStopSelectionAction.SelectDestination(99),
        )
        assertEquals(TripStopRoleProblem.DESTINATION_STOP_NOT_FOUND, outsideTrip.roleProblem)

        val valid = TripStopSelectionReducer.reduce(
            initial(),
            TripStopSelectionAction.SelectDestination(13),
        )
        val draft = TripStopSelectionReducer.draft(valid) as GtfsTripConsentDraft.Ready
        assertEquals(GtfsSelectedStopRef(2, "Lohne"), draft.selection.boarding)
        assertEquals(GtfsSelectedStopRef(13, "Achim"), draft.selection.destination)
        assertEquals(draft.selection.destination, draft.selection.alarmTarget)
    }

    @Test
    fun alarmTargetMustStayInsideTheSelectedBoardingDestinationSegment() {
        val selected = TripStopSelectionReducer.reduce(
            initial(),
            TripStopSelectionAction.SelectDestination(13),
        )
        val beforeBoarding = TripStopSelectionReducer.reduce(
            selected,
            TripStopSelectionAction.SelectAlarmTarget(2),
        )
        assertEquals(TripStopRoleProblem.WRONG_STOP_ORDER, beforeBoarding.roleProblem)

        val afterDestination = TripStopSelectionReducer.reduce(
            selected,
            TripStopSelectionAction.SelectAlarmTarget(15),
        )
        assertEquals(TripStopRoleProblem.WRONG_STOP_ORDER, afterDestination.roleProblem)

        val valid = TripStopSelectionReducer.reduce(
            selected,
            TripStopSelectionAction.SelectAlarmTarget(7),
        )
        assertEquals(GtfsSelectedStopRef(7, "Transfer"), valid.alarmTarget)
        assertTrue(TripStopSelectionReducer.draft(valid) is GtfsTripConsentDraft.Ready)
    }

    @Test
    fun tripDirectionAndServiceDateChangesInvalidatePreviouslySelectedRoles() {
        val selected = TripStopSelectionReducer.reduce(
            TripStopSelectionReducer.reduce(
                initial(),
                TripStopSelectionAction.SelectDestination(13),
            ),
            TripStopSelectionAction.SelectAlarmTarget(7),
        )
        val changedTrips = listOf(
            detail.copy(identity = identity.copy(tripId = "trip-8")),
            detail.copy(identity = identity.copy(directionId = 2)),
            detail.copy(identity = identity.copy(serviceDate = GtfsServiceDate(2026, 10, 21))),
        )

        for (changed in changedTrips) {
            val replaced = TripStopSelectionReducer.reduce(
                selected,
                TripStopSelectionAction.ReplaceTrip(changed, changed.stops.first()),
            )
            assertEquals(changed.identity, replaced.detail.identity)
            assertNull(replaced.destination)
            assertNull(replaced.alarmTarget)
        }
    }

    @Test
    fun sameTripRefreshPreservesCompatibleRolesButInvalidatesChangedStopReferences() {
        val selected = TripStopSelectionReducer.reduce(
            TripStopSelectionReducer.reduce(
                initial(),
                TripStopSelectionAction.SelectDestination(13),
            ),
            TripStopSelectionAction.SelectAlarmTarget(7),
        )
        val displayRefresh = detail.copy(
            stops = detail.stops.map { it.copy(name = "Updated ${it.name}", platformCode = "2") },
        )
        val preserved = TripStopSelectionReducer.reduce(
            selected,
            TripStopSelectionAction.ReplaceTrip(displayRefresh, displayRefresh.stops.first()),
        )
        assertEquals(selected.destination, preserved.destination)
        assertEquals(selected.alarmTarget, preserved.alarmTarget)

        val changedDestination = detail.copy(
            stops = detail.stops.map {
                if (it.stopSequence == 13) it.copy(stopId = "new-achim-id") else it
            },
        )
        val invalidated = TripStopSelectionReducer.reduce(
            selected,
            TripStopSelectionAction.ReplaceTrip(changedDestination, changedDestination.stops.first()),
        )
        assertNull(invalidated.destination)
        assertNull(invalidated.alarmTarget)
        assertEquals(TripStopRoleProblem.DESTINATION_STOP_NOT_FOUND, invalidated.roleProblem)
    }

    @Test
    fun changingBoardingRoleClearsDestinationAndAlarmOverride() {
        val selected = TripStopSelectionReducer.reduce(
            TripStopSelectionReducer.reduce(
                initial(),
                TripStopSelectionAction.SelectDestination(13),
            ),
            TripStopSelectionAction.SelectAlarmTarget(7),
        )
        val replaced = TripStopSelectionReducer.reduce(
            selected,
            TripStopSelectionAction.ReplaceTrip(detail, detail.stops.single { it.stopSequence == 7 }),
        )
        assertEquals(GtfsSelectedStopRef(7, "Transfer"), replaced.boarding)
        assertNull(replaced.destination)
        assertNull(replaced.alarmTarget)
    }

    @Test
    fun clearingDestinationAlsoClearsAlarmOverride() {
        val selected = TripStopSelectionReducer.reduce(
            TripStopSelectionReducer.reduce(
                initial(),
                TripStopSelectionAction.SelectDestination(13),
            ),
            TripStopSelectionAction.SelectAlarmTarget(7),
        )
        val cleared = TripStopSelectionReducer.reduce(
            selected,
            TripStopSelectionAction.SelectDestination(null),
        )
        assertNull(cleared.destination)
        assertNull(cleared.alarmTarget)
        assertTrue(TripStopSelectionReducer.draft(cleared) is GtfsTripConsentDraft.Rejected)
    }
}
