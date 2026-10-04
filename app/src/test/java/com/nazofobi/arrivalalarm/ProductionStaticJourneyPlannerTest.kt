package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductionStaticJourneyPlannerTest {
    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val date = GtfsServiceDate(2026, 10, 4)

    @Test
    fun mapperPreservesStableGraphIdentityStopsAndScheduledTimes() {
        val data = FakeRouteOptionData(
            routes = mapOf(
                "R1" to GtfsScheduleRoute("R1", "A1", "RE 1", null, 2),
                "R2" to GtfsScheduleRoute("R2", "A2", "IC 2", null, 2),
            ),
            trips = mapOf(
                "T1" to trip("T1", "R1", "Bremen"),
                "T2" to trip("T2", "R2", "Berlin"),
            ),
            stops = mapOf(
                "A" to stop("A", "Lohne"),
                "X" to stop("X", "Bremen Hbf"),
                "B" to stop("B", "Berlin Hbf"),
            ),
            stopTimes = mapOf(
                "T1" to listOf(
                    st("T1", 1, "A", "08:00:00", "08:00:00"),
                    st("T1", 2, "X", "08:20:00", "08:20:00"),
                ),
                "T2" to listOf(
                    st("T2", 1, "X", "08:25:00", "08:25:00"),
                    st("T2", 2, "B", "08:45:00", "08:45:00"),
                ),
            ),
        )
        val journey = StaticTransitJourney(
            id = "stable-static-id",
            access = StaticRouterAccess("A", 60),
            egress = StaticRouterAccess("B", 120),
            legs = listOf(
                StaticTransitLeg("T1", "R1", "A", "X", at("08:00:00"), at("08:20:00")),
                StaticTransitLeg("T2", "R2", "X", "B", at("08:25:00"), at("08:45:00")),
            ),
            arrivalEpochMillis = at("08:47:00"),
        )

        val option = StaticRouteOptionMapper(data, zone).map(
            journey = journey,
            origin = MapPoint(52.66, 8.23, "Lohne"),
            destination = MapPoint(52.52, 13.40, "Berlin"),
            serviceDate = date,
            sourceUpdatedAtEpochSeconds = 1234L,
        )

        assertEquals("stable-static-id", option.id)
        assertEquals("RE 1 → IC 2", option.line)
        assertEquals("Berlin", option.direction)
        assertEquals("08:00", option.departure)
        assertEquals("08:47", option.arrival)
        assertEquals(3, option.walkingMinutes)
        assertEquals(1, option.transfers)
        assertEquals(listOf("T1", "T2"), option.tripIds)
        assertEquals(listOf("A", "X", "B"), option.stops.map { it.id })
        assertEquals("Lohne", option.stops[0].name)
        assertEquals("Bremen Hbf", option.stops[1].name)
        assertEquals("Berlin Hbf", option.stops[2].name)
        assertEquals("2026-10-04T08:20:00+02:00", option.stops[1].plannedArrival)
        assertEquals("2026-10-04T08:25:00+02:00", option.stops[1].plannedDeparture)
        assertNull(option.stops[1].arrival)
        assertNull(option.stops[1].departure)
        assertEquals(1234L, option.sourceUpdatedAtEpochSeconds)
    }

    @Test
    fun frequencyShiftIsAppliedToEveryMappedStop() {
        val data = FakeRouteOptionData(
            routes = mapOf("R" to GtfsScheduleRoute("R", null, "S", null, 3)),
            trips = mapOf("F" to trip("F", "R", "Ziel")),
            stops = mapOf("A" to stop("A", "A"), "B" to stop("B", "B")),
            stopTimes = mapOf(
                "F" to listOf(
                    st("F", 1, "A", "00:00:00", "00:00:00"),
                    st("F", 2, "B", "00:10:00", "00:10:00"),
                ),
            ),
        )
        val shift = 9 * 60 * 60 * 1000L
        val journey = StaticTransitJourney(
            id = "frequency-instance",
            access = StaticRouterAccess("A"),
            egress = StaticRouterAccess("B"),
            legs = listOf(
                StaticTransitLeg(
                    tripId = "F",
                    routeId = "R",
                    fromStopId = "A",
                    toStopId = "B",
                    departureEpochMillis = at("00:00:00") + shift,
                    arrivalEpochMillis = at("00:10:00") + shift,
                    frequencyBased = true,
                    frequencyInstanceStartSeconds = 9 * 3600,
                ),
            ),
            arrivalEpochMillis = at("00:10:00") + shift,
        )

        val option = StaticRouteOptionMapper(data, zone).map(
            journey,
            MapPoint(0.0, 0.0, "A"),
            MapPoint(0.0, 0.0, "B"),
            date,
            1L,
        )

        assertEquals("09:00", option.departure)
        assertEquals("2026-10-04T09:00:00+02:00", option.stops.first().plannedDeparture)
        assertEquals("2026-10-04T09:10:00+02:00", option.stops.last().plannedArrival)
    }

    private fun at(raw: String): Long =
        GtfsServiceTime.parse(raw).resolve(date, zone).epochMillis

    private fun trip(id: String, routeId: String, headsign: String) =
        GtfsScheduleTrip(id, routeId, "S", headsign, 0, null, null, null)

    private fun stop(id: String, name: String) =
        GtfsScheduleStop(id, name, 0.0, 0.0, null, 0, null, null)

    private fun st(
        tripId: String,
        sequence: Int,
        stopId: String,
        arrival: String,
        departure: String,
    ) = GtfsScheduleStopTime(
        tripId,
        sequence,
        stopId,
        arrival,
        departure,
        null,
        null,
        null,
        1,
    )

    private class FakeRouteOptionData(
        private val routes: Map<String, GtfsScheduleRoute>,
        private val trips: Map<String, GtfsScheduleTrip>,
        private val stops: Map<String, GtfsScheduleStop>,
        private val stopTimes: Map<String, List<GtfsScheduleStopTime>>,
    ) : StaticRouteOptionData {
        override fun route(routeId: String) = routes[routeId]
        override fun trip(tripId: String) = trips[tripId]
        override fun stop(stopId: String) = stops[stopId]
        override fun stopTimesForTrip(tripId: String, limit: Int) =
            stopTimes[tripId].orEmpty().take(limit)
    }
}
