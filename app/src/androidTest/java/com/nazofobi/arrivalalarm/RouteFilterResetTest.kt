package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
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
class RouteFilterResetTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun noRouteWithSavedRestrictionsOffersSingleResetAndReplans() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val keys = listOf(
            ArrivalUiPreferences.KEY_DIRECT_ONLY_ROUTES,
            ArrivalUiPreferences.KEY_AT_MOST_ONE_TRANSFER,
            ArrivalUiPreferences.KEY_MAX_WALKING_15_MINUTES,
            ArrivalUiPreferences.KEY_FEWER_TRANSFERS_FIRST,
            ArrivalUiPreferences.KEY_SHORTER_WALKING_FIRST,
        )
        val original = keys.associateWith { prefs.all[it] }
        try {
            val editor = prefs.edit()
            keys.forEach { editor.putBoolean(it, true) }
            check(editor.commit())
            var refreshes = 0
            rule.setContent {
                MaterialTheme {
                    RouteJourneyPanel(
                        origin = MapPoint(52.665, 8.237, "Lohne"),
                        destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
                        routeOptions = emptyList(),
                        routeBusy = false,
                        routeStatus = null,
                        routeOffline = false,
                        selectedRouteId = null,
                        journey = JourneyUiState(),
                        onRefreshRoutes = { refreshes++ },
                        onSelectRoute = {},
                        onArmAlarm = {},
                        onCancelAlarm = {},
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
            rule.onNodeWithTag("route-filter-reset").performScrollTo().assertExists().performClick()
            rule.onNodeWithTag("route-filter-reset").assertDoesNotExist()
            rule.onNodeWithTag("route-direct-only-filter").assertIsNotSelected()
            rule.onNodeWithTag("route-at-most-one-transfer-filter").assertIsNotSelected()
            rule.onNodeWithTag("route-max-walking-15-filter").assertIsNotSelected()
            rule.runOnIdle {
                assertEquals(1, refreshes)
                keys.forEach { key -> assertEquals(false, prefs.getBoolean(key, true)) }
            }
        } finally {
            val editor = prefs.edit()
            original.forEach { (key, value) ->
                if (value is Boolean) editor.putBoolean(key, value) else editor.remove(key)
            }
            editor.commit()
        }
    }

    @Test
    fun noRouteWithoutRestrictiveFiltersDoesNotOfferMisleadingReset() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val keys = listOf(
            ArrivalUiPreferences.KEY_DIRECT_ONLY_ROUTES,
            ArrivalUiPreferences.KEY_AT_MOST_ONE_TRANSFER,
            ArrivalUiPreferences.KEY_MAX_WALKING_15_MINUTES,
        )
        val original = keys.associateWith { prefs.all[it] }
        try {
            val editor = prefs.edit()
            keys.forEach { editor.putBoolean(it, false) }
            check(editor.commit())
            rule.setContent {
                MaterialTheme {
                    RouteJourneyPanel(
                        origin = MapPoint(52.665, 8.237, "Lohne"),
                        destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
                        routeOptions = emptyList(),
                        routeBusy = false,
                        routeStatus = null,
                        routeOffline = false,
                        selectedRouteId = null,
                        journey = JourneyUiState(),
                        onRefreshRoutes = {},
                        onSelectRoute = {},
                        onArmAlarm = {},
                        onCancelAlarm = {},
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
            rule.onNodeWithTag("route-filter-reset").assertDoesNotExist()
            rule.onNodeWithTag("route-results-empty").assertExists()
        } finally {
            val editor = prefs.edit()
            original.forEach { (key, value) ->
                if (value is Boolean) editor.putBoolean(key, value) else editor.remove(key)
            }
            editor.commit()
        }
    }
}
