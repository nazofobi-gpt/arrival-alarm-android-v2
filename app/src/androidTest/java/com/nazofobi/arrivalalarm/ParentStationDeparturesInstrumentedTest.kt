package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParentStationDeparturesInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val timeZone = TimeZone.getTimeZone("Europe/Berlin")

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun parentStationShowsChildPlatformsWithoutRoutingAndPreservesPlatform() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        try {
            val db = index.writableDatabase
            db.execSQL(
                "INSERT OR REPLACE INTO metadata(key,value) VALUES " +
                    "('ready','1'),('fetched_at','1234'),('stop_count','3')," +
                    "('source_version','platform-fixture'),('schema_version','4')",
            )
            db.execSQL(
                "INSERT INTO stops(id,name,lat,lon,location_type) " +
                    "VALUES('HBF','Central Station',52.0,8.0,1)",
            )
            db.execSQL(
                "INSERT INTO stops(id,name,lat,lon,parent_station,location_type,platform_code) " +
                    "VALUES('P1','Platform 1',52.0,8.0,'HBF',0,'1')",
            )
            db.execSQL(
                "INSERT INTO stops(id,name,lat,lon,parent_station,location_type,platform_code) " +
                    "VALUES('P2','Platform 2',52.0,8.0,'HBF',0,'2')",
            )
            db.execSQL(
                "INSERT INTO routes(route_id,short_name,route_type) VALUES('R','RE 1',2)",
            )
            db.execSQL(
                "INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) " +
                    "VALUES('S',0,0,0,0,0,0,1,'20260101','20261231')",
            )
            listOf(
                Triple("T1", "P1", "08:00:00"),
                Triple("T2", "P2", "08:05:00"),
            ).forEach { (trip, stop, departureTime) ->
                db.execSQL(
                    "INSERT INTO trips(trip_id,route_id,service_id,headsign) VALUES(?,?,?,?)",
                    arrayOf(trip, "R", "S", "Destination"),
                )
                db.execSQL(
                    "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) " +
                        "VALUES(?,1,?,?,?)",
                    arrayOf(trip, stop, departureTime, departureTime),
                )
            }

            val now = GtfsServiceTime.parse("07:55:00")
                .resolve(GtfsServiceDate(2026, 10, 4), timeZone).epochMillis
            val planner = ProductionStationDeparturesPlanner(index, nowMillis = { now })
            val stationBoard = planner.plan("HBF", limit = 8)

            assertEquals(listOf("T1", "T2"), stationBoard.map { it.tripId })
            assertEquals(listOf("1", "2"), stationBoard.map { it.platform })
            assertTrue(stationBoard.all { it.scheduledEpochSeconds > now / 1_000L })
            assertEquals(listOf("T1"), planner.plan("P1", limit = 8).map { it.tripId })
        } finally {
            index.close()
        }
    }

    @Test
    fun invalidOrUnreadyParentStationDoesNotProduceFakeDepartures() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        try {
            val planner = ProductionStationDeparturesPlanner(index)
            assertTrue(planner.plan("HBF", limit = 8).isEmpty())
            assertTrue(planner.plan("", limit = 8).isEmpty())
        } finally {
            index.close()
        }
    }
}
