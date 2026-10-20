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

    private fun serviceTimeEpoch(clock: String): Long =
        GtfsServiceTime.parse(clock)
            .resolve(identity.serviceDate, java.util.TimeZone.getTimeZone("Europe/Berlin"))
            .epochMillis / 1_000L

    private fun scheduledDeparture() = Departure(
        tripId = identity.tripId,
        line = "RE1",
        direction = "Achim",
        scheduledEpochSeconds = serviceTimeEpoch("08:01:00"),
        routeId = identity.routeId,
        gtfsInstance = identity,
        boardingStopSequence = 10,
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
    @Test fun boardInspectionExposesExactStopsWithoutSavingOrArming() = runBlocking {
        seedSchedule()
        AndroidSelectedGtfsTripSession(context).use { session ->
            val inspected = session.inspect(scheduledDeparture(), "A")
            assertTrue(inspected is GtfsDepartureInspection.Ready)
            val preview = inspected as GtfsDepartureInspection.Ready
            assertEquals(identity, preview.detail.identity)
            assertEquals(10, preview.boarding.stopSequence)
            assertEquals("A", preview.boarding.stopId)
            assertEquals(listOf(10, 20, 30), preview.detail.stops.map { it.stopSequence })
            // A departure preview is not user approval of destination/alarm roles.
            assertEquals(GtfsSelectionRestore.Empty, session.restore())
        }
    }

    @Test fun boardInspectionRejectsSyntheticCancelledOrConflictingEvidence() = runBlocking {
        seedSchedule()
        AndroidSelectedGtfsTripSession(context).use { session ->
            val valid = scheduledDeparture()
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.SOURCE_IDENTITY_MISSING,
                ),
                session.inspect(valid.copy(gtfsInstance = null), "A"),
            )
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.SOURCE_IDENTITY_MISSING,
                ),
                session.inspect(valid.copy(boardingStopSequence = null), "A"),
            )
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.CANCELLED_DEPARTURE,
                ),
                session.inspect(valid.copy(cancelled = true), "A"),
            )
            for (invalid in listOf(
                valid.copy(tripId = "another-trip"),
                valid.copy(routeId = "another-route"),
                valid.copy(boardingStopSequence = 99),
                valid.copy(boardingStopSequence = -1),
                valid.copy(scheduledEpochSeconds = 0L),
            )) {
                assertEquals(
                    GtfsDepartureInspection.Rejected(
                        GtfsDepartureInspectionFailure.SOURCE_EVIDENCE_MISMATCH,
                    ),
                    session.inspect(invalid, "A"),
                )
            }
            // The sequence alone is insufficient: a platform or loop stop may differ.
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.SOURCE_EVIDENCE_MISMATCH,
                ),
                session.inspect(valid, "B"),
            )
            assertEquals(GtfsSelectionRestore.Empty, session.restore())
        }
    }

    @Test fun boardInspectionRejectsDepartureTimeNotMatchingGtfsServiceDay() = runBlocking {
        seedSchedule()
        AndroidSelectedGtfsTripSession(context).use { session ->
            val tampered = scheduledDeparture().copy(
                scheduledEpochSeconds = serviceTimeEpoch("08:02:00"),
            )
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.SOURCE_EVIDENCE_MISMATCH,
                ),
                session.inspect(tampered, "A"),
            )
            assertEquals(GtfsSelectionRestore.Empty, session.restore())
        }
    }

    @Test fun boardInspectionRejectsDisallowedPickupAndUnavailableIndex() = runBlocking {
        AndroidSelectedGtfsTripSession(context).use { missing ->
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.TRIP_UNAVAILABLE,
                ),
                missing.inspect(scheduledDeparture(), "A"),
            )
        }
        seedSchedule()
        NationwideTransitIndex(context).use { index ->
            index.writableDatabase.execSQL(
                "UPDATE stop_times SET pickup_type=1 WHERE trip_id='T' AND stop_sequence=10"
            )
        }
        val session = AndroidSelectedGtfsTripSession(context)
        assertEquals(
            GtfsDepartureInspection.Rejected(
                GtfsDepartureInspectionFailure.BOARDING_NOT_ALLOWED,
            ),
            session.inspect(scheduledDeparture(), "A"),
        )
        session.close()
        assertEquals(
            GtfsDepartureInspection.Rejected(
                GtfsDepartureInspectionFailure.SESSION_CLOSED,
            ),
            session.inspect(scheduledDeparture(), "A"),
        )
    }

    @Test fun boardInspectionRejectsTerminalAndNonAlightableDownstreamStops() = runBlocking {
        seedSchedule()
        AndroidSelectedGtfsTripSession(context).use { session ->
            // A terminal boarding event must not fabricate an alighting destination.
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.NO_DOWNSTREAM_ALIGHTING_STOP,
                ),
                session.inspect(
                    scheduledDeparture().copy(
                        boardingStopSequence = 30,
                        scheduledEpochSeconds = serviceTimeEpoch("09:00:00"),
                    ),
                    "C",
                ),
            )
            NationwideTransitIndex(context).use { index ->
                index.writableDatabase.execSQL(
                    "UPDATE stop_times SET drop_off_type=1 WHERE trip_id='T' AND stop_sequence>10"
                )
            }
            // Later physical stops are insufficient when alighting is forbidden there.
            assertEquals(
                GtfsDepartureInspection.Rejected(
                    GtfsDepartureInspectionFailure.NO_DOWNSTREAM_ALIGHTING_STOP,
                ),
                session.inspect(scheduledDeparture(), "A"),
            )
            assertEquals(GtfsSelectionRestore.Empty, session.restore())
        }
    }

}
