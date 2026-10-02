package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsScheduleRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun typedQueriesReturnOrderedScheduleRowsAndEmptyCases() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val db = index.writableDatabase

        db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,location_type,platform_code,wheelchair_boarding) VALUES('A','Alpha',52.0,8.0,NULL,1,NULL,1)")
        db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,location_type,platform_code,wheelchair_boarding) VALUES('B','Beta',52.1,8.1,NULL,0,'2',2)")
        db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,location_type) VALUES('A-2','Alpha platform 2',52.0,8.0,'A',0)")
        db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,location_type) VALUES('A-1','Alpha platform 1',52.0,8.0,'A',0)")
        db.execSQL("INSERT INTO routes(route_id,agency_id,short_name,long_name,route_type) VALUES('R','AG','RE1','Regional Express',2)")
        db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id,shape_id,wheelchair_accessible,bikes_allowed) VALUES('T','R','S','Beta',0,'SH',1,1)")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type,shape_dist_traveled,timepoint) VALUES('T',2,'B','25:10:00','25:11:00',0,0,10.0,1)")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type,shape_dist_traveled,timepoint) VALUES('T',1,'A','23:55:00','23:56:00',0,0,0.0,1)")
        db.execSQL("INSERT INTO transfers(from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_route_id,to_route_id,from_trip_id,to_trip_id) VALUES('A','B',2,180,NULL,NULL,NULL,NULL)")
        db.execSQL("INSERT INTO shapes(shape_id,sequence,lat,lon,dist_traveled) VALUES('SH',2,52.1,8.1,10.0)")
        db.execSQL("INSERT INTO shapes(shape_id,sequence,lat,lon,dist_traveled) VALUES('SH',1,52.0,8.0,0.0)")
        db.execSQL("INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) VALUES('S',1,1,1,1,1,0,0,'20260101','20261231')")
        db.execSQL("INSERT INTO calendar_dates(service_id,date,exception_type) VALUES('S','20261003',1)")

        val repo = GtfsScheduleRepository(index)

        assertEquals("Alpha", repo.stop("A")?.name)
        assertEquals(listOf("A-1"), repo.childStops("A", limit = 1).map { it.id })
        assertEquals("RE1", repo.route("R")?.shortName)
        assertEquals("S", repo.trip("T")?.serviceId)
        assertEquals(listOf(1, 2), repo.stopTimesForTrip("T").map { it.stopSequence })
        assertEquals(listOf("T"), repo.candidateTripsAtStop("A").map { it.trip.id })
        assertEquals(180, repo.transfersFromStop("A").single().minTransferTimeSeconds)
        assertEquals(listOf(1, 2), repo.shapePoints("SH").map { it.sequence })
        assertTrue(repo.calendar("S")!!.monday)
        assertEquals(1, repo.calendarException("S", "20261003")?.exceptionType)

        assertNull(repo.stop("missing"))
        assertNull(repo.trip("missing"))
        assertTrue(repo.stopTimesForTrip("missing").isEmpty())
        assertTrue(repo.candidateTripsAtStop("missing").isEmpty())
        assertTrue(repo.transfersFromStop("missing").isEmpty())
        assertTrue(repo.childStops("missing").isEmpty())
        assertTrue(repo.shapePoints("missing").isEmpty())

        index.close()
    }

    @Test
    fun candidateAndSequenceQueriesAreBoundedAndUseIndexedPredicates() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val db = index.writableDatabase

        db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('A','Alpha',52.0,8.0)")
        db.execSQL("INSERT INTO routes(route_id,short_name,route_type) VALUES('R','R',2)")
        repeat(120) { i ->
            val tripId = "T%03d".format(i)
            db.execSQL(
                "INSERT INTO trips(trip_id,route_id,service_id,headsign) VALUES(?,?,?,?)",
                arrayOf(tripId, "R", "S", "Destination $i"),
            )
            db.execSQL(
                "INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES(?,?,?,?,?)",
                arrayOf(tripId, 1, "A", "08:00:00", "08:00:00"),
            )
            repeat(20) { seq ->
                db.execSQL(
                    "INSERT OR REPLACE INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES(?,?,?,?,?)",
                    arrayOf(tripId, seq + 2, "B-$seq", "08:10:00", "08:10:00"),
                )
            }
        }

        val repo = GtfsScheduleRepository(index)
        assertEquals(7, repo.candidateTripsAtStop("A", limit = 7).size)
        assertEquals(5, repo.stopTimesForTrip("T000", limit = 5).size)

        val stopPlan = db.rawQuery(
            "EXPLAIN QUERY PLAN SELECT trip_id FROM stop_times WHERE stop_id=? LIMIT 5",
            arrayOf("A"),
        ).use { cursor ->
            buildList {
                val detail = cursor.getColumnIndexOrThrow("detail")
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }
        }
        val childPlan = db.rawQuery(
            "EXPLAIN QUERY PLAN SELECT id FROM stops WHERE parent_station=? ORDER BY id LIMIT 5",
            arrayOf("A"),
        ).use { cursor ->
            buildList {
                val detail = cursor.getColumnIndexOrThrow("detail")
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }
        }
        val tripPlan = db.rawQuery(
            "EXPLAIN QUERY PLAN SELECT stop_sequence FROM stop_times WHERE trip_id=? ORDER BY stop_sequence LIMIT 5",
            arrayOf("T000"),
        ).use { cursor ->
            buildList {
                val detail = cursor.getColumnIndexOrThrow("detail")
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }
        }

        assertTrue(stopPlan.any { it.contains("idx_stop_times_stop", ignoreCase = true) })
        assertTrue(childPlan.any { it.contains("idx_stops_parent", ignoreCase = true) })
        assertFalse(tripPlan.any { it.contains("SCAN stop_times", ignoreCase = true) })

        index.close()
    }
}
