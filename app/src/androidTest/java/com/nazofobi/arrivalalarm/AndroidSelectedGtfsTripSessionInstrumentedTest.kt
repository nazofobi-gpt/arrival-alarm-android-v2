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

    @Test fun absentOfflineIndexCannotConfirmAProvisionalTrip() = runBlocking {
        AndroidSelectedGtfsTripSession(context).use { session ->
            assertFalse(session.confirm(selection))
            assertEquals(GtfsSelectionRestore.Empty, session.restore())
        }
    }


    @Test fun previewIsSourceBoundAndDoesNotSilentlyCreateASelection() = runBlocking {
        seedSchedule()
        val sourceDeparture = Departure(
            tripId = "T",
            line = "RE1",
            direction = "Achim",
            scheduledEpochSeconds = 1_790_000_000L,
            routeId = "R",
            gtfsInstance = identity,
            boardingStopSequence = 10,
        )
        AndroidSelectedGtfsTripSession(context).use { session ->
            val available = session.preview(sourceDeparture)
            assertTrue(available is DepartureTripPreview.Available)
            val preview = available as DepartureTripPreview.Available
            assertEquals(identity, preview.detail.identity)
            assertEquals("A", preview.boarding.stopId)
            assertEquals(10, preview.boarding.stopSequence)
            // Preview is strictly informational; no persisted consent or alarm target.
            assertEquals(GtfsSelectionRestore.Empty, session.restore())

            assertEquals(
                DepartureTripPreview.Reason.MISSING_SOURCE_IDENTITY,
                (session.preview(sourceDeparture.copy(gtfsInstance = null))
                    as DepartureTripPreview.Unavailable).reason,
            )
            assertEquals(
                DepartureTripPreview.Reason.MISSING_SOURCE_IDENTITY,
                (session.preview(sourceDeparture.copy(boardingStopSequence = null))
                    as DepartureTripPreview.Unavailable).reason,
            )
            assertEquals(
                DepartureTripPreview.Reason.SOURCE_MISMATCH,
                (session.preview(sourceDeparture.copy(tripId = "DIFFERENT"))
                    as DepartureTripPreview.Unavailable).reason,
            )
            assertEquals(
                DepartureTripPreview.Reason.SOURCE_MISMATCH,
                (session.preview(sourceDeparture.copy(routeId = "DIFFERENT"))
                    as DepartureTripPreview.Unavailable).reason,
            )
            assertEquals(
                DepartureTripPreview.Reason.BOARDING_UNAVAILABLE,
                (session.preview(sourceDeparture.copy(boardingStopSequence = 999))
                    as DepartureTripPreview.Unavailable).reason,
            )
            assertEquals(GtfsSelectionRestore.Empty, session.restore())
        }
    }

    @Test fun previewRejectsUnavailableIndexAndClosedSession() = runBlocking {
        val departure = Departure(
            tripId = "T", line = "RE1", direction = "Achim",
            scheduledEpochSeconds = 1_790_000_000L,
            routeId = "R", gtfsInstance = identity, boardingStopSequence = 10,
        )
        val session = AndroidSelectedGtfsTripSession(context)
        assertEquals(
            DepartureTripPreview.Reason.INDEX_UNAVAILABLE,
            (session.preview(departure) as DepartureTripPreview.Unavailable).reason,
        )
        session.close()
        assertEquals(
            DepartureTripPreview.Reason.SESSION_CLOSED,
            (session.preview(departure) as DepartureTripPreview.Unavailable).reason,
        )
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
