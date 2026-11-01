package com.nazofobi.arrivalalarm

import java.util.TimeZone
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
    fun acceptsExactIdentityAndOrderedReferences() {
        val result = SelectedTripPlannerConstraint.validate(selection, detail, evidence)
        assertTrue(result is SelectedTripPlannerValidation.Accepted)
    }

    @Test
    fun missingCancellationSnapshotFailsClosed() {
        val result = SelectedTripPlannerConstraint.validate(selection, detail, null)
            as SelectedTripPlannerValidation.Rejected
        assertEquals(SelectedTripPlannerConflictReason.MISSING_CANCELLATION_EVIDENCE, result.reason)
    }

    @Test
    fun cancellationIsMatchedToTheFullTripInstance() {
        val cancelled = evidence.copy(cancelledTripInstances = setOf(identity))
        val rejected = SelectedTripPlannerConstraint.validate(selection, detail, cancelled)
            as SelectedTripPlannerValidation.Rejected
        assertEquals(SelectedTripPlannerConflictReason.SELECTED_TRIP_CANCELLED, rejected.reason)

        val otherDate = identity.copy(serviceDate = GtfsServiceDate.parse("2026-11-02"))
        val accepted = SelectedTripPlannerConstraint.validate(
            selection,
            detail,
            evidence.copy(cancelledTripInstances = setOf(otherDate)),
        )
        assertTrue(accepted is SelectedTripPlannerValidation.Accepted)
    }

    @Test
    fun safeAlternativesExcludeSelectedCancelledAndIdentitylessOptions() {
        val origin = MapPoint(0.01, 0.01, "Origin")
        val destination = MapPoint(0.04, 0.04, "Destination")
        fun option(id: String, tripIds: List<String>) = RouteOption(
            id, origin, destination, "Line", "Direction", "08:00", "08:15", 0, 0, tripIds,
        )
        val cancelled = identity.copy(tripId = "trip-b")
        val alternatives = SelectedTripPlannerConstraint.safeAlternatives(
            listOf(
                option("selected", listOf("trip-a")),
                option("cancelled", listOf("trip-b")),
                option("other", listOf("trip-c")),
                option("unknown", emptyList()),
            ),
            selection,
            evidence.copy(cancelledTripInstances = setOf(cancelled)),
        )
        assertEquals(listOf("other"), alternatives.map { it.id })
    }

    @Test
    fun loopOccurrencesKeepSequenceInRouteStopIdentity() {
        val roles = (TripStopRoleValidator.validate(
            detail,
            TripStopRoles(2, 4, 3),
        ) as TripStopRoleResult.Accepted).roles
        val option = SelectedTripRouteOptionMapper(TimeZone.getTimeZone("UTC"), 1.2).map(
            origin = MapPoint(0.02, 0.02, "Origin"),
            destination = MapPoint(0.04, 0.04, "Destination"),
            selection = selection,
            detail = detail,
            roles = roles,
            line = "Line",
            sourceUpdatedAtEpochSeconds = 1L,
        )!!
        assertEquals(listOf("2:loop", "3:loop", "4:stop-d"), option.stops.map { it.id })
    }

    private fun stop(sequence: Int, id: String, name: String, arrival: String, departure: String) =
        GtfsTripDetailStop(
            sequence, id, name, null, null, null, arrival, departure,
            sequence * 0.01, sequence * 0.01, 0, 0,
        )
}
