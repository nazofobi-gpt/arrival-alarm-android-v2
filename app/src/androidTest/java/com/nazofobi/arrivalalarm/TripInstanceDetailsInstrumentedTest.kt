package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripInstanceDetailsInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun realSqliteGraphYieldsExactOrderedStopsAndServiceDayValidation() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        try {
            val db = index.writableDatabase
            db.execSQL("INSERT INTO metadata(key,value) VALUES('ready','1')")
            db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('A','Bremen Hbf',53.083,8.813)")
            db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,platform_code) VALUES('B','Bremen-Mahndorf',53.034,8.942,'B-station','2')")
            db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('C','Achim',53.013,9.032)")
            db.execSQL("INSERT INTO routes(route_id,short_name,route_type) VALUES('R','RE1',2)")
            db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) VALUES('T','R','S','Achim',1)")
            // Deliberately insert out of order: the reader must use real GTFS stop_sequence.
            db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('T',30,'C','24:35:00','24:35:00')")
            db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('T',10,'A','23:55:00','23:56:00')")
            db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('T',20,'B','24:18:00','24:19:00')")
            db.execSQL("INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) VALUES('S',1,0,0,0,0,0,0,'20261001','20261031')")

            val reader = GtfsTripInstanceDetailReader(index)
            val key = TripInstanceIdentity("T", GtfsServiceDate.parse("20261012"), 1)
            val found = reader.resolve(key)
            assertTrue(found is TripInstanceDetailResult.Found)
            val details = (found as TripInstanceDetailResult.Found).details
            assertEquals(listOf(10, 20, 30), details.stops.map { it.stopSequence })
            assertEquals(listOf("A", "B", "C"), details.stops.map { it.stopId })
            assertEquals("B-station", details.stops[1].parentStationId)
            assertEquals("2", details.stops[1].platformCode)
            assertEquals("24:35:00", details.stops.last().arrivalTime)
            assertEquals(
                TripInstanceDetailResult.Unavailable(TripInstanceDetailResult.Reason.DIRECTION_CHANGED),
                reader.resolve(key.copy(directionId = 0)),
            )
            assertEquals(
                TripInstanceDetailResult.Unavailable(TripInstanceDetailResult.Reason.SERVICE_NOT_ACTIVE),
                reader.resolve(key.copy(serviceDate = GtfsServiceDate.parse("20261013"))),
            )
            db.execSQL("INSERT INTO calendar_dates(service_id,date,exception_type) VALUES('S','20261012',2)")
            assertEquals(
                TripInstanceDetailResult.Unavailable(TripInstanceDetailResult.Reason.SERVICE_NOT_ACTIVE),
                reader.resolve(key),
            )
        } finally {
            index.close()
        }
    }
}
