package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
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

    private fun stop(sequence: Int, id: String): GtfsTripDetailStop {
        // Every ordinary fixture represents a feasible GTFS service-day journey.
        // Invalid stop chronology must be introduced explicitly by each negative test.
        val minute = when (sequence) {
            2 -> 0
            5 -> 5
            7 -> 10
            13 -> 20
            else -> error("Unexpected fixture stop sequence: $sequence")
        }
        val arrival = minute.toString().padStart(2, '0')
        val departure = (minute + 1).toString().padStart(2, '0')
        return GtfsTripDetailStop(
            stopSequence = sequence,
            stopId = id,
            name = id,
            parentStationId = null,
            platformCode = null,
            wheelchairBoarding = null,
            scheduledArrival = "08:$arrival:00",
            scheduledDeparture = "08:$departure:00",
            latitude = 52.665,
            longitude = 8.237,
        )
    }

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
        val wrongTypedVersion = JSONObject(original).apply {
            put("version", getInt("version").toString())
        }
        assertTrue(
            preferences.edit()
                .putString(SelectedGtfsTripStore.KEY, wrongTypedVersion.toString())
                .commit()
        )
        assertRejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT, store().restore())

        assertTrue(preferences.edit().putString(SelectedGtfsTripStore.KEY, "{not json").commit())
        assertRejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT, store().restore())
    }

    @Test fun oversizedStoredSnapshotIsRejectedWithoutJsonParsingOrResolving() {
        val oversized = "{".repeat(70_000)
        assertTrue(preferences.edit().putString(SelectedGtfsTripStore.KEY, oversized).commit())
        var resolverCalled = false
        val guarded = store {
            resolverCalled = true
            GtfsTripDetailResult.Available(detail())
        }

        assertRejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT, guarded.restore())
        assertFalse(resolverCalled)
        // Invalid evidence stays intact for explicit recovery; no phantom journey is armed.
        assertEquals(oversized, preferences.getString(SelectedGtfsTripStore.KEY, null))
    }

    @Test fun oversizedSelectionCannotReplacePreviouslyConfirmedTrip() {
        val normal = store()
        assertTrue(normal.save(selected))
        val before = preferences.getString(SelectedGtfsTripStore.KEY, null)
        val oversized = selected.copy(identity = identity.copy(tripId = "x".repeat(70_000)))
        var resolverCalled = false
        val withOversizedIdentity = store { trip ->
            resolverCalled = true
            GtfsTripDetailResult.Available(detail(trip = trip))
        }

        assertFalse(withOversizedIdentity.save(oversized))
        assertFalse(resolverCalled)
        assertEquals(before, preferences.getString(SelectedGtfsTripStore.KEY, null))
        assertTrue(normal.restore() is GtfsSelectionRestore.Verified)
    }

    @Test fun blankTripServiceOrRouteIdFailsBeforeResolverAndPreservesSelection() {
        val verifiedStore = store()
        assertTrue(verifiedStore.save(selected))
        val original = preferences.getString(SelectedGtfsTripStore.KEY, null)
        val invalidIdentities = listOf(
            identity.copy(tripId = " "),
            identity.copy(serviceId = ""),
            identity.copy(routeId = "\t"),
        )
        for (invalid in invalidIdentities) {
            var resolverCalled = false
            val permissiveResolver = store { requested ->
                resolverCalled = true
                GtfsTripDetailResult.Available(detail(trip = requested))
            }
            assertFalse(permissiveResolver.save(selected.copy(identity = invalid)))
            assertFalse(resolverCalled)
            assertEquals(original, preferences.getString(SelectedGtfsTripStore.KEY, null))
        }
        assertTrue(verifiedStore.restore() is GtfsSelectionRestore.Verified)
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

    @Test fun timetableShiftWithSameTripIdentityFailsClosed() {
        assertTrue(store().save(selected))
        val unchanged = preferences.getString(SelectedGtfsTripStore.KEY, null)
        val revised = detail().copy(
            stops = listOf(
                stop(2, "Lohne"),
                stop(7, "Transfer").copy(
                    scheduledArrival = "08:15:00", scheduledDeparture = "08:16:00",
                ),
                stop(13, "Achim"),
            ),
        )
        assertRejected(
            GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
            store { GtfsTripDetailResult.Available(revised) }.restore(),
        )
        assertEquals(unchanged, preferences.getString(SelectedGtfsTripStore.KEY, null))
    }

    @Test fun intermediateScheduleChangeOutsideExplicitRoleStopsAlsoFailsClosed() {
        val original = detail().copy(
            stops = listOf(stop(2, "Lohne"), stop(5, "Intermediate"),
                stop(7, "Transfer"), stop(13, "Achim")),
        )
        val initial = store { GtfsTripDetailResult.Available(original) }
        assertTrue(initial.save(selected))
        val revised = original.copy(
            stops = original.stops.map { stop ->
                if (stop.stopSequence == 5) stop.copy(scheduledDeparture = "08:06:30") else stop
            },
        )
        assertRejected(
            GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
            store { GtfsTripDetailResult.Available(revised) }.restore(),
        )
    }


    @Test fun oldSnapshotWithoutVerifiedScheduleRequiresNewSelection() {
        assertTrue(store().save(selected))
        val persisted = requireNotNull(preferences.getString(SelectedGtfsTripStore.KEY, null))
        val old = JSONObject(persisted).apply {
            put("version", 1)
            remove("schedule_fingerprint")
        }
        assertTrue(preferences.edit().putString(SelectedGtfsTripStore.KEY, old.toString()).commit())
        assertRejected(
            GtfsSelectionRestoreProblem.LEGACY_SNAPSHOT_REQUIRES_RESELECTION,
            store().restore(),
        )
    }

    @Test fun invalidFingerprintNeverRestoresAnUnprovenTrip() {
        assertTrue(store().save(selected))
        val persisted = requireNotNull(preferences.getString(SelectedGtfsTripStore.KEY, null))
        val corrupted = JSONObject(persisted).apply {
            put("schedule_fingerprint", "invalid")
        }
        assertTrue(preferences.edit().putString(
            SelectedGtfsTripStore.KEY, corrupted.toString(),
        ).commit())
        assertRejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT, store().restore())
    }


    @Test fun changedPlatformOrAccessibilityMetadataCannotRestoreOldSelection() {
        assertTrue(store().save(selected))
        val persisted = preferences.getString(SelectedGtfsTripStore.KEY, null)
        val updatedPlatform = detail().copy(stops = detail().stops.map { stop ->
            if (stop.stopSequence == 13) stop.copy(platformCode = "Gleis 4") else stop
        })
        assertRejected(
            GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
            store { GtfsTripDetailResult.Available(updatedPlatform) }.restore(),
        )
        val updatedAccessibility = detail().copy(stops = detail().stops.map { stop ->
            if (stop.stopSequence == 2) stop.copy(wheelchairBoarding = 2) else stop
        })
        assertRejected(
            GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
            store { GtfsTripDetailResult.Available(updatedAccessibility) }.restore(),
        )
        assertEquals(persisted, preferences.getString(SelectedGtfsTripStore.KEY, null))
    }

    @Test fun versionTwoSnapshotWithoutStopMetadataRequiresReselection() {
        assertTrue(store().save(selected))
        val persisted = requireNotNull(preferences.getString(SelectedGtfsTripStore.KEY, null))
        val legacy = JSONObject(persisted).apply { put("version", 2) }
        assertTrue(
            preferences.edit().putString(SelectedGtfsTripStore.KEY, legacy.toString()).commit()
        )
        assertRejected(
            GtfsSelectionRestoreProblem.LEGACY_SNAPSHOT_REQUIRES_RESELECTION,
            store().restore(),
        )
    }


    @Test fun nonBoardableAndNonAlightableStopsCannotBePersisted() {
        val noPickup = detail().copy(stops = detail().stops.map { stop ->
            if (stop.stopSequence == 2) stop.copy(pickupType = 1) else stop
        })
        assertFalse(store { GtfsTripDetailResult.Available(noPickup) }.save(selected))

        val requestOnlyDropOff = detail().copy(stops = detail().stops.map { stop ->
            if (stop.stopSequence == 13) stop.copy(dropOffType = 2) else stop
        })
        assertFalse(store { GtfsTripDetailResult.Available(requestOnlyDropOff) }.save(selected))

        val missingCoordinates = detail().copy(stops = detail().stops.map { stop ->
            if (stop.stopSequence == 7) stop.copy(latitude = Double.NaN) else stop
        })
        assertFalse(store { GtfsTripDetailResult.Available(missingCoordinates) }.save(selected))
        assertEquals(GtfsSelectionRestore.Empty, store().restore())
    }

    @Test fun physicalLocationAndIntermediateAccessChangesInvalidateOldConsent() {
        assertTrue(store().save(selected))
        val persisted = preferences.getString(SelectedGtfsTripStore.KEY, null)
        val movedStop = detail().copy(stops = detail().stops.map { stop ->
            if (stop.stopSequence == 7) stop.copy(latitude = 52.666) else stop
        })
        assertRejected(
            GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
            store { GtfsTripDetailResult.Available(movedStop) }.restore(),
        )
        val changedIntermediatePickup = detail().copy(stops = detail().stops.map { stop ->
            if (stop.stopSequence == 7) stop.copy(pickupType = 2) else stop
        })
        assertRejected(
            GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED,
            store { GtfsTripDetailResult.Available(changedIntermediatePickup) }.restore(),
        )
        assertEquals(persisted, preferences.getString(SelectedGtfsTripStore.KEY, null))
    }

    @Test fun oldVersionThreeSnapshotCannotBypassNewStopSafetyRules() {
        assertTrue(store().save(selected))
        val saved = requireNotNull(preferences.getString(SelectedGtfsTripStore.KEY, null))
        val legacy = JSONObject(saved).apply { put("version", 3) }
        assertTrue(preferences.edit().putString(
            SelectedGtfsTripStore.KEY, legacy.toString(),
        ).commit())
        assertRejected(
            GtfsSelectionRestoreProblem.LEGACY_SNAPSHOT_REQUIRES_RESELECTION,
            store().restore(),
        )
    }

}
