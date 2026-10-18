package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSelectedGtfsTripSessionInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val identity = GtfsTripInstanceIdentity(
        tripId = "T", serviceDate = GtfsServiceDate(2026, 10, 18),
        serviceId = "S", routeId = "R", directionId = 1,
    )
    private val selection = GtfsSelectedTrip(
        identity,
        GtfsSelectedStopRef(10, "A"),
        GtfsSelectedStopRef(30, "C"),
        GtfsSelectedStopRef(20, "B"),
    )

    @Before fun clean() {
        context.deleteDatabase("nationwide_transit.db")
        context.getSharedPreferences("selected_gtfs_trip_state", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
        context.getSharedPreferences("selected_gtfs_trip_state", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun seedSchedule() {
        NationwideTransitIndex(context).use { index ->
            val db = index.writableDatabase
            db.execSQL("INSERT INTO metadata(key,value) VALUES('ready','1')")
            db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('A','Lohne',52.66,8.24)")
            db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('B','Intermediate',52.7,8.3)")
            db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('C','Achim',53.0,9.0)")
            db.execSQL("INSERT INTO routes(route_id,short_name,route_type) VALUES('R','RE1',2)")
            db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) VALUES('T','R','S','Achim',1)")
            db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('T',30,'C','09:00:00','09:00:00')")
            db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('T',10,'A','08:00:00','08:01:00')")
            db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time) VALUES('T',20,'B','08:30:00','08:31:00')")
            db.execSQL("INSERT INTO calendar(service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date) VALUES('S',1,1,1,1,1,1,1,'20260101','20261231')")
        }
    }

    @Test fun validExplicitTripPersistsAcrossNewSessionAndRejectsScheduleMutation() = runBlocking {
        seedSchedule()
        AndroidSelectedGtfsTripSession(context).use { first ->
            assertTrue(first.confirm(selection))
            assertEquals(
                selection,
                (first.restore() as GtfsSelectionRestore.Verified).selection,
            )
        }
        AndroidSelectedGtfsTripSession(context).use { reopened ->
            assertEquals(
                selection,
                (reopened.restore() as GtfsSelectionRestore.Verified).selection,
            )
        }
        NationwideTransitIndex(context).use { index ->
            index.writableDatabase.execSQL(
                "UPDATE stop_times SET departure_time='08:45:00' WHERE trip_id='T' AND stop_sequence=20"
            )
        }
        AndroidSelectedGtfsTripSession(context).use { changed ->
            val result = changed.restore()
            assertEquals(
                GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
                (result as GtfsSelectionRestore.Rejected).reason,
            )
        }
    }

    @Test fun explicitRoleConfirmationRejectsProhibitedBoardingAndStaleCoordinates() = runBlocking {
        seedSchedule()
        val roles = TripStopRoles(10, 30, 20)
        AndroidSelectedGtfsTripSession(context).use { session ->
            assertEquals(selection, session.confirmRoles(identity, roles))
            assertEquals(selection, (session.restore() as GtfsSelectionRestore.Verified).selection)
            assertEquals(null, session.confirmRoles(identity, TripStopRoles(30, 10, 20)))
            assertFalse(session.confirm(selection.copy(boarding = GtfsSelectedStopRef(10, "wrong-stop"))))
            NationwideTransitIndex(context).use { index ->
                index.writableDatabase.execSQL(
                    "UPDATE stop_times SET pickup_type=1 WHERE trip_id='T' AND stop_sequence=10"
                )
            }
            assertEquals(null, session.confirmRoles(identity, roles))
            assertFalse(session.confirm(selection))
            assertEquals(
                GtfsSelectionRestoreProblem.INVALID_STOP_ROLES,
                (session.restore() as GtfsSelectionRestore.Rejected).reason,
            )
            NationwideTransitIndex(context).use { index ->
                index.writableDatabase.execSQL(
                    "UPDATE stop_times SET pickup_type=NULL WHERE trip_id='T' AND stop_sequence=10"
                )
                index.writableDatabase.execSQL(
                    "UPDATE stop_times SET drop_off_type=2 WHERE trip_id='T' AND stop_sequence=30"
                )
            }
            assertEquals(null, session.confirmRoles(identity, roles))
            assertFalse(session.confirm(selection))
            NationwideTransitIndex(context).use { index ->
                index.writableDatabase.execSQL(
                    "UPDATE stop_times SET drop_off_type=NULL WHERE trip_id='T' AND stop_sequence=30"
                )
                index.writableDatabase.execSQL(
                    "UPDATE stops SET lat=95.0 WHERE id='A'"
                )
            }
            assertEquals(null, session.confirmRoles(identity, roles))
            assertFalse(session.confirm(selection))
            assertEquals(
                GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
                (session.restore() as GtfsSelectionRestore.Rejected).reason,
            )
        }
    }

    @Test fun absentOfflineIndexCannotConfirmAProvisionalTrip() = runBlocking {
        AndroidSelectedGtfsTripSession(context).use { session ->
            assertFalse(session.confirm(selection))
            assertEquals(GtfsSelectionRestore.Empty, session.restore())
        }
    }

    @Test fun closedSessionNeverReusesOpenSQLiteResources() = runBlocking {
        seedSchedule()
        val session = AndroidSelectedGtfsTripSession(context)
        session.close()
        assertFalse(session.confirm(selection))
        assertEquals(
            GtfsSelectionRestoreProblem.TRIP_UNAVAILABLE,
            (session.restore() as GtfsSelectionRestore.Rejected).reason,
        )
    }
}
