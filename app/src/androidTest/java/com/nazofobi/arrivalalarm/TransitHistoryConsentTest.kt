package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitHistoryConsentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun oldImplicitHistoryIsPurgedAndNewTripsRequireExplicitLocalConsent() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
        val consentWasPresent = prefs.contains("recent_trip_history_enabled")
        val originalConsent = runCatching {
            prefs.getBoolean("recent_trip_history_enabled", false)
        }.getOrDefault(false)
        val oldHistory = runCatching {
            prefs.getString("recent_trip_ids", null)
        }.getOrNull()
        val favoriteBefore = runCatching {
            prefs.getStringSet("favorite_stop_ids", null)?.toSet()
        }.getOrNull()

        try {
            prefs.edit().putBoolean("recent_trip_history_enabled", false)
                .putString("recent_trip_ids", "old-unconsented-trip").commit()
            rule.setContent {
                MaterialTheme {
                    TransitExperiencePanel(
                        stopId = "consent-stop",
                        stopName = "Lohne",
                        lineName = "RE 18",
                        direction = "Osnabrück",
                        departures = listOf(Departure("new-trip", "RE 18", "Osnabrück", 2_000L)),
                        alerts = emptyList(),
                        isOfflineCache = false,
                        nowEpochSeconds = 1_000L,
                    )
                }
            }
            rule.onNodeWithTag("recent-trips-consent-toggle").assertExists()
            rule.onNodeWithTag("recent-trips-clear").assertDoesNotExist()
            rule.onNodeWithTag("departure-0").performClick()
            rule.runOnIdle {
                assertEquals("", prefs.getString("recent_trip_ids", ""))
                assertEquals(false, prefs.getBoolean("recent_trip_history_enabled", true))
            }

            rule.onNodeWithTag("recent-trips-consent-toggle").performClick()
            rule.onNodeWithTag("departure-0").performClick()
            rule.runOnIdle {
                assertEquals(true, prefs.getBoolean("recent_trip_history_enabled", false))
                assertEquals("new-trip", prefs.getString("recent_trip_ids", ""))
            }

            rule.onNodeWithTag("recent-trips-consent-toggle").performClick()
            rule.onNodeWithTag("recent-trips-clear").assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(false, prefs.getBoolean("recent_trip_history_enabled", true))
                assertEquals("", prefs.getString("recent_trip_ids", ""))
                assertEquals(favoriteBefore, prefs.getStringSet("favorite_stop_ids", null)?.toSet())
            }
        } finally {
            val restore = prefs.edit().remove("recent_trip_history_enabled")
                .remove("recent_trip_ids").remove("favorite_stop_ids")
            if (consentWasPresent) restore.putBoolean("recent_trip_history_enabled", originalConsent)
            if (oldHistory != null) restore.putString("recent_trip_ids", oldHistory)
            if (favoriteBefore != null) restore.putStringSet("favorite_stop_ids", favoriteBefore)
            restore.commit()
        }
    }
}
