package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionStationDepartureIdentityTest {
    private fun departure(
        routeId: String?,
        line: String,
        direction: String,
        scheduledEpochSeconds: Long = 1_900_000_000L,
    ) = Departure(
        tripId = "reused-trip",
        line = line,
        direction = direction,
        scheduledEpochSeconds = scheduledEpochSeconds,
        routeId = routeId,
    )

    @Test fun sameTripAndTimeOnDistinctRoutesOrDirectionsRemainVisible() {
        val first = departure("route-a", "10", "Lohne")
        val otherRoute = departure("route-b", "20", "Bremen")
        val otherDirection = departure("route-a", "10", "Vechta")
        val otherLine = departure("route-a", "11", "Lohne")

        assertEquals(
            listOf(first, otherRoute, otherDirection, otherLine),
            distinctStationDepartures(listOf(first, otherRoute, otherDirection, otherLine)),
        )
    }

    @Test fun repeatedUpdatesForSameServiceDoNotDuplicateCards() {
        val planned = departure("route-a", "10", "Lohne")
        val duplicate = planned.copy(realtimeEpochSeconds = planned.scheduledEpochSeconds + 120L)

        assertEquals(listOf(planned), distinctStationDepartures(listOf(planned, duplicate)))
    }

    @Test fun sameTripOnDifferentScheduledDeparturesRemainsDistinct() {
        val first = departure("route-a", "10", "Lohne")
        val next = first.copy(scheduledEpochSeconds = first.scheduledEpochSeconds + 3_600L)

        assertEquals(listOf(first, next), distinctStationDepartures(listOf(first, next)))
    }

    @Test fun stationEvidenceRequiresAConcreteGtfsServiceAndPreservesCalendarDay() {
        val trip = GtfsScheduleTrip(
            id = "trip-17", routeId = "route-5", serviceId = "weekday",
            headsign = "Bremen", directionId = 1, shapeId = null,
            wheelchairAccessible = null, bikesAllowed = null,
        )
        val monday = GtfsServiceDate(2026, 10, 19)
        val tuesday = GtfsServiceDate(2026, 10, 20)
        val first = stationDepartureEvidence(trip, 2, monday)!!
        val otherDay = stationDepartureEvidence(trip, 2, tuesday)!!
        assertEquals(2, first.boardingStopSequence)
        assertEquals(GtfsTripInstanceIdentity.scheduled(trip, monday), first.identity)
        assertEquals(false, first.identity == otherDay.identity)
        assertEquals(null, stationDepartureEvidence(trip, 2, null))
        assertEquals(null, stationDepartureEvidence(trip, -1, monday))
        assertEquals(null, stationDepartureEvidence(trip.copy(serviceId = "  "), 2, monday))
    }
}
