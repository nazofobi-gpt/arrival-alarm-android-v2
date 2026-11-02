package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun pinningLineStoresOnlyLocalStopScopedRouteBookmark() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
        val stopId = "pin-regression-stop-20261028"
        val routeId = "pin-regression-route-20261028"
        val key = pinnedStopLineKey(stopId, routeId, "gtfs-full")!!
        prefs.edit().remove("pinned_stop_line_keys_v1").commit()
        try {
            rule.setContent {
                MaterialTheme {
                    TransitExperiencePanel(
                        stopId = stopId,
                        stopProviderId = "gtfs-full",
                        stopName = "Test stop",
                        lineName = "RE 9",
                        direction = "Bremen",
                        departures = listOf(
                            Departure("pin-trip", "RE 9", "Bremen", 2_000L, routeId = routeId),
                        ),
                        alerts = emptyList(),
                        isOfflineCache = false,
                        nowEpochSeconds = 1_000L,
                    )
                }
            }
            rule.onNodeWithTag("pin-departure-0").performClick()
            rule.onNodeWithTag("pinned-departure-0").assertExists()
            rule.runOnIdle {
                assertEquals(setOf(key), prefs.getStringSet("pinned_stop_line_keys_v1", emptySet()))
                // No alert subscription or trip-history consent is created by pinning.
                assertEquals(null, prefs.getString("pin_alert_subscription", null))
            }
            rule.onNodeWithTag("pin-departure-0").performClick()
            rule.runOnIdle {
                assertEquals(emptySet<String>(), prefs.getStringSet("pinned_stop_line_keys_v1", emptySet()))
            }
        } finally {
            prefs.edit().remove("pinned_stop_line_keys_v1").commit()
        }
    }

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
    fun concreteFrequencyAlternativeSelectsExactScheduledInstance() {
        var selectedEpoch: Long? = null
        var legacyRouteId: String? = null
        val first = Departure(
            tripId = "frequency-template", line = "RE 9", direction = "Bremen",
            scheduledEpochSeconds = 1_200L, routeId = "route-re9",
            realtimeTripStartDate = "20261019", realtimeTripStartTime = "08:00:00",
        )
        val next = first.copy(
            scheduledEpochSeconds = 1_500L, realtimeTripStartTime = "08:05:00",
        )
        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "stop-bremen-alt", stopName = "Bremen Hbf",
                    lineName = "RE 9", direction = "Bremen",
                    departures = listOf(first, next),
                    alerts = emptyList(), isOfflineCache = false, nowEpochSeconds = 1_000L,
                    onSelectJourney = { legacyRouteId = it },
                    onSelectDeparture = { selectedEpoch = it.scheduledEpochSeconds },
                )
            }
        }
        rule.onNodeWithTag("departure-0").performClick()
        rule.onNodeWithTag("alternate-departure-0").performClick()
        rule.runOnIdle {
            assertEquals(1_500L, selectedEpoch)
            assertEquals(null, legacyRouteId)
        }
    }

    @Test
    fun clearRecentTripsRemovesOnlyTripHistoryAndUpdatesPanel() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
        val previousFavorites = prefs.getStringSet("favorite_stop_ids", emptySet()).orEmpty().toSet()
        val previouslyEnabled = prefs.getBoolean("recent_trip_history_enabled", false)
        val hadConsentFlag = prefs.contains("recent_trip_history_enabled")
        prefs.edit().putBoolean("recent_trip_history_enabled", false)
            .remove("recent_trip_ids").commit()

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
        rule.onNodeWithTag("recent-trips-consent-toggle").performClick()
        rule.onNodeWithTag("departure-0").performClick()
        rule.onNodeWithTag("recent-trips-clear").assertExists().performClick()
        rule.onNodeWithTag("recent-trips-clear").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals("", prefs.getString("recent_trip_ids", ""))
            assertEquals(previousFavorites, prefs.getStringSet("favorite_stop_ids", emptySet()).orEmpty().toSet())
            val editor = prefs.edit()
            if (hadConsentFlag) editor.putBoolean("recent_trip_history_enabled", previouslyEnabled)
            else editor.remove("recent_trip_history_enabled")
            editor.commit()
        }
    }


    @Test
    fun malformedLocalFavoritesAndTripHistoryDoNotCrashDepartureBoard() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
        val originalFavorites = prefs.getStringSet("favorite_stop_ids", null)?.toSet()
        val originalRecents = prefs.getString("recent_trip_ids", null)
        try {
            // Both preference types can be wrong after a migration or foreign write.
            prefs.edit()
                .putString("favorite_stop_ids", "not-a-string-set")
                .putInt("recent_trip_ids", 42)
                .commit()

            rule.setContent {
                MaterialTheme {
                    TransitExperiencePanel(
                        stopId = "stop-corrupt-preferences",
                        stopName = "Lohne",
                        lineName = "RE 18",
                        direction = "Osnabrück",
                        departures = emptyList(),
                        alerts = emptyList(),
                        isOfflineCache = false,
                        nowEpochSeconds = 1_000L,
                    )
                }
            }

            rule.onNodeWithTag("stop-detail").assertExists()
            rule.onNodeWithTag("favorite-stop").performClick()
            rule.runOnIdle {
                // The next legitimate change repairs both wrong-typed fields.
                assertEquals(
                    setOf("stop-corrupt-preferences"),
                    prefs.getStringSet("favorite_stop_ids", emptySet()).orEmpty(),
                )
                assertEquals("", prefs.getString("recent_trip_ids", ""))
            }
        } finally {
            val editor = prefs.edit()
                .remove("favorite_stop_ids")
                .remove("recent_trip_ids")
            originalFavorites?.let { editor.putStringSet("favorite_stop_ids", it) }
            originalRecents?.let { editor.putString("recent_trip_ids", it) }
            editor.commit()
        }
    }

    @Test
    fun departureBoardShowsSourceBackedDelayBesideScheduledTime() {
        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "re9-delay-test",
                    stopName = "Lohne",
                    lineName = "RE9",
                    direction = "Bremen",
                    departures = listOf(
                        Departure("re9-trip", "RE9", "Bremen", 1_500L,
                            realtimeEpochSeconds = 1_800L),
                    ),
                    alerts = emptyList(),
                    isOfflineCache = false,
                    nowEpochSeconds = 1_000L,
                    providerCapabilities = TransitProviderCapabilities(realtimeDepartures = true),
                )
            }
        }
        rule.onNodeWithTag("departure-0").assertExists()
        rule.onNodeWithTag("departure-delay-0").assertTextContains("5", substring = true)
    }

    @Test
    fun laterDeparturesRequireExplicitShowMoreAndCanBeCollapsed() {
        val candidates = (0 until 12).map { index ->
            Departure(
                tripId = "re9-later-$index",
                line = "RE9",
                direction = "Bremen",
                scheduledEpochSeconds = 2_000L + index * 60L,
                routeId = "re9-route",
            )
        }
        var selectedTrip: String? = null
        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "test-more-departures",
                    stopName = "Lohne",
                    lineName = "RE9",
                    direction = "Bremen",
                    departures = candidates,
                    alerts = emptyList(),
                    isOfflineCache = false,
                    nowEpochSeconds = 1_000L,
                    onSelectDeparture = { selectedTrip = it.tripId },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        rule.onNodeWithTag("departure-7").assertExists()
        rule.onNodeWithTag("departure-8").assertDoesNotExist()
        rule.onNodeWithTag("departures-show-more-toggle").performClick()
        rule.onNodeWithTag("departure-8").assertExists()
        rule.onNodeWithTag("departure-11").assertExists().performScrollTo().performClick()
        rule.runOnIdle { assertEquals("re9-later-11", selectedTrip) }
        rule.onNodeWithTag("departures-show-more-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("departure-8").assertDoesNotExist()
        rule.runOnIdle { assertEquals("re9-later-11", selectedTrip) }
    }

    @Test
    fun shortDepartureBoardHasNoExpansionControl() {
        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "short-board-test",
                    stopName = "Lohne",
                    lineName = "RE9",
                    direction = "Bremen",
                    departures = (0 until 8).map { index ->
                        Departure("short-$index", "RE9", "Bremen", 2_000L + index * 60L)
                    },
                    alerts = emptyList(),
                    isOfflineCache = false,
                    nowEpochSeconds = 1_000L,
                )
            }
        }
        rule.onNodeWithTag("departures-show-more-toggle").assertDoesNotExist()
        rule.onNodeWithTag("departure-7").assertExists()
    }


    @Test
    fun lineAndDirectionFilterChangesCardsOnlyUntilUserSelectsTrip() {
        val candidates = (0 until 12).map { index ->
            val group = index % 3
            Departure(
                tripId = "filtered-trip-$index",
                line = if (group == 2) "RE18" else "RE9",
                direction = if (group == 1) "Osnabrück" else "Bremen",
                scheduledEpochSeconds = 2_000L + index * 60L,
                routeId = "route-$index",
            )
        }
        var selected: String? = null
        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "group-filter-test",
                    stopName = "Lohne",
                    lineName = "",
                    direction = "",
                    departures = candidates,
                    alerts = emptyList(),
                    isOfflineCache = false,
                    nowEpochSeconds = 1_000L,
                    onSelectDeparture = { selected = it.tripId },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        rule.onNodeWithTag("departures-show-more-toggle").assertExists()
        rule.onNodeWithTag("departures-filter-all").assertExists()
        rule.onNodeWithTag("departures-filter-1").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(null, selected) }
        rule.onNodeWithTag("departures-show-more-toggle").assertDoesNotExist()
        rule.onNodeWithTag("departure-3").assertExists()
        rule.onNodeWithTag("departure-4").assertDoesNotExist()
        rule.onNodeWithTag("departure-0").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("filtered-trip-1", selected) }
        rule.onNodeWithTag("departures-filter-all").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("filtered-trip-1", selected) }
        rule.onNodeWithTag("departures-show-more-toggle").assertExists()
        rule.onNodeWithTag("departure-7").assertExists()
    }

    @Test
    fun singleDepartureGroupCannotShowMisleadingFilter() {
        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "single-group-test",
                    stopName = "Lohne",
                    lineName = "RE9",
                    direction = "Bremen",
                    departures = listOf(
                        Departure("first", "RE9", "Bremen", 2_000L),
                        Departure("second", "RE9", "Bremen", 2_060L),
                    ),
                    alerts = emptyList(),
                    isOfflineCache = false,
                    nowEpochSeconds = 1_000L,
                )
            }
        }
        rule.onNodeWithTag("departures-filter-all").assertDoesNotExist()
        rule.onNodeWithTag("departure-1").assertExists()
    }

    @Test
    fun offlineDepartureBoardHidesUnverifiedDelay() {
        rule.setContent {
            MaterialTheme {
                TransitExperiencePanel(
                    stopId = "re9-offline-delay-test",
                    stopName = "Lohne",
                    lineName = "RE9",
                    direction = "Bremen",
                    departures = listOf(
                        Departure("re9-trip-offline", "RE9", "Bremen", 1_500L,
                            realtimeEpochSeconds = 1_800L),
                    ),
                    alerts = emptyList(),
                    isOfflineCache = true,
                    nowEpochSeconds = 1_000L,
                    providerCapabilities = TransitProviderCapabilities(realtimeDepartures = true),
                )
            }
        }
        rule.onNodeWithTag("departure-0").assertExists()
        rule.onNodeWithTag("departure-delay-0").assertDoesNotExist()
    }

}
