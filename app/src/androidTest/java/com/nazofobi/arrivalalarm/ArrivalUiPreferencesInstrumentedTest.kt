package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device regression for the optional fewer-transfers ordering preference. */
@RunWith(AndroidJUnit4::class)
class ArrivalUiPreferencesInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fewerTransfersPreferenceSurvivesNewAdapterWithoutChangingDirectOnly() {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        withRestoredPreferenceKeys(
            prefs,
            ArrivalUiPreferences.KEY_FEWER_TRANSFERS_FIRST,
            ArrivalUiPreferences.KEY_DIRECT_ONLY_ROUTES,
        ) {
            prefs.edit()
                .remove(ArrivalUiPreferences.KEY_FEWER_TRANSFERS_FIRST)
                .remove(ArrivalUiPreferences.KEY_DIRECT_ONLY_ROUTES)
                .commit()

            val first = ArrivalUiPreferences(context)
            assertFalse(first.loadFewerTransfersFirst())
            assertFalse(first.loadDirectOnlyRoutes())

            first.saveDirectOnlyRoutes(true)
            first.saveFewerTransfersFirst(true)

            val restored = ArrivalUiPreferences(context)
            assertTrue(restored.loadDirectOnlyRoutes())
            assertTrue(restored.loadFewerTransfersFirst())

            restored.saveFewerTransfersFirst(false)
            val afterToggle = ArrivalUiPreferences(context)
            assertFalse(afterToggle.loadFewerTransfersFirst())
            assertTrue(afterToggle.loadDirectOnlyRoutes())
        }
    }

    @Test
    fun wrongStoredPreferenceTypeFailsClosedWithoutCrashing() {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        withRestoredPreferenceKeys(prefs, ArrivalUiPreferences.KEY_FEWER_TRANSFERS_FIRST) {
            prefs.edit().putString(ArrivalUiPreferences.KEY_FEWER_TRANSFERS_FIRST, "bad-type").commit()

            assertFalse(ArrivalUiPreferences(context).loadFewerTransfersFirst())
        }
    }

    @Test
    fun atMostOneTransferPreferencePersistsAndMalformedTypeFailsClosed() {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        withRestoredPreferenceKeys(prefs, ArrivalUiPreferences.KEY_AT_MOST_ONE_TRANSFER) {
            prefs.edit().remove(ArrivalUiPreferences.KEY_AT_MOST_ONE_TRANSFER).commit()
            assertFalse(ArrivalUiPreferences(context).loadAtMostOneTransfer())

            ArrivalUiPreferences(context).saveAtMostOneTransfer(true)
            assertTrue(ArrivalUiPreferences(context).loadAtMostOneTransfer())

            prefs.edit().putString(ArrivalUiPreferences.KEY_AT_MOST_ONE_TRANSFER, "invalid").commit()
            assertFalse(ArrivalUiPreferences(context).loadAtMostOneTransfer())
        }
    }

    private inline fun withRestoredPreferenceKeys(
        prefs: SharedPreferences,
        vararg keys: String,
        block: () -> Unit,
    ) {
        val originals = keys.associateWith { prefs.all[it] }
        try {
            block()
        } finally {
            val edit = prefs.edit()
            originals.forEach { (key, original) ->
                when (original) {
                    null -> edit.remove(key)
                    is Boolean -> edit.putBoolean(key, original)
                    is String -> edit.putString(key, original)
                    is Int -> edit.putInt(key, original)
                    is Long -> edit.putLong(key, original)
                    is Float -> edit.putFloat(key, original)
                    else -> error("Unexpected original preference type for $key")
                }
            }
            edit.commit()
        }
    }
}
