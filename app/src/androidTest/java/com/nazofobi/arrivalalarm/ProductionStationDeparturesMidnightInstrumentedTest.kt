package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A station's next departures span GTFS service-day boundaries. A trip at 24:05
 * belongs to Saturday; a trip at 00:10 belongs to Sunday. Both must be visible
 * shortly before midnight, including with live providers entirely unavailable.
 */
@RunWith(AndroidJUnit4::class)
class ProductionStationDeparturesMidnightInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = TimeZone.getTimeZone("Europe/Berlin")

    @Test
    fun lateEveningBoardIncludesExtendedTodayAndEarlyTomorrowInTimeOrder() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        try {
            val db = index.writableDatabase
            db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('ready','1')")
            db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('A','Station A',52.0,8.0)")
            db.execSQL("INSERT INTO routes(route_id,short_name,route_type) VALUES('R','R1',2)")
            db.execSQL(
                "INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) " +
                    "VALUES('SATURDAY',0,0,0,0,0,1,0,'20260101','20261231')"
            )
            db.execSQL(
                "INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) " +
                    "VALUES('SUNDAY',0,0,0,0,0,0,1,'20260101','20261231')"
            )
            db.execSQL(
                "INSERT INTO trips(trip_id,route_id,service_id,headsign) VALUES('SAT_2405','R','SATURDAY','Overnight')"
            )
            db.execSQL(
                "INSERT INTO trips(trip_id,route_id,service_id,headsign) VALUES('SUN_0010','R','SUNDAY','Next day')"
            )
            db.execSQL(
                "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('SAT_2405',1,'A','24:05:00','24:05:00')"
            )
            db.execSQL(
                "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('SUN_0010',1,'A','00:10:00','00:10:00')"
            )

            val now = GregorianCalendar(zone).apply {
                set(2026, Calendar.OCTOBER, 3, 23, 55, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val planner = ProductionStationDeparturesPlanner(
                index = index,
                nowMillis = { now },
                agencyTimeZone = zone,
            )
            val departures = planner.plan(stopId = "A", limit = 4)

            assertEquals(listOf("SAT_2405", "SUN_0010"), departures.map { it.tripId })
            assertTrue(departures.all { it.scheduledEpochSeconds * 1_000L > now })
            assertEquals(
                5 * 60L,
                departures[1].scheduledEpochSeconds - departures[0].scheduledEpochSeconds,
            )
        } finally {
            index.close()
            context.deleteDatabase("nationwide_transit.db")
        }
    }
}
