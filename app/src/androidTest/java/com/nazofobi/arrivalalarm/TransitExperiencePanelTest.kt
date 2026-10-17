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
class TransitExperiencePanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun departureSelectionPublishesCanonicalRouteId() {
        var selectedRouteId: String? = null

        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "stop-bremen",
                    stopName = "Bremen Hbf",
                    lineName = "RE 9",
                    direction = "Bremen Hbf",
                    departures = listOf(
                        Departure(
                            tripId = "trip-re9",
                            line = "RE 9",
                            direction = "Bremen Hbf",
                            scheduledEpochSeconds = 2_000L,
                            routeId = "route-re9",
                        )
                    ),
                    alerts = emptyList(),
                    isOfflineCache = false,
                    nowEpochSeconds = 1_000L,
                    onSelectJourney = { selectedRouteId = it },
                )
            }
        }

        rule.onNodeWithTag("departure-0").performClick()
        rule.runOnIdle {
            assertEquals("route-re9", selectedRouteId)
        }
    }
    @Test
    fun clearRecentTripsRemovesOnlyTripHistoryAndUpdatesPanel() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
        val previousFavorites = prefs.getStringSet("favorite_stop_ids", emptySet()).orEmpty().toSet()
        prefs.edit().remove("recent_trip_ids").commit()

        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "stop-test-history",
                    stopName = "Lohne",
                    lineName = "RE 18",
                    direction = "Osnabrück",
                    departures = listOf(
                        Departure("trip-history", "RE 18", "Osnabrück", 2_000L),
                    ),
                    alerts = emptyList(),
                    isOfflineCache = false,
                    nowEpochSeconds = 1_000L,
                )
            }
        }

        rule.onNodeWithTag("recent-trips-clear").assertDoesNotExist()
        rule.onNodeWithTag("departure-0").performClick()
        rule.onNodeWithTag("recent-trips-clear").assertExists().performClick()
        rule.onNodeWithTag("recent-trips-clear").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals("", prefs.getString("recent_trip_ids", ""))
            assertEquals(previousFavorites, prefs.getStringSet("favorite_stop_ids", emptySet()).orEmpty().toSet())
        }
    }

}
