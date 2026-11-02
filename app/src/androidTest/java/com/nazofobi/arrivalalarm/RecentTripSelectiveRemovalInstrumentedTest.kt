package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentTripSelectiveRemovalInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun staleTripCannotReopenWhileRemovingItPreservesCurrentTripAndFavorites() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
        val hadConsent = prefs.contains("recent_trip_history_enabled")
        val originalConsent = runCatching {
            prefs.getBoolean("recent_trip_history_enabled", false)
        }.getOrDefault(false)
        val originalRecents = runCatching {
            prefs.getString("recent_trip_ids", null)
        }.getOrNull()
        val originalFavorites = runCatching {
            prefs.getStringSet("favorite_stop_ids", null)?.toSet()
        }.getOrNull()

        try {
            prefs.edit()
                .putBoolean("recent_trip_history_enabled", true)
                .putString("recent_trip_ids", "stale-trip\u001Fcurrent-trip")
                .putStringSet("favorite_stop_ids", setOf("favorite-stop"))
                .commit()
            var selectedTrip: String? = null
            rule.setContent {
                MaterialTheme {
                    TransitExperiencePanel(
                        stopId = "consent-stop",
                        stopName = "Lohne",
                        lineName = "RE 18",
                        direction = "Bremen",
                        departures = listOf(
                            Departure(
                                tripId = "current-trip",
                                line = "RE 18",
                                direction = "Bremen",
                                scheduledEpochSeconds = 1_200L,
                            ),
                        ),
                        alerts = emptyList(),
                        isOfflineCache = false,
                        nowEpochSeconds = 1_000L,
                        onSelectDeparture = { selectedTrip = it.tripId },
                    )
                }
            }
            rule.onNodeWithTag("recent-trip-open-0").assertIsNotEnabled()
            rule.onNodeWithTag("recent-trip-open-1").assertIsEnabled()
            rule.onNodeWithTag("recent-trip-remove-0").performClick()
            rule.runOnIdle {
                assertEquals("current-trip", prefs.getString("recent_trip_ids", null))
                assertEquals(setOf("favorite-stop"), prefs.getStringSet("favorite_stop_ids", null))
                assertEquals(null, selectedTrip)
            }
            rule.onNodeWithTag("recent-trip-open-0").performClick()
            rule.runOnIdle { assertEquals("current-trip", selectedTrip) }
            rule.onNodeWithTag("recent-trip-remove-0").performClick()
            rule.onNodeWithTag("recent-trip-0").assertDoesNotExist()
            rule.runOnIdle {
                assertEquals("", prefs.getString("recent_trip_ids", null))
                assertTrue(prefs.getBoolean("recent_trip_history_enabled", false))
                assertEquals(setOf("favorite-stop"), prefs.getStringSet("favorite_stop_ids", null))
            }
        } finally {
            val editor = prefs.edit().remove("recent_trip_history_enabled")
                .remove("recent_trip_ids").remove("favorite_stop_ids")
            if (hadConsent) editor.putBoolean("recent_trip_history_enabled", originalConsent)
            if (originalRecents != null) editor.putString("recent_trip_ids", originalRecents)
            if (originalFavorites != null) editor.putStringSet("favorite_stop_ids", originalFavorites)
            editor.commit()
        }
    }
}
