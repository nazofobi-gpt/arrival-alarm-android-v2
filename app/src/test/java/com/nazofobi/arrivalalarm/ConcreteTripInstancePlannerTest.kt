package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConcreteTripInstancePlannerTest {
    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val date = GtfsServiceDate(2026, 10, 2)

    @Test fun concreteScheduledTripPreservesOrderAndPreviousServiceDayAfterMidnight() {
        val planner = ConcreteTripInstancePlanner(FakeData(), zone)
        val instance = planner.plan(departure(), "A")!!
        assertEquals(date, instance.serviceDate)
        assertEquals(listOf("A", "B"), instance.sequence.stops.map { it.stop.id })
        assertEquals(1, instance.sequence.boardingSequence)
        assertEquals(15 * 60_000L,
            instance.scheduledTimes.getValue(2) - instance.scheduledTimes.getValue(1))
    }

    @Test fun routeMismatchAndUnknownPlatformDoNotSelectTrip() {
        val planner = ConcreteTripInstancePlanner(FakeData(), zone)
        assertNull(planner.plan(departure().copy(routeId = "WRONG"), "A"))
        assertNull(planner.plan(departure(), "UNKNOWN"))
    }

    @Test fun inactiveServiceAndFrequencyTemplateAreNotMistakenForRealInstance() {
        assertNull(ConcreteTripInstancePlanner(FakeData(active = false), zone).plan(departure(), "A"))
        assertNull(ConcreteTripInstancePlanner(FakeData(), zone).plan(
            departure().copy(realtimeTripStartDate = "20261002"), "A"))
    }

    private fun departure() = Departure(
        tripId = "T", line = "1", direction = "B",
        scheduledEpochSeconds = GtfsServiceTime.parse("23:55:00")
            .resolve(date, zone).epochMillis / 1_000,
        routeId = "R",
    )

    private class FakeData(private val active: Boolean = true) : StaticGtfsScheduleData {
        private val trip = GtfsScheduleTrip("T", "R", "S", "B", 0, null, null, null)
        private val stops = mapOf(
            "A" to GtfsScheduleStop("A", "Stop A", 52.0, 8.0, null, 0, null, null),
            "B" to GtfsScheduleStop("B", "Stop B", 52.1, 8.1, null, 0, null, null),
        )
        private val times = listOf(
            GtfsScheduleStopTime("T", 1, "A", "23:55:00", "23:55:00", 0, 1, null, 1),
            GtfsScheduleStopTime("T", 2, "B", "24:10:00", "24:10:00", 1, 0, null, 1),
        )
        override fun trip(tripId: String) = trip.takeIf { it.id == tripId }
        override fun stop(stopId: String) = stops[stopId]
        override fun stopTimesForTrip(tripId: String, limit: Int) =
            if (tripId == "T") times.take(limit) else emptyList()
        override fun isServiceActive(serviceId: String, serviceDate: GtfsServiceDate) =
            active && serviceId == "S" && serviceDate == GtfsServiceDate(2026, 10, 2)
        override fun candidateTripsAtStop(stopId: String, serviceDate: GtfsServiceDate,
            earliestBoardSeconds: Int, limit: Int) = emptyList<GtfsScheduleTripCandidate>()
        override fun frequenciesForTrip(tripId: String, limit: Int) = emptyList<GtfsScheduleFrequency>()
        override fun transfersFromStop(stopId: String, limit: Int) = emptyList<GtfsScheduleTransfer>()
        override fun linkedTransfersFromTrip(fromTripId: String, limit: Int) =
            emptyList<GtfsScheduleTransfer>()
        override fun tripsInBlock(blockId: String, serviceId: String, limit: Int) =
            emptyList<GtfsScheduleTrip>()
        override fun childStops(parentStationId: String, limit: Int) =
            emptyList<GtfsScheduleStop>()
        override fun nearbyStops(stopId: String, radiusMeters: Int, limit: Int) =
            emptyList<GtfsScheduleNearbyStop>()
    }
}
