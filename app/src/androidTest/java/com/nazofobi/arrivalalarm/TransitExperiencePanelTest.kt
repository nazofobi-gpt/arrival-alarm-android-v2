package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
