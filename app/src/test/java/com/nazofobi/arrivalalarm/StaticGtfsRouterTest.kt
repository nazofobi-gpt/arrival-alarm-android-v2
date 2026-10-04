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

    @Test fun implicitSiblingPlatformTransferWorksWithoutTransfersFile() {
        val parent = stop("P", locationType = 1, lat = 52.0, lon = 8.0)
        val x = stop("X", parent = "P", lat = 52.0, lon = 8.0)
        val y = stop("Y", parent = "P", lat = 52.0002, lon = 8.0)
        val data = baseTransferData(targetStop = "Y").copy(
            stops = mapOf("P" to parent, "X" to x, "Y" to y),
            children = mapOf("P" to listOf(x, y)),
        )
        val result = StaticGtfsRouter(data, zone, implicitTransferSeconds = 60).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertEquals(
            listOf("T1", "T2"),
            (result as StaticRouterResult.Journeys).journeys.single().legs.map { it.tripId },
        )
    }

    @Test fun implicitNearbyTransferUsesRadiusAndDistanceDerivedWalkTime() {
        val x = stop("X", lat = 52.0, lon = 8.0)
        val y = stop("Y", lat = 52.001, lon = 8.0)
        val close = baseTransferData(targetStop = "Y").copy(
            stops = mapOf("X" to x, "Y" to y),
            nearby = listOf(GtfsScheduleNearbyStop(y, 100)),
        )
        val closeResult = StaticGtfsRouter(
            close,
            zone,
            implicitTransferSeconds = 60,
            maxImplicitTransferRadiusMeters = 300,
            implicitWalkingMetersPerSecond = 1.0,
        ).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(closeResult is StaticRouterResult.Journeys)

        val outsideRadius = close.copy(nearby = listOf(GtfsScheduleNearbyStop(y, 301)))
        val outsideResult = StaticGtfsRouter(
            outsideRadius,
            zone,
            implicitTransferSeconds = 60,
            maxImplicitTransferRadiusMeters = 300,
            implicitWalkingMetersPerSecond = 1.0,
        ).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(outsideResult is StaticRouterResult.NoPath)

        val distanceBound = close.copy(nearby = listOf(GtfsScheduleNearbyStop(y, 250)))
        val tooEarly = StaticGtfsRouter(
            distanceBound,
            zone,
            implicitTransferSeconds = 60,
            maxImplicitTransferRadiusMeters = 300,
            implicitWalkingMetersPerSecond = 1.0,
        ).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(tooEarly is StaticRouterResult.NoPath)

        val later = baseTransferData(targetStop = "Y", secondDeparture = "10:10:00").copy(
            stops = mapOf("X" to x, "Y" to y),
            nearby = listOf(GtfsScheduleNearbyStop(y, 250)),
        )
        val laterResult = StaticGtfsRouter(
            later,
            zone,
            implicitTransferSeconds = 60,
            maxImplicitTransferRadiusMeters = 300,
            implicitWalkingMetersPerSecond = 1.0,
        ).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(laterResult is StaticRouterResult.Journeys)
    }

    @Test fun explicitRulesRemainAuthoritativeOverImplicitNearbyTransfer() {
        val x = stop("X", lat = 52.0, lon = 8.0)
        val y = stop("Y", lat = 52.0005, lon = 8.0)
        val nearby = listOf(GtfsScheduleNearbyStop(y, 50))

        val prohibited = baseTransferData(targetStop = "Y").copy(
            stops = mapOf("X" to x, "Y" to y),
            nearby = nearby,
            transfers = mapOf("X" to listOf(transfer("X", "Y", 3, 0))),
        )
        val prohibitedResult = StaticGtfsRouter(
            prohibited,
            zone,
            implicitTransferSeconds = 60,
            maxImplicitTransferRadiusMeters = 300,
        ).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(prohibitedResult is StaticRouterResult.NoPath)

        val minimumOverride = prohibited.copy(
            transfers = mapOf("X" to listOf(transfer("X", "Y", 2, 240))),
        )
        val minimumResult = StaticGtfsRouter(
            minimumOverride,
            zone,
            implicitTransferSeconds = 60,
            maxImplicitTransferRadiusMeters = 300,
        ).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(minimumResult is StaticRouterResult.NoPath)
    }

    @Test fun implicitNearbyTransferHonorsCandidateCap() {
        val x = stop("X", lat = 52.0, lon = 8.0)
        val y1 = stop("Y1", lat = 52.0001, lon = 8.0)
        val y2 = stop("Y2", lat = 52.0002, lon = 8.0)
        val y3 = stop("Y3", lat = 52.0003, lon = 8.0)
        val data = baseTransferData(targetStop = "Y3").copy(
            stops = mapOf("X" to x, "Y1" to y1, "Y2" to y2, "Y3" to y3),
            nearby = listOf(
                GtfsScheduleNearbyStop(y1, 20),
                GtfsScheduleNearbyStop(y2, 30),
                GtfsScheduleNearbyStop(y3, 40),
            ),
        )
        val result = StaticGtfsRouter(
            data,
            zone,
            maxImplicitTransferCandidates = 2,
            maxImplicitTransferRadiusMeters = 300,
        ).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertTrue(result is StaticRouterResult.NoPath)
        assertEquals(2, data.lastNearbyLimit)
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

    @Test fun densePastCandidatesAreFilteredBeforeInitialLimit() {
        val past = (0 until 140).map { i ->
            candidate("P$i", "R1", "S", 1, "09:00:00")
        }
        val viable = candidate("T1", "R1", "S", 1, "10:05:00")
        val data = FakeData(
            candidates = mapOf("A" to past + viable),
            times = mapOf(
                "T1" to listOf(
                    st("T1", 1, "A", "10:05:00"),
                    st("T1", 2, "B", "10:20:00"),
                ),
            ),
        )
        val result = StaticGtfsRouter(data, zone, maxCandidatesPerStop = 1).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("10:00:00"),
        )
        assertEquals(
            listOf("T1"),
            (result as StaticRouterResult.Journeys).journeys.single().legs.map { it.tripId },
        )
    }

    @Test fun densePastTransferCandidatesAreFilteredBeforeTransferLimit() {
        val past = (0 until 140).map { i ->
            candidate("PX$i", "R2", "S", 1, "10:00:00")
        }
        val second = candidate("T2", "R2", "S", 1, "10:08:00")
        val data = FakeData(
            candidates = mapOf(
                "A" to listOf(candidate("T1", "R1", "S", 1, "10:00:00")),
                "X" to past + second,
            ),
            times = mapOf(
                "T1" to listOf(
                    st("T1", 1, "A", "10:00:00"),
                    st("T1", 2, "X", "10:05:00"),
                ),
                "T2" to listOf(
                    st("T2", 1, "X", "10:08:00"),
                    st("T2", 2, "B", "10:18:00"),
                ),
            ),
        )
        val result = StaticGtfsRouter(data, zone, maxCandidatesPerStop = 1).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )
        assertEquals(
            listOf("T1", "T2"),
            (result as StaticRouterResult.Journeys).journeys.single().legs.map { it.tripId },
        )
    }

    @Test fun type4LinkedTripContinuesWithoutAlightOrReboardRestrictions() {
        val secondTrip = trip("T2", "R2", "S")
        val data = FakeData(
            candidates = mapOf("A" to listOf(candidate("T1", "R1", "S", 1, "10:00:00"))),
            times = mapOf(
                "T1" to listOf(
                    st("T1", 1, "A", "10:00:00"),
                    st("T1", 2, "X", "10:05:00", dropOff = 1),
                ),
                "T2" to listOf(
                    st("T2", 1, "Y", "10:05:00", pickup = 1),
                    st("T2", 2, "B", "10:18:00"),
                ),
            ),
            trips = mapOf("T2" to secondTrip),
            linkedTransfers = mapOf(
                "T1" to listOf(transfer("X", "Y", 4, 0, fromTrip = "T1", toTrip = "T2")),
            ),
        )

        val result = StaticGtfsRouter(data, zone).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )

        assertEquals(
            listOf("T1", "T2"),
            (result as StaticRouterResult.Journeys).journeys.single().legs.map { it.tripId },
        )
    }

    @Test fun type5LinkedTripOverridesBlockAndForcesReboard() {
        val firstTrip = trip("T1", "R1", "S", block = "BLOCK")
        val secondTrip = trip("T2", "R2", "S", block = "BLOCK")
        val data = FakeData(
            candidates = mapOf(
                "A" to listOf(GtfsScheduleTripCandidate(firstTrip, 1, null, "10:00:00")),
            ),
            times = mapOf(
                "T1" to listOf(
                    st("T1", 1, "A", "10:00:00"),
                    st("T1", 2, "X", "10:05:00", dropOff = 1),
                ),
                "T2" to listOf(
                    st("T2", 1, "X", "10:05:00", pickup = 1),
                    st("T2", 2, "B", "10:18:00"),
                ),
            ),
            linkedTransfers = mapOf(
                "T1" to listOf(
                    GtfsScheduleTransfer(null, null, 5, null, null, null, "T1", "T2"),
                ),
            ),
            blockTrips = mapOf("BLOCK" to listOf(firstTrip, secondTrip)),
        )

        val result = StaticGtfsRouter(data, zone).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )

        assertTrue(result is StaticRouterResult.NoPath)
    }

    @Test fun compatibleConsecutiveTripsInSameBlockContinueWithoutExplicitRule() {
        val firstTrip = trip("T1", "R1", "S", block = "BLOCK")
        val secondTrip = trip("T2", "R2", "S", block = "BLOCK")
        val data = FakeData(
            candidates = mapOf(
                "A" to listOf(GtfsScheduleTripCandidate(firstTrip, 1, null, "10:00:00")),
            ),
            times = mapOf(
                "T1" to listOf(
                    st("T1", 1, "A", "10:00:00"),
                    st("T1", 2, "X", "10:05:00", dropOff = 1),
                ),
                "T2" to listOf(
                    st("T2", 1, "X", "10:06:00", pickup = 1),
                    st("T2", 2, "B", "10:18:00"),
                ),
            ),
            blockTrips = mapOf("BLOCK" to listOf(firstTrip, secondTrip)),
        )

        val result = StaticGtfsRouter(data, zone).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )

        assertEquals(
            listOf("T1", "T2"),
            (result as StaticRouterResult.Journeys).journeys.single().legs.map { it.tripId },
        )
    }

    @Test fun blockContinuityRejectsMismatchedEndpointAndUnboundedWait() {
        val firstTrip = trip("T1", "R1", "S", block = "BLOCK")
        val wrongEndpoint = trip("T2", "R2", "S", block = "BLOCK")
        val tooLate = trip("T3", "R3", "S", block = "BLOCK")
        val data = FakeData(
            candidates = mapOf(
                "A" to listOf(GtfsScheduleTripCandidate(firstTrip, 1, null, "10:00:00")),
            ),
            times = mapOf(
                "T1" to listOf(st("T1", 1, "A", "10:00:00"), st("T1", 2, "X", "10:05:00")),
                "T2" to listOf(st("T2", 1, "Y", "10:06:00"), st("T2", 2, "B", "10:18:00")),
                "T3" to listOf(st("T3", 1, "X", "17:00:00"), st("T3", 2, "B", "17:18:00")),
            ),
            blockTrips = mapOf("BLOCK" to listOf(firstTrip, wrongEndpoint, tooLate)),
        )

        val result = StaticGtfsRouter(data, zone).route(
            listOf(StaticRouterAccess("A")),
            listOf(StaticRouterAccess("B")),
            date,
            at("09:55:00"),
        )

        assertTrue(result is StaticRouterResult.NoPath)
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
        secondDeparture:String="10:08:00",
    ):FakeData {
        val cs=mutableListOf(candidate("T2","R2","S",1,secondDeparture)); if(extraCandidate!=null) cs+=extraCandidate
        val ts=mutableMapOf(
            "T1" to listOf(
                st("T1",1,"A","10:00:00"),
                st("T1",2,"X","10:05:00",dropOff=firstAlightDropOff),
            ),
            "T2" to listOf(
                st("T2",1,targetStop,secondDeparture,pickup=secondBoardPickup),
                st("T2",2,"B","10:18:00"),
            ),
        ); if(extraTrip!=null) ts["T3"]=extraTrip
        return FakeData(candidates=mapOf("A" to listOf(candidate("T1","R1","S",1,"10:00:00")),targetStop to cs),times=ts)
    }
    private fun at(raw:String)=GtfsServiceTime.parse(raw).resolve(date,zone).epochMillis
    private fun trip(id:String,route:String,service:String,block:String?=null)=
        GtfsScheduleTrip(id,route,service,null,null,null,null,block)
    private fun candidate(trip:String,route:String,service:String,seq:Int,dep:String,block:String?=null)=
        GtfsScheduleTripCandidate(this.trip(trip,route,service,block),seq,null,dep)
    private fun st(
        trip:String,
        seq:Int,
        stop:String,
        time:String,
        pickup:Int?=null,
        dropOff:Int?=null,
    )=GtfsScheduleStopTime(trip,seq,stop,time,time,pickup,dropOff,null,null)
    private fun transfer(from:String,to:String,type:Int,min:Int,fromRoute:String?=null,toRoute:String?=null,fromTrip:String?=null,toTrip:String?=null)=GtfsScheduleTransfer(from,to,type,min,fromRoute,toRoute,fromTrip,toTrip)
    private fun stop(
        id:String,
        parent:String?=null,
        locationType:Int?=0,
        lat:Double=0.0,
        lon:Double=0.0,
    )=GtfsScheduleStop(id,id,lat,lon,parent,locationType,null,null)

    private data class FakeData(
        private val active:Boolean=true,
        private val candidates: Map<String, List<GtfsScheduleTripCandidate>> = emptyMap(),
        private val times: Map<String, List<GtfsScheduleStopTime>> = emptyMap(),
        val transfers: Map<String, List<GtfsScheduleTransfer>> = emptyMap(),
        val linkedTransfers: Map<String, List<GtfsScheduleTransfer>> = emptyMap(),
        val blockTrips: Map<String, List<GtfsScheduleTrip>> = emptyMap(),
        val trips: Map<String, GtfsScheduleTrip> = emptyMap(),
        val stops: Map<String, GtfsScheduleStop> = emptyMap(),
        val children: Map<String, List<GtfsScheduleStop>> = emptyMap(),
        val nearby: List<GtfsScheduleNearbyStop> = emptyList(),
    ):StaticGtfsScheduleData {
        var lastNearbyLimit: Int = 0
        override fun candidateTripsAtStop(
            stopId:String,
            serviceDate:GtfsServiceDate,
            earliestBoardSeconds:Int,
            limit:Int,
        )=candidates[stopId].orEmpty()
            .filter { candidate ->
                active && candidateBoardSeconds(candidate)?.let { it >= earliestBoardSeconds } == true
            }
            .sortedWith(
                compareBy<GtfsScheduleTripCandidate> { candidateBoardSeconds(it) ?: Int.MAX_VALUE }
                    .thenBy { it.trip.id }
                    .thenBy { it.stopSequence }
            )
            .take(limit)
        override fun stopTimesForTrip(tripId:String,limit:Int)=times[tripId].orEmpty().take(limit)
        override fun transfersFromStop(stopId:String,limit:Int)=transfers[stopId].orEmpty().take(limit)
        override fun linkedTransfersFromTrip(fromTripId:String,limit:Int)=
            linkedTransfers[fromTripId].orEmpty().take(limit)
        override fun tripsInBlock(blockId:String,serviceId:String,limit:Int)=
            blockTrips[blockId].orEmpty().filter { it.serviceId == serviceId }.take(limit)
        override fun trip(tripId:String)=trips[tripId]
            ?: blockTrips.values.asSequence().flatten().firstOrNull { it.id == tripId }
            ?: candidates.values.asSequence().flatten().map { it.trip }.firstOrNull { it.id == tripId }
        override fun stop(stopId:String)=stops[stopId]
        override fun childStops(parentStationId:String,limit:Int)=children[parentStationId].orEmpty().sortedBy{it.id}.take(limit)
        override fun nearbyStops(stopId:String,radiusMeters:Int,limit:Int):List<GtfsScheduleNearbyStop> {
            lastNearbyLimit = limit
            return nearby.filter { it.stop.id != stopId && it.distanceMeters <= radiusMeters }
                .sortedWith(compareBy<GtfsScheduleNearbyStop> { it.distanceMeters }.thenBy { it.stop.id })
                .take(limit)
        }
        private fun candidateBoardSeconds(candidate:GtfsScheduleTripCandidate):Int? =
            (candidate.departureTime ?: candidate.arrivalTime)?.let { raw ->
                runCatching { GtfsServiceTime.parse(raw).secondsFromServiceDayStart }.getOrNull()
            }

        override fun isServiceActive(serviceId:String,serviceDate:GtfsServiceDate)=active
    }
}
