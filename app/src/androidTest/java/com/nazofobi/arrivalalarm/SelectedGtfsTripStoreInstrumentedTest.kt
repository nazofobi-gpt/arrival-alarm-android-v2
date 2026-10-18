package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectedGtfsTripStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var preferences: SharedPreferences
    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-42",
        serviceDate = GtfsServiceDate(2026, 10, 18),
        serviceId = "service-4",
        routeId = "route-9",
        directionId = 1,
    )
    private val selected = GtfsSelectedTrip(
        identity = identity,
        boarding = GtfsSelectedStopRef(2, "Lohne"),
        destination = GtfsSelectedStopRef(13, "Achim"),
        alarmTarget = GtfsSelectedStopRef(7, "Transfer"),
    )

    @Before fun cleanPreferences() {
        preferences = context.getSharedPreferences("g180_verified_trip_test", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
    }

    private fun stop(sequence: Int, id: String) = GtfsTripDetailStop(
        stopSequence = sequence,
        stopId = id,
        name = id,
        parentStationId = null,
        platformCode = null,
        wheelchairBoarding = null,
        scheduledArrival = "08:00:00",
        scheduledDeparture = "08:01:00",
    )

    private fun detail(
        trip: GtfsTripInstanceIdentity = identity,
        intermediateId: String = "Transfer",
    ) = GtfsTripDetail(
        identity = trip,
        headsign = "Achim",
        stops = listOf(
            stop(2, "Lohne"),
            stop(7, intermediateId),
            stop(13, "Achim"),
        ),
    )

    private fun store(
        resolve: (GtfsTripInstanceIdentity) -> GtfsTripDetailResult = {
            GtfsTripDetailResult.Available(detail())
        },
    ) = SelectedGtfsTripStore(preferences, resolve)

    private fun assertRejected(
        expected: GtfsSelectionRestoreProblem,
        actual: GtfsSelectionRestore,
    ) {
        assertTrue(actual is GtfsSelectionRestore.Rejected)
        assertEquals(expected, (actual as GtfsSelectionRestore.Rejected).reason)
    }

    @Test fun exactServiceDateAndOrderedRoleRefsSurviveProcessRecreation() {
        val first = store()
        assertEquals(GtfsSelectionRestore.Empty, first.restore())
        assertTrue(first.save(selected))
        // A new store instance simulates recreation; no values come from old in-memory state.
        val restored = store().restore() as GtfsSelectionRestore.Verified
        assertEquals(identity, restored.selection.identity)
        assertEquals(selected, restored.selection)
        assertEquals(listOf(2, 7, 13), restored.detail.stops.map { it.stopSequence })
        assertEquals("Transfer", restored.selection.alarmTarget.stopId)
    }

    @Test fun changedStopIdWithSameSequenceCannotSilentlyReselect() {
        assertTrue(store().save(selected))
        val persisted = preferences.getString(SelectedGtfsTripStore.KEY, null)
        assertNotNull(persisted)
        val changedStatic = store {
            GtfsTripDetailResult.Available(detail(intermediateId = "Different Stop"))
        }
        assertRejected(GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED, changedStatic.restore())
        // Keep evidence instead of clearing the user's choice or automatically switching stops.
        assertEquals(persisted, preferences.getString(SelectedGtfsTripStore.KEY, null))
    }

    @Test fun reusedTripIdWithDifferentServiceOrDirectionFailsClosed() {
        assertTrue(store().save(selected))
        val changed = store {
            GtfsTripDetailResult.Available(detail(trip = identity.copy(serviceId = "new-service")))
        }
        assertRejected(GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED, changed.restore())
        val otherDate = store {
            GtfsTripDetailResult.Available(
                detail(trip = identity.copy(serviceDate = GtfsServiceDate(2026, 10, 19)))
            )
        }
        assertRejected(GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED, otherDate.restore())
    }

    @Test fun inactiveOrUnavailableScheduleIsNotAConfirmedSelection() {
        assertTrue(store().save(selected))
        assertRejected(
            GtfsSelectionRestoreProblem.TRIP_UNAVAILABLE,
            store { GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.SERVICE_INACTIVE) }.restore(),
        )
    }

    @Test fun wrongTypedOrCorruptedSnapshotRejectsWithoutPartialRecovery() {
        assertTrue(store().save(selected))
        val original = requireNotNull(preferences.getString(SelectedGtfsTripStore.KEY, null))
        assertTrue(
            preferences.edit()
                .putString(SelectedGtfsTripStore.KEY, original.replace("\"version\":1", "\"version\":\"1\""))
                .commit()
        )
        assertRejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT, store().restore())

        assertTrue(preferences.edit().putString(SelectedGtfsTripStore.KEY, "{not json").commit())
        assertRejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT, store().restore())
    }

    @Test fun invalidRoleOrderingNeverOverwritesKnownGoodChoice() {
        assertTrue(store().save(selected))
        val original = preferences.getString(SelectedGtfsTripStore.KEY, null)
        assertFalse(
            store().save(
                selected.copy(
                    boarding = GtfsSelectedStopRef(13, "Achim"),
                    destination = GtfsSelectedStopRef(2, "Lohne"),
                )
            )
        )
        assertFalse(store().save(selected.copy(alarmTarget = GtfsSelectedStopRef(7, "Other Stop"))))
        assertEquals(original, preferences.getString(SelectedGtfsTripStore.KEY, null))
        assertTrue(store().restore() is GtfsSelectionRestore.Verified)
    }

    @Test fun repeatedStopIdIsBoundToExplicitSequenceNotFirstMatch() {
        val loop = detail().copy(stops = listOf(
            stop(2, "Lohne"),
            stop(7, "Lohne"),
            stop(13, "Achim"),
        ))
        val looping = selected.copy(
            boarding = GtfsSelectedStopRef(7, "Lohne"),
            alarmTarget = GtfsSelectedStopRef(13, "Achim"),
        )
        val withLoop = store { GtfsTripDetailResult.Available(loop) }
        assertTrue(withLoop.save(looping))
        val result = withLoop.restore() as GtfsSelectionRestore.Verified
        assertEquals(7, result.selection.boarding.sequence)
        assertEquals("Lohne", result.selection.boarding.stopId)
    }
}
