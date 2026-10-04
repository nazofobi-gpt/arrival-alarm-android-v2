package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionStaticJourneyPlannerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val serviceDate = GtfsServiceDate(2026, 10, 4)

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun journeyOptionsUsesLocalStaticGraphWithoutCallingLiveProvider() {
        seedReadyGraph(includeTrip = true)
        var liveCalls = 0
        val gateway = NationwideTransitGateway(
            context = context,
            nowMillis = { at("07:55:00") },
            liveJourneyLoader = { _, _, _ ->
                liveCalls += 1
                throw IOException("provider disabled")
            },
        )
        try {
            val resolution = gateway.resolveJourneyOptions(
                origin = MapPoint(52.665, 8.237, "Lohne"),
                destination = MapPoint(52.700, 8.400, "Achim"),
                limit = 3,
            )

            assertTrue(resolution is NationwideJourneyResolution.Results)
            resolution as NationwideJourneyResolution.Results
            assertEquals(NationwideJourneySource.LOCAL_STATIC, resolution.source)
            assertEquals(0, liveCalls)
            val option = resolution.options.single()
            assertEquals(listOf("T1"), option.tripIds)
            assertEquals("RE 1", option.line)
            assertEquals("Achim", option.direction)
            assertEquals(listOf("A", "B"), option.stops.map { it.id })
            assertEquals("Lohne", option.stops.first().name)
            assertEquals("Achim", option.stops.last().name)
            assertEquals("2026-10-04T08:00:00+02:00", option.stops.first().plannedDeparture)
            assertEquals("2026-10-04T08:30:00+02:00", option.stops.last().plannedArrival)
            assertEquals(1_234L, option.sourceUpdatedAtEpochSeconds)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun noStaticPathFallsBackToLiveAndProviderFailureRemainsTyped() {
        seedReadyGraph(includeTrip = false)
        val liveOption = RouteOption(
            id = "live-fallback",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(52.700, 8.400, "Achim"),
            line = "LIVE",
            direction = "Achim",
            departure = "08:05",
            arrival = "08:35",
            walkingMinutes = 2,
            transfers = 0,
        )
        val liveGateway = NationwideTransitGateway(
            context = context,
            nowMillis = { at("07:55:00") },
            liveJourneyLoader = { _, _, _ -> listOf(liveOption) },
        )
        try {
            val fallback = liveGateway.resolveJourneyOptions(
                liveOption.origin,
                liveOption.destination,
                3,
            )
            assertTrue(fallback is NationwideJourneyResolution.Results)
            fallback as NationwideJourneyResolution.Results
            assertEquals(NationwideJourneySource.LIVE_FALLBACK, fallback.source)
            assertEquals(listOf("live-fallback"), fallback.options.map { it.id })
        } finally {
            liveGateway.close()
        }

        val unavailableGateway = NationwideTransitGateway(
            context = context,
            nowMillis = { at("07:55:00") },
            liveJourneyLoader = { _, _, _ -> throw IOException("offline") },
        )
        try {
            val unavailable = unavailableGateway.resolveJourneyOptions(
                liveOption.origin,
                liveOption.destination,
                3,
            )
            assertTrue(unavailable is NationwideJourneyResolution.ProviderUnavailable)
            unavailable as NationwideJourneyResolution.ProviderUnavailable
            assertEquals(LocalStaticJourneyOutcome.NoPath, unavailable.localOutcome)
            assertEquals("offline", unavailable.message)
        } finally {
            unavailableGateway.close()
        }
    }

    @Test
    fun afterMidnightPrefersPreviousServiceDayCarryoverOverNextNightTrip() {
        seedCrossMidnightGraph()
        var liveCalls = 0
        val gateway = NationwideTransitGateway(
            context = context,
            nowMillis = { at("00:05:00") },
            liveJourneyLoader = { _, _, _ ->
                liveCalls += 1
                throw IOException("provider disabled")
            },
        )
        try {
            val resolution = gateway.resolveJourneyOptions(
                origin = MapPoint(52.665, 8.237, "Lohne"),
                destination = MapPoint(52.700, 8.400, "Achim"),
                limit = 1,
            )

            assertTrue(resolution is NationwideJourneyResolution.Results)
            resolution as NationwideJourneyResolution.Results
            assertEquals(NationwideJourneySource.LOCAL_STATIC, resolution.source)
            assertEquals(0, liveCalls)
            val option = resolution.options.single()
            assertEquals(listOf("PREV"), option.tripIds)
            assertEquals("2026-10-04T00:10:00+02:00", option.stops.first().plannedDeparture)
            assertEquals("2026-10-04T00:30:00+02:00", option.stops.last().plannedArrival)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun overlappingNearestWindowsDoNotCreateFalseSameOrigin() {
        seedReadyGraph(includeTrip = true)
        val index = NationwideTransitIndex(context)
        try {
            val planner = ProductionStaticJourneyPlanner(
                index = index,
                agencyTimeZone = zone,
                nowMillis = { at("07:55:00") },
                maxAccessStops = 4,
            )

            val outcome = planner.plan(
                origin = MapPoint(52.665, 8.237, "Lohne"),
                destination = MapPoint(52.700, 8.400, "Achim"),
                limit = 3,
            )

            assertTrue(outcome is LocalStaticJourneyOutcome.Results)
        } finally {
            index.close()
        }
    }

    private fun seedCrossMidnightGraph() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val db = index.writableDatabase
        db.execSQL(
            "INSERT OR REPLACE INTO metadata(key,value) VALUES" +
                "('ready','1'),('fetched_at','1234'),('stop_count','2')," +
                "('source_version','fixture'),('schema_version','4')",
        )
        db.execSQL(
            "INSERT INTO stops(id,name,lat,lon,parent_station,location_type) " +
                "VALUES('A','Lohne',52.665,8.237,NULL,0)",
        )
        db.execSQL(
            "INSERT INTO stops(id,name,lat,lon,parent_station,location_type) " +
                "VALUES('B','Achim',52.700,8.400,NULL,0)",
        )
        db.execSQL(
            "INSERT INTO routes(route_id,agency_id,short_name,long_name,route_type) " +
                "VALUES('R1','AG','RE 1','Regional Express',2)",
        )
        db.execSQL(
            "INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) " +
                "VALUES('S_PREV',0,0,0,0,0,1,0,'20261003','20261003')",
        )
        db.execSQL(
            "INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) " +
                "VALUES('S_CUR',0,0,0,0,0,0,1,'20261004','20261004')",
        )
        db.execSQL(
            "INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) " +
                "VALUES('PREV','R1','S_PREV','Achim',0)",
        )
        db.execSQL(
            "INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) " +
                "VALUES('NEXT','R1','S_CUR','Achim',0)",
        )
        db.execSQL(
            "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type) " +
                "VALUES('PREV',1,'A','24:10:00','24:10:00',0,0)",
        )
        db.execSQL(
            "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type) " +
                "VALUES('PREV',2,'B','24:30:00','24:30:00',0,0)",
        )
        db.execSQL(
            "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type) " +
                "VALUES('NEXT',1,'A','24:20:00','24:20:00',0,0)",
        )
        db.execSQL(
            "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type) " +
                "VALUES('NEXT',2,'B','24:40:00','24:40:00',0,0)",
        )
        index.close()
    }

    private fun seedReadyGraph(includeTrip: Boolean) {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val db = index.writableDatabase
        db.execSQL(
            "INSERT OR REPLACE INTO metadata(key,value) VALUES" +
                "('ready','1'),('fetched_at','1234'),('stop_count','2')," +
                "('source_version','fixture'),('schema_version','4')",
        )
        db.execSQL(
            "INSERT INTO stops(id,name,lat,lon,parent_station,location_type) " +
                "VALUES('A','Lohne',52.665,8.237,NULL,0)",
        )
        db.execSQL(
            "INSERT INTO stops(id,name,lat,lon,parent_station,location_type) " +
                "VALUES('B','Achim',52.700,8.400,NULL,0)",
        )
        db.execSQL(
            "INSERT INTO routes(route_id,agency_id,short_name,long_name,route_type) " +
                "VALUES('R1','AG','RE 1','Regional Express',2)",
        )
        db.execSQL(
            "INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) " +
                "VALUES('S',0,0,0,0,0,0,1,'20260101','20261231')",
        )
        if (includeTrip) {
            db.execSQL(
                "INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) " +
                    "VALUES('T1','R1','S','Achim',0)",
            )
            db.execSQL(
                "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type) " +
                    "VALUES('T1',1,'A','08:00:00','08:00:00',0,0)",
            )
            db.execSQL(
                "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type) " +
                    "VALUES('T1',2,'B','08:30:00','08:30:00',0,0)",
            )
        }
        index.close()
    }

    private fun at(raw: String): Long =
        GtfsServiceTime.parse(raw).resolve(serviceDate, zone).epochMillis
}
