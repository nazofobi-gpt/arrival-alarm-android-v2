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
        val start = at("23:50:00")
        val result = StaticGtfsRouter(data, zone).route(listOf(StaticRouterAccess("A",60)), listOf(StaticRouterAccess("B",120)), date, start)
        val journey = (result as StaticRouterResult.Journeys).journeys.single()
        assertEquals("T1", journey.legs.single().tripId)
        assertEquals(120_000L, journey.arrivalEpochMillis - journey.legs.single().arrivalEpochMillis)
    }

    @Test fun implicitSameStopTransferWorksWithoutTransfersFile() {
        val data = baseTransferData()
        val result = StaticGtfsRouter(data, zone, implicitTransferSeconds = 120).route(
            listOf(StaticRouterAccess("A")), listOf(StaticRouterAccess("B")), date, at("09:55:00"))
        assertEquals(listOf("T1","T2"), (result as StaticRouterResult.Journeys).journeys.single().legs.map { it.tripId })
    }

    @Test fun type2MinimumAndType3SpecificProhibitionGovern() {
        val common = baseTransferData(extraCandidate = candidate("T3","R3","S",1,"10:09:00"), extraTrip = listOf(st("T3",1,"X","10:09:00"),st("T3",2,"B","10:17:00")))
        val rules = listOf(
            transfer("X","X",2,180),
            transfer("X","X",3,0,toTrip="T3"),
        )
        val data = common.copy(transfers = mapOf("X" to rules))
        val result = StaticGtfsRouter(data, zone).route(listOf(StaticRouterAccess("A")),listOf(StaticRouterAccess("B")),date,at("09:55:00"))
        val journey=(result as StaticRouterResult.Journeys).journeys.single()
        assertEquals(listOf("T1","T2"),journey.legs.map{it.tripId})
    }

    @Test fun tripAndRouteScopedRulesDoNotLeakAndSpecificityWins() {
        val common=baseTransferData()
        val rules=listOf(
            transfer("X","X",3,0,toRoute="R2"),
            transfer("X","X",2,120,fromTrip="T1",toTrip="T2"),
        )
        val data=common.copy(transfers=mapOf("X" to rules))
        val result=StaticGtfsRouter(data,zone).route(listOf(StaticRouterAccess("A")),listOf(StaticRouterAccess("B")),date,at("09:55:00"))
        assertEquals(listOf("T1","T2"),(result as StaticRouterResult.Journeys).journeys.single().legs.map{it.tripId})
    }

    @Test fun crossSideTripRouteOutranksSameSideTripRoute() {
        val parent=stop("P",locationType=1)
        val child=stop("X",parent="P")
        val data=baseTransferData().copy(
            stops=mapOf("X" to child,"P" to parent),
            transfers=mapOf(
                "X" to listOf(transfer("X","X",3,0,fromRoute="R1",fromTrip="T1")),
                "P" to listOf(transfer("P","X",2,120,toRoute="R2",fromTrip="T1")),
            ),
        )
        val result=StaticGtfsRouter(data,zone).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertEquals(
            listOf("T1","T2"),
            (result as StaticRouterResult.Journeys).journeys.single().legs.map { it.tripId },
        )
    }

    @Test fun equalMaxApplicableTransferRulesFailSafe() {
        val data=baseTransferData().copy(
            transfers=mapOf(
                "X" to listOf(
                    transfer("X","X",2,60,fromTrip="T1",toRoute="R2"),
                    transfer("X","X",2,120,fromRoute="R1",toTrip="T2"),
                ),
            ),
        )
        val result=StaticGtfsRouter(data,zone).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(result is StaticRouterResult.NoPath)
    }

    @Test fun stationRuleExpandsToChildPlatform() {
        val parent=stop("P",locationType=1)
        val child=stop("X",parent="P")
        val y=stop("Y",parent="Q")
        val q=stop("Q",locationType=1)
        val data=baseTransferData(targetStop="Y").copy(
            stops=mapOf("X" to child,"P" to parent,"Y" to y,"Q" to q),
            children=mapOf("Q" to listOf(y)),
            transfers=mapOf("P" to listOf(transfer("P","Q",2,60))),
        )
        val result=StaticGtfsRouter(data,zone).route(listOf(StaticRouterAccess("A")),listOf(StaticRouterAccess("B")),date,at("09:55:00"))
        assertEquals(listOf("T1","T2"),(result as StaticRouterResult.Journeys).journeys.single().legs.map{it.tripId})
    }

    @Test fun nonRegularPickupAndDropOffAreNotOfferedAsScheduledDirectService() {
        for (type in 1..3) {
            val blockedPickup = FakeData(
                candidates = mapOf("A" to listOf(candidate("T1","R1","S",1,"10:00:00"))),
                times = mapOf(
                    "T1" to listOf(
                        st("T1",1,"A","10:00:00",pickup=type),
                        st("T1",2,"B","10:15:00"),
                    ),
                ),
            )
            assertTrue(
                StaticGtfsRouter(blockedPickup,zone).route(
                    listOf(StaticRouterAccess("A")),
                    listOf(StaticRouterAccess("B")),
                    date,
                    at("09:55:00"),
                ) is StaticRouterResult.NoPath,
            )

            val blockedDropOff = FakeData(
                candidates = mapOf("A" to listOf(candidate("T1","R1","S",1,"10:00:00"))),
                times = mapOf(
                    "T1" to listOf(
                        st("T1",1,"A","10:00:00"),
                        st("T1",2,"B","10:15:00",dropOff=type),
                    ),
                ),
            )
            assertTrue(
                StaticGtfsRouter(blockedDropOff,zone).route(
                    listOf(StaticRouterAccess("A")),
                    listOf(StaticRouterAccess("B")),
                    date,
                    at("09:55:00"),
                ) is StaticRouterResult.NoPath,
            )
        }
    }

    @Test fun transferRequiresRegularFirstLegDropOffAndSecondLegPickup() {
        val blockedFirstAlight = baseTransferData(firstAlightDropOff=1)
        assertTrue(
            StaticGtfsRouter(blockedFirstAlight,zone).route(
                listOf(StaticRouterAccess("A")),
                listOf(StaticRouterAccess("B")),
                date,
                at("09:55:00"),
            ) is StaticRouterResult.NoPath,
        )

        val blockedSecondBoard = baseTransferData(secondBoardPickup=1)
        assertTrue(
            StaticGtfsRouter(blockedSecondBoard,zone).route(
                listOf(StaticRouterAccess("A")),
                listOf(StaticRouterAccess("B")),
                date,
                at("09:55:00"),
            ) is StaticRouterResult.NoPath,
        )
    }

    @Test fun inactiveServiceNoPathAndSameOriginExplicit() {
        val data=FakeData(active=false,candidates=mapOf("A" to listOf(candidate("T1","R1","S",1,"10:00:00"))))
        val router=StaticGtfsRouter(data,zone)
        assertTrue(router.route(listOf(StaticRouterAccess("A")),listOf(StaticRouterAccess("B")),date,at("09:00:00")) is StaticRouterResult.NoPath)
        assertTrue(router.route(listOf(StaticRouterAccess("A")),listOf(StaticRouterAccess("A")),date,at("09:00:00")) is StaticRouterResult.SameOrigin)
    }

    private fun baseTransferData(
        extraCandidate:GtfsScheduleTripCandidate?=null,
        extraTrip:List<GtfsScheduleStopTime>?=null,
        targetStop:String="X",
        firstAlightDropOff:Int?=null,
        secondBoardPickup:Int?=null,
    ):FakeData {
        val cs=mutableListOf(candidate("T2","R2","S",1,"10:08:00")); if(extraCandidate!=null) cs+=extraCandidate
        val ts=mutableMapOf(
            "T1" to listOf(
                st("T1",1,"A","10:00:00"),
                st("T1",2,"X","10:05:00",dropOff=firstAlightDropOff),
            ),
            "T2" to listOf(
                st("T2",1,targetStop,"10:08:00",pickup=secondBoardPickup),
                st("T2",2,"B","10:18:00"),
            ),
        ); if(extraTrip!=null) ts["T3"]=extraTrip
        return FakeData(candidates=mapOf("A" to listOf(candidate("T1","R1","S",1,"10:00:00")),targetStop to cs),times=ts)
    }
    private fun at(raw:String)=GtfsServiceTime.parse(raw).resolve(date,zone).epochMillis
    private fun candidate(trip:String,route:String,service:String,seq:Int,dep:String)=GtfsScheduleTripCandidate(GtfsScheduleTrip(trip,route,service,null,null,null,null,null),seq,null,dep)
    private fun st(
        trip:String,
        seq:Int,
        stop:String,
        time:String,
        pickup:Int?=null,
        dropOff:Int?=null,
    )=GtfsScheduleStopTime(trip,seq,stop,time,time,pickup,dropOff,null,null)
    private fun transfer(from:String,to:String,type:Int,min:Int,fromRoute:String?=null,toRoute:String?=null,fromTrip:String?=null,toTrip:String?=null)=GtfsScheduleTransfer(from,to,type,min,fromRoute,toRoute,fromTrip,toTrip)
    private fun stop(id:String,parent:String?=null,locationType:Int?=0)=GtfsScheduleStop(id,id,0.0,0.0,parent,locationType,null,null)

    private data class FakeData(
        private val active:Boolean=true,
        private val candidates: Map<String, List<GtfsScheduleTripCandidate>> = emptyMap(),
        private val times: Map<String, List<GtfsScheduleStopTime>> = emptyMap(),
        val transfers: Map<String, List<GtfsScheduleTransfer>> = emptyMap(),
        val stops: Map<String, GtfsScheduleStop> = emptyMap(),
        val children: Map<String, List<GtfsScheduleStop>> = emptyMap(),
    ):StaticGtfsScheduleData {
        override fun candidateTripsAtStop(stopId:String,limit:Int)=candidates[stopId].orEmpty().take(limit)
        override fun stopTimesForTrip(tripId:String,limit:Int)=times[tripId].orEmpty().take(limit)
        override fun transfersFromStop(stopId:String,limit:Int)=transfers[stopId].orEmpty().take(limit)
        override fun stop(stopId:String)=stops[stopId]
        override fun childStops(parentStationId:String,limit:Int)=children[parentStationId].orEmpty().sortedBy{it.id}.take(limit)
        override fun isServiceActive(serviceId:String,serviceDate:GtfsServiceDate)=active
    }
}
