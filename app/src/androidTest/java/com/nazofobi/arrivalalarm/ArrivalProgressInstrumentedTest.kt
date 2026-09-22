package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrivalProgressInstrumentedTest {
    @Test fun sharedPreferencesRestoresArmedProgressOnApi36() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("arrival_progress_g134_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = SharedPreferencesArrivalProgressStore(prefs)
        val route = ArmedRoute(
            routeId = "api36-route",
            stops = listOf(
                GeoPoint(53.0830, 8.8130),
                GeoPoint(53.0735, 8.8065),
                GeoPoint(53.0618, 8.7908),
            ),
            targetIndex = 2,
        )

        val first = ArrivalProgressController(store)
        first.arm(route)
        first.onLocation(LocationFix(route.stops[1], accuracyMeters = 8.0, speedMps = 9.0))
        assertTrue(first.state.lastMatchedStopIndex >= 1)

        val recreated = ArrivalProgressController(store)
        recreated.arm(route)
        assertTrue(recreated.state.armed)
        assertTrue(recreated.state.lastMatchedStopIndex >= 1)

        val target = route.stops[2]
        recreated.onLocation(LocationFix(target, accuracyMeters = 6.0, speedMps = 5.0))
        recreated.onLocation(LocationFix(target, accuracyMeters = 6.0, speedMps = 5.0))
        assertEquals(ArrivalAlertPhase.ARRIVED, recreated.state.phase)
        assertEquals(1, recreated.state.arrivalEventCount)

        val secondRecreation = ArrivalProgressController(store)
        secondRecreation.arm(route)
        secondRecreation.onLocation(LocationFix(route.stops[0], accuracyMeters = 30.0, speedMps = 0.0))
        assertEquals(ArrivalAlertPhase.ARRIVED, secondRecreation.state.phase)
        assertEquals(1, secondRecreation.state.arrivalEventCount)
        prefs.edit().clear().commit()
    }
}
