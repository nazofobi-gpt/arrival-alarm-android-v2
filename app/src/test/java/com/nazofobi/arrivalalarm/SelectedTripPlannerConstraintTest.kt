package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTripPlannerConstraintTest {
    private val date = GtfsServiceDate.parse("2026-11-01")
    private val identity = GtfsTripInstanceIdentity("trip-a", date, "service-a", "route-a", 0)
    private val detail = GtfsTripDetail(
        identity,
        "Direction",
        listOf(
            stop(1, "stop-a", "A", "08:00:00", "08:00:00"),
            stop(2, "loop", "B", "08:05:00", "08:06:00"),
            stop(3, "loop", "C", "08:10:00", "08:11:00"),
            stop(4, "stop-d", "D", "08:15:00", "08:15:00"),
        ),
    )
    private val selection = GtfsSelectedTrip(
        identity,
        GtfsSelectedStopRef(2, "loop"),
        GtfsSelectedStopRef(4, "stop-d"),
        GtfsSelectedStopRef(3, "loop"),
    )
    private val evidence = SelectedTripCancellationEvidence(1L, emptySet())

    @Test
    fun exactTripAndStopReferencesAreAccepted() {
        val result = SelectedTripPlannerConstraint.validate(selection, detail, evidence)
        assertTrue(result is SelectedTripPlannerValidation.Accepted)
    }

    @Test
    fun missingCancellationEvidenceFailsClosed() {
        val result = SelectedTripPlannerConstraint.validate(selection, detail, null)
            as SelectedTripPlannerValidation.Rejected
        assertEquals(SelectedTripPlannerConflictReason.MISSING_CANCELLATION_EVIDENCE, result.reason)
    }

    @Test
    fun cancelledTripIsRejected() {
        val cancelled = evidence.copy(cancelledTripIds = setOf("trip-a"))
        val result = SelectedTripPlannerConstraint.validate(selection, detail, cancelled)
            as SelectedTripPlannerValidation.Rejected
        assertEquals(SelectedTripPlannerConflictReason.SELECTED_TRIP_CANCELLED, result.reason)
    }

    @Test
    fun loopOccurrencesRetainDistinctSequenceIds() {
        val roles = (TripStopRoleValidator.validate(
            detail,
            TripStopRoles(2, 4, 3),
        ) as TripStopRoleResult.Accepted).roles
        val option = SelectedTripRouteOptionMapper(
            java.util.TimeZone.getTimeZone("UTC"),
            1.2,
        ).map(
            MapPoint(0.0, 0.0, "Origin"),
            MapPoint(0.03, 0.03, "Destination"),
            selection,
            detail,
            roles,
            "Line 1",
            1L,
        )!!
        assertEquals(listOf("2:loop", "3:loop", "4:stop-d"), option.stops.map { it.id })
    }

    private fun stop(sequence: Int, id: String, name: String, arrival: String, departure: String) =
        GtfsTripDetailStop(
            sequence, id, name, null, null, null, arrival, departure,
            sequence * 0.01, sequence * 0.01, 0, 0,
        )
}
