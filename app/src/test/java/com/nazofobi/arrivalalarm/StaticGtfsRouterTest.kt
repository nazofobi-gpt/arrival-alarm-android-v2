package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticGtfsRouterTest {
    private val date = GtfsServiceDate(2026, 10, 2)
    private val zone = TimeZone.getTimeZone("Europe/Berlin")

    @Test fun directAndMidnightJourneyIsDeterministic() {
        val data = FakeData(
            candidates = mapOf("A" to listOf(candidate("T1", "R1", "S", 1, "23:55:00"))),
            times = mapOf("T1" to listOf(st("T1",1,"A","23:55:00"), st("T1",2,"B","24:10:00"))),
        )
        val start = GtfsServiceTime.parse("23:50:00").resolve(date, zone).epochMillis
        val result = StaticGtfsRouter(data, zone).route(
            listOf(StaticRouterAccess("A", 60)),
            listOf(StaticRouterAccess("B", 120)),
            date,
            start,
        )
        assertTrue(result is StaticRouterResult.Journeys)
        val journey = (result as StaticRouterResult.Journeys).journeys.single()
        assertEquals("T1", journey.legs.single().tripId)
        assertEquals(0, journey.transferCount)
        assertEquals(120_000L, journey.arrivalEpochMillis - journey.legs.single().arrivalEpochMillis)
    }

    @Test fun transferHonorsMinimumTransferTime() {
        val data = FakeData(
            candidates = mapOf(
                "A" to listOf(candidate("T1","R1","S",1,"10:00:00")),
                "X" to listOf(candidate("TOO_SOON","R2","S",1,"10:06:00"), candidate("T2","R2","S",1,"10:08:00")),
            ),
            times = mapOf(
                "T1" to listOf(st("T1",1,"A","10:00:00"), st("T1",2,"X","10:05:00")),
                "TOO_SOON" to listOf(st("TOO_SOON",1,"X","10:06:00"), st("TOO_SOON",2,"B","10:20:00")),
                "T2" to listOf(st("T2",1,"X","10:08:00"), st("T2",2,"B","10:18:00")),
            ),
            transfers = mapOf("X" to listOf(GtfsScheduleTransfer("X","X",0,120,null,null,null,null))),
        )
        val start = GtfsServiceTime.parse("09:55:00").resolve(date, zone).epochMillis
        val result = StaticGtfsRouter(data, zone).route(listOf(StaticRouterAccess("A")), listOf(StaticRouterAccess("B")), date, start)
        val journey = (result as StaticRouterResult.Journeys).journeys.single()
        assertEquals(listOf("T1","T2"), journey.legs.map { it.tripId })
        assertEquals(1, journey.transferCount)
    }

    @Test fun inactiveServiceNoPathAndSameOriginExplicit() {
        val data = FakeData(active = false, candidates = mapOf("A" to listOf(candidate("T1","R1","S",1,"10:00:00"))))
        val start = GtfsServiceTime.parse("09:00:00").resolve(date, zone).epochMillis
        val router = StaticGtfsRouter(data, zone)
        assertTrue(router.route(listOf(StaticRouterAccess("A")), listOf(StaticRouterAccess("B")), date, start) is StaticRouterResult.NoPath)
        assertTrue(router.route(listOf(StaticRouterAccess("A")), listOf(StaticRouterAccess("A")), date, start) is StaticRouterResult.SameOrigin)
    }

    private fun candidate(trip:String, route:String, service:String, seq:Int, dep:String) =
        GtfsScheduleTripCandidate(GtfsScheduleTrip(trip,route,service,null,null,null,null,null),seq,null,dep)
    private fun st(trip:String, seq:Int, stop:String, time:String) =
        GtfsScheduleStopTime(trip,seq,stop,time,time,null,null,null,null)

    private class FakeData(
        private val active:Boolean = true,
        private val candidates:Map<String,List<GtfsScheduleTripCandidate>> = emptyMap(),
        private val times:Map<String,List<GtfsScheduleStopTime>> = emptyMap(),
        private val transfers:Map<String,List<GtfsScheduleTransfer>> = emptyMap(),
    ): StaticGtfsScheduleData {
        override fun candidateTripsAtStop(stopId:String, limit:Int)=candidates[stopId].orEmpty().take(limit)
        override fun stopTimesForTrip(tripId:String, limit:Int)=times[tripId].orEmpty().take(limit)
        override fun transfersFromStop(stopId:String, limit:Int)=transfers[stopId].orEmpty().take(limit)
        override fun isServiceActive(serviceId:String, serviceDate:GtfsServiceDate)=active
    }
}
