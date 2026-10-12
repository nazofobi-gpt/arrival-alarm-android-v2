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
class ArrivalProgressPersistenceSafetyInstrumentedTest {
    private val prefs by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            "arrival_progress_persistence_safety_test",
            Context.MODE_PRIVATE,
        )
    }
    private val store by lazy { SharedPreferencesArrivalProgressStore(prefs) }

    @Before fun clearBefore() {
        prefs.edit().clear().commit()
    }

    @After fun clearAfter() {
        prefs.edit().clear().commit()
    }

    @Test fun wrongTypedLegacyPreferencesCannotCrashRestoration() {
        prefs.edit().putInt("route_id", 123).commit()
        assertNull(store.load())

        prefs.edit().clear().putString("route_id", "journey")
            .putString("phase", "TRACKING").putString("armed", "true").commit()
        assertNull(store.load())
    }

    @Test fun unknownPhaseAndArmedIdleStateFailClosed() {
        prefs.edit().putString("route_id", "journey")
            .putBoolean("armed", true).putString("phase", "LEGACY_PHASE").commit()
        assertNull(store.load())

        prefs.edit().putString("phase", "IDLE").commit()
        assertNull(store.load())
    }

    @Test fun malformedStoredProgressAndCountersFailClosed() {
        val valid = ArrivalProgressState(
            routeId = "journey",
            armed = true,
            phase = ArrivalAlertPhase.TRACKING,
            progressFraction = 0.5,
        )
        store.save(valid)
        assertEquals(valid, store.load())

        prefs.edit().putFloat("progress", Float.POSITIVE_INFINITY).commit()
        assertNull(store.load())

        store.save(valid)
        prefs.edit().putFloat("distance", -5f).commit()
        assertNull(store.load())

        store.save(valid)
        prefs.edit().putInt("inside_count", -1).commit()
        assertNull(store.load())

        store.save(valid)
        prefs.edit().putInt("last_index", -1).commit()
        assertNull(store.load())
    }

    @Test fun validTerminalArrivalStillSurvivesProcessRecreation() {
        val arrived = ArrivalProgressState(
            routeId = "journey",
            armed = true,
            phase = ArrivalAlertPhase.ARRIVED,
            lastMatchedStopIndex = 2,
            progressFraction = 1.0,
            distanceToTargetMeters = 4.0,
            adaptiveThresholdMeters = 180.0,
            consecutiveInsideThreshold = 2,
            arrivalEventCount = 1,
        )
        store.save(arrived)
        assertEquals(arrived, store.load())
    }
}
