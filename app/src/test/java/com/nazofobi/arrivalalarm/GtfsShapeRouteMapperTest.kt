package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class GtfsShapeRouteMapperTest {
    @Test fun selectedTripUsesOnlyTheBoardedGtfsShapeSegment() {
        val date = GtfsServiceDate(2026, 10, 4)
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val departure = GtfsServiceTime.parse("08:00:00").resolve(date, zone).epochMillis
        val arrival = GtfsServiceTime.parse("08:10:00").resolve(date, zone).epochMillis

        val data = object : StaticRouteOptionData {
            var requestedShapeId: String? = null
            var requestedShapeLimit: Int = 0
            override fun route(routeId: String) =
                GtfsScheduleRoute("R", null, "RE", null, 2)
            override fun trip(tripId: String) =
                GtfsScheduleTrip("T", "R", "service", "B", 0, "shape", null, null)
            override fun stop(stopId: String) =
                GtfsScheduleStop(stopId, stopId, 52.0, if (stopId == "A") 8.2 else 8.8, null, 0, null, null)
            override fun stopTimesForTrip(tripId: String, limit: Int) = listOf(
                GtfsScheduleStopTime("T", 1, "A", "08:00:00", "08:00:00", null, null, 2.0, 1),
                GtfsScheduleStopTime("T", 2, "B", "08:10:00", "08:10:00", null, null, 8.0, 1),
            )
            override fun shapePoints(shapeId: String, limit: Int): List<GtfsScheduleShapePoint> {
                requestedShapeId = shapeId
                requestedShapeLimit = limit
                return listOf(
                    GtfsScheduleShapePoint("shape", 1, 52.0, 8.0, 0.0),
                    GtfsScheduleShapePoint("shape", 2, 52.0, 8.5, 5.0),
                    GtfsScheduleShapePoint("shape", 3, 52.0, 9.0, 10.0),
                )
            }
        }
        val journey = StaticTransitJourney(
            id = "trip-segment",
            access = StaticRouterAccess("A"),
            egress = StaticRouterAccess("B"),
            legs = listOf(StaticTransitLeg("T", "R", "A", "B", departure, arrival)),
            arrivalEpochMillis = arrival,
        )
        val option = StaticRouteOptionMapper(data, zone).map(
            journey, MapPoint(52.0, 8.2, "A"), MapPoint(52.0, 8.8, "B"), date, 42L,
        )
        assertEquals(listOf("A", "B"), option.stops.map { it.id })
        assertEquals(1, option.shapePolylines.size)
        assertEquals(8.2, option.shapePolylines.single().first().longitude, 1e-9)
        assertEquals(8.8, option.shapePolylines.single().last().longitude, 1e-9)
        assertEquals("shape", data.requestedShapeId)
        assertEquals(2_048, data.requestedShapeLimit)
    }
}
