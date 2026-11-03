package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrivalTargetLabelStoreInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun namedStopSurvivesRecreationWithoutChangingTheArmedCoordinates() {
        val first = ArrivalTargetLabelStore(context)
        first.clear()
        try {
            assertTrue(first.save(MapPoint(52.669, 8.238, "Lohne Bahnhof")))
            val recreated = ArrivalTargetLabelStore(context)
            assertEquals("Lohne Bahnhof", recreated.labelFor(GeoPoint(52.669, 8.238)))
            assertNull(recreated.labelFor(GeoPoint(52.6691, 8.238)))
            assertNull(recreated.labelFor(GeoPoint(52.669, 8.2381)))
        } finally {
            first.clear()
        }
    }

    @Test fun blankControlAndInvalidCoordinateLabelsFailClosed() {
        val store = ArrivalTargetLabelStore(context)
        store.clear()
        try {
            assertTrue(store.save(MapPoint(52.669, 8.238, "Lohne Bahnhof")))
            assertFalse(store.save(MapPoint(52.669, 8.238, " \n ")))
            assertNull(store.labelFor(GeoPoint(52.669, 8.238)))
            assertFalse(store.save(MapPoint(Double.NaN, 8.238, "Home")))
            assertFalse(store.save(MapPoint(52.669, 8.238, "Home\u202Espoof")))
            assertNull(store.labelFor(GeoPoint(52.669, 8.238)))
        } finally {
            store.clear()
        }
    }

    @Test fun clearingOnOriginChangeNeverReusesOldDestinationLabel() {
        val store = ArrivalTargetLabelStore(context)
        store.clear()
        try {
            assertTrue(store.save(MapPoint(52.669, 8.238, "Old trip")))
            store.clear()
            assertNull(ArrivalTargetLabelStore(context).labelFor(GeoPoint(52.669, 8.238)))
            assertTrue(store.save(MapPoint(52.669, 8.238, "Updated trip")))
            assertEquals("Updated trip", store.labelFor(GeoPoint(52.669, 8.238)))
        } finally {
            store.clear()
        }
    }
}
