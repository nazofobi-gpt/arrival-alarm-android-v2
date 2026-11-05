package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePackAutoUpdatePreferenceInstrumentedTest {
    @Test
    fun preferencePersistsAcrossContextRecreationAndManualRefreshRemainsPermitted() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences("g244-automatic-update-test-$name", mode)
        }
        val prefs = isolated.getSharedPreferences("offline_pack_controls", Context.MODE_PRIVATE)
        try {
            assertTrue(prefs.edit().clear().commit())
            assertTrue(OfflinePackAutoUpdatePreference.isEnabled(isolated))
            assertTrue(OfflinePackAutoUpdatePreference.persist(isolated, false))
            assertFalse(OfflinePackAutoUpdatePreference.isEnabled(isolated))
            assertFalse(shouldRunPackRefresh(autoEnabled = false, manualRequest = false))
            assertTrue(shouldRunPackRefresh(autoEnabled = false, manualRequest = true))
            assertTrue(OfflinePackAutoUpdatePreference.persist(isolated, true))
            assertTrue(OfflinePackAutoUpdatePreference.isEnabled(isolated))
            assertTrue(shouldRunPackRefresh(autoEnabled = true, manualRequest = false))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
