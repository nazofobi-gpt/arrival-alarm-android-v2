package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTripPlannerIntegrationTest {
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
    fun validSelectionReturnsItsOwnTripOption() {
        val result = planner().plan(origin, destination, selection, evidence)
            as SelectedTripPlannerOutcome.Selected
        assertEquals(selection, result.selection)
        assertEquals(listOf("trip-a"), result.option.tripIds)
        assertEquals(listOf("2:loop", "3:loop", "4:stop-d"), result.option.stops.map { it.id })
    }

    @Test
    fun cancellationConflictOffersOnlyDifferentVerifiedTrips() {
        val alternatives = LocalStaticJourneyOutcome.Results(
            listOf(
                option("selected", listOf("trip-a")),
                option("cancelled", listOf("trip-b")),
                option("alternative", listOf("trip-c")),
            ),
        )
        val result = planner(alternatives = alternatives).plan(
            origin,
            destination,
            selection,
            evidence.copy(cancelledTripIds = setOf("trip-a", "trip-b")),
        ) as SelectedTripPlannerOutcome.Conflict

        assertEquals(SelectedTripPlannerConflictReason.SELECTED_TRIP_CANCELLED, result.reason)
        assertEquals(listOf("alternative"), result.alternatives.map { it.id })
    }

    @Test
    fun changedIdentityIsNotReboundToTheNewTrip() {
        val changed = detail.copy(identity = identity.copy(serviceId = "changed-service"))
        val result = planner(
            resolve = { GtfsTripDetailResult.Available(changed) },
            alternatives = LocalStaticJourneyOutcome.Results(listOf(option("alternative", listOf("trip-c")))),
        ).plan(origin, destination, selection, evidence) as SelectedTripPlannerOutcome.Conflict

        assertEquals(SelectedTripPlannerConflictReason.IDENTITY_DRIFT, result.reason)
        assertEquals(listOf("alternative"), result.alternatives.map { it.id })
    }

    @Test
    fun missingDetailReturnsTypedConflictAndKeepsAlternativesSeparate() {
        val result = planner(
            resolve = {
                GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.MISSING_STOP_METADATA)
            },
            alternatives = LocalStaticJourneyOutcome.Results(listOf(option("alternative", listOf("trip-c")))),
        ).plan(origin, destination, selection, evidence) as SelectedTripPlannerOutcome.Conflict

        assertEquals(SelectedTripPlannerConflictReason.MISSING_SCHEDULE_EVIDENCE, result.reason)
        assertEquals(GtfsTripDetailFailure.MISSING_STOP_METADATA, result.detailFailure)
        assertEquals(listOf("alternative"), result.alternatives.map { it.id })
    }

    private fun planner(
        resolve: (GtfsTripInstanceIdentity) -> GtfsTripDetailResult = {
            GtfsTripDetailResult.Available(detail)
        },
        alternatives: LocalStaticJourneyOutcome = LocalStaticJourneyOutcome.Results(emptyList()),
    ) = SelectedTripPlanner(
        resolveDetail = resolve,
        sourceUpdatedAtEpochSeconds = { 10L },
        routeLabel = { "Line 1" },
        alternativesPlanner = SelectedTripAlternativePlanner { _, _, _ -> alternatives },
        timeZone = TimeZone.getTimeZone("UTC"),
        walkingMetersPerSecond = 1.2,
    )

    private fun option(id: String, tripIds: List<String>) = RouteOption(
        id, origin, destination, "Line", "Direction", "08:00", "08:15", 0, 0, tripIds,
    )

    private fun stop(sequence: Int, id: String, name: String, arrival: String, departure: String) =
        GtfsTripDetailStop(
            sequence, id, name, null, null, null, arrival, departure,
            sequence * 0.01, sequence * 0.01, 0, 0,
        )
}
