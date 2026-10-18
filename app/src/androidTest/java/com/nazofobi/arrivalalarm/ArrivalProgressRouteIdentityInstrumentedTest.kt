package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrivalProgressRouteIdentityInstrumentedTest {
    private val preferences by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            "arrival_progress_route_identity_test",
            Context.MODE_PRIVATE,
        )
    }
    private val store by lazy { SharedPreferencesArrivalProgressStore(preferences) }
    private val route = ArmedRoute(
        routeId = "trip-123",
        stops = listOf(
            GeoPoint(52.0, 8.0),
            GeoPoint(52.01, 8.0),
            GeoPoint(52.02, 8.0),
        ),
        targetIndex = 1,
    )

    private fun fix(point: GeoPoint) = LocationFix(point, accuracyMeters = 5.0, speedMps = 0.0)

    @Before fun clearBefore() {
        preferences.edit().clear().commit()
    }

    @After fun clearAfter() {
        preferences.edit().clear().commit()
    }

    @Test fun sameTargetRestoresArrivalButDifferentTargetRequiresFreshFixes() {
        ArrivalProgressController(store).apply {
            arm(route)
            repeat(2) { onLocation(fix(route.stops[1])) }
            assertEquals(ArrivalAlertPhase.ARRIVED, state.phase)
        }
        assertEquals(route.persistenceFingerprint, store.load()?.routeFingerprint)

        ArrivalProgressController(store).apply {
            arm(route.copy())
            assertEquals(ArrivalAlertPhase.ARRIVED, state.phase)
            assertEquals(1, state.arrivalEventCount)
        }

        ArrivalProgressController(store).apply {
            val newTarget = route.copy(targetIndex = 2)
            arm(newTarget)
            assertEquals(ArrivalAlertPhase.TRACKING, state.phase)
            assertEquals(0, state.arrivalEventCount)
            onLocation(fix(newTarget.stops[2]))
            assertEquals(ArrivalAlertPhase.IMMINENT, state.phase)
            onLocation(fix(newTarget.stops[2]))
            assertEquals(ArrivalAlertPhase.ARRIVED, state.phase)
        }
    }

    @Test fun oldPreferencesWithoutTargetFingerprintCannotReplayArrival() {
        preferences.edit()
            .putString("route_id", route.routeId)
            .putBoolean("armed", true)
            .putString("phase", "ARRIVED")
            .putInt("arrival_event_count", 1)
            .commit()
        assertNull(store.load()?.routeFingerprint)

        val restored = ArrivalProgressController(store)
        restored.arm(route)
        assertEquals(ArrivalAlertPhase.TRACKING, restored.state.phase)
        assertEquals(0, restored.state.arrivalEventCount)
        assertEquals(route.persistenceFingerprint, store.load()?.routeFingerprint)
    }
}
