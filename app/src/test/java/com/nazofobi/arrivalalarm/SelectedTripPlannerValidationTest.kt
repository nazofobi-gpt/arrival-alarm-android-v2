package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedTripPlannerValidationTest {
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
    private val origin = MapPoint(0.02, 0.02, "Origin")
    private val destination = MapPoint(0.04, 0.04, "Destination")

    @Test
    fun changedStopIdIsNotAcceptedForTheSameSequence() {
        val changed = selection.copy(boarding = GtfsSelectedStopRef(2, "other"))
        val result = planner().plan(origin, destination, changed, evidence)
            as SelectedTripPlannerOutcome.Conflict
        assertEquals(SelectedTripPlannerConflictReason.STOP_REFERENCE_CHANGED, result.reason)
    }

    @Test
    fun impossibleRoleOrderReturnsTypedConflict() {
        val changed = selection.copy(
            destination = GtfsSelectedStopRef(2, "loop"),
            alarmTarget = GtfsSelectedStopRef(3, "loop"),
        )
        val result = planner().plan(origin, destination, changed, evidence)
            as SelectedTripPlannerOutcome.Conflict
        assertEquals(SelectedTripPlannerConflictReason.INFEASIBLE_STOP_ROLES, result.reason)
        assertEquals(TripStopRoleProblem.WRONG_STOP_ORDER, result.roleProblem)
    }

    @Test
    fun frequencyTemplateCannotMasqueradeAsConcreteTripInstance() {
        val frequencyIdentity = identity.copy(frequencyStartSeconds = 300)
        val result = planner(
            resolve = {
                GtfsTripDetailResult.Unavailable(
                    GtfsTripDetailFailure.FREQUENCY_INSTANCE_UNSUPPORTED,
                )
            },
        ).plan(
            origin,
            destination,
            selection.copy(identity = frequencyIdentity),
            evidence,
        ) as SelectedTripPlannerOutcome.Conflict
        assertEquals(
            SelectedTripPlannerConflictReason.FREQUENCY_INSTANCE_UNSUPPORTED,
            result.reason,
        )
    }

    @Test
    fun absentScheduleTimestampFailsClosed() {
        val result = planner(sourceUpdatedAt = 0L)
            .plan(origin, destination, selection, evidence) as SelectedTripPlannerOutcome.Conflict
        assertEquals(SelectedTripPlannerConflictReason.MISSING_SCHEDULE_EVIDENCE, result.reason)
    }

    private fun planner(
        resolve: (GtfsTripInstanceIdentity) -> GtfsTripDetailResult = {
            GtfsTripDetailResult.Available(detail)
        },
        sourceUpdatedAt: Long = 10L,
    ) = SelectedTripPlanner(
        resolveDetail = resolve,
        sourceUpdatedAtEpochSeconds = { sourceUpdatedAt },
        routeLabel = { "Line 1" },
        alternativesPlanner = SelectedTripAlternativePlanner { _, _, _ ->
            LocalStaticJourneyOutcome.Results(emptyList())
        },
        timeZone = TimeZone.getTimeZone("UTC"),
        walkingMetersPerSecond = 1.2,
    )

    private fun stop(sequence: Int, id: String, name: String, arrival: String, departure: String) =
        GtfsTripDetailStop(
            sequence, id, name, null, null, null, arrival, departure,
            sequence * 0.01, sequence * 0.01, 0, 0,
        )
}
