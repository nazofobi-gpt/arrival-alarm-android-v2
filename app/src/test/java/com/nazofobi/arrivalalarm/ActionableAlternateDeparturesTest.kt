package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionableAlternateDeparturesTest {
    private val origin = MapPoint(52.0, 8.0, "Origin")
    private val destination = MapPoint(52.1, 8.1, "Destination")

    private fun route(
        id: String,
        departure: Long?,
        line: String = "S1",
        direction: String = "Central",
        from: MapPoint = origin,
        to: MapPoint = destination,
    ) = RouteOption(
        id = id,
        origin = from,
        destination = to,
        line = line,
        direction = direction,
        departure = "12:00",
        arrival = "12:30",
        walkingMinutes = 0,
        transfers = 0,
        departureEpochMillis = departure,
    )

    @Test fun choosesRealFutureDeparturesInTimeOrderAndKeepsSelectionDistinct() {
        val selected = route("selected", 1_000L)
        val choices = actionableAlternateDepartures(
            selected,
            listOf(route("late", 8_000L), route("selected", 1_000L),
                route("soon", 3_000L), route("middle", 5_000L)),
            nowEpochMillis = 2_000L,
        )
        assertEquals(listOf("soon", "middle", "late"), choices.map { it.id })
    }

    @Test fun excludesUnverifiedPastAndSameDeparture() {
        val selected = route("selected", 1_000L)
        val choices = actionableAlternateDepartures(
            selected,
            listOf(route("no-epoch", null), route("past", 1_999L),
                route("same-departure", 1_000L), route("future", 2_001L)),
            nowEpochMillis = 2_000L,
        )
        assertEquals(listOf("future"), choices.map { it.id })
    }

    @Test fun neverConfusesDifferentEndpointsLineOrDirection() {
        val selected = route("selected", 1_000L)
        val choices = actionableAlternateDepartures(
            selected,
            listOf(route("other-line", 3_000L, line = "S2"),
                route("other-direction", 3_000L, direction = "East"),
                route("other-origin", 3_000L, from = MapPoint(53.0, 8.0, "Other")),
                route("other-target", 3_000L, to = MapPoint(53.1, 8.1, "Other")),
                route("matching", 3_000L)),
            nowEpochMillis = 2_000L,
        )
        assertEquals(listOf("matching"), choices.map { it.id })
    }

    @Test fun selectedTripWithoutAbsoluteDepartureCannotOfferInventedTimes() {
        assertTrue(actionableAlternateDepartures(route("unknown", null),
            listOf(route("future", 3_000L)), nowEpochMillis = 2_000L).isEmpty())
    }

    @Test fun boundsResultsAndRejectsInvalidLimit() {
        val selected = route("selected", 1_000L)
        val candidates = (1..12).map { route("candidate-$it", 2_000L + it) }
        assertEquals(3, actionableAlternateDepartures(
            selected, candidates, nowEpochMillis = 2_000L,
        ).size)
        assertTrue(actionableAlternateDepartures(
            selected, candidates, nowEpochMillis = 2_000L, limit = 0,
        ).isEmpty())
        assertTrue(actionableAlternateDepartures(
            selected, candidates, nowEpochMillis = 2_000L, limit = 50,
        ).isEmpty())
    }
}
