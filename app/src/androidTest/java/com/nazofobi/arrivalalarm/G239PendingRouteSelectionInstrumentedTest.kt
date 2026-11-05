package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G239PendingRouteSelectionInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun previousTimetableRouteCannotBeSelectedUntilNewSearchCompletes() {
        val busy = mutableStateOf(true)
        var selections = 0
        val origin = MapPoint(52.665, 8.237, "Lohne")
        val destination = MapPoint(52.700, 8.400, "Achim")
        val priorRoute = RouteOption(
            id = "old-departure",
            origin = origin,
            destination = destination,
            line = "RE 1",
            direction = "Achim",
            departure = "08:00",
            arrival = "08:30",
            walkingMinutes = 0,
            transfers = 0,
        )

        rule.setContent {
            MaterialTheme {
                RouteJourneyPanel(
                    origin = origin,
                    destination = destination,
                    routeOptions = listOf(priorRoute),
                    routeBusy = busy.value,
                    routeStatus = "Calculating a new service day",
                    routeOffline = false,
                    selectedRouteId = null,
                    journey = JourneyUiState(),
                    onRefreshRoutes = {},
                    onSelectRoute = { selections++ },
                    onArmAlarm = {},
                    onCancelAlarm = {},
                )
            }
        }

        // The old card may still be rendered while the future/arrival-by query runs,
        // but it must not be actionable or arm a route from the previous service day.
        rule.onNodeWithTag("route-option-select-0").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, selections); busy.value = false }
        rule.onNodeWithTag("route-option-select-0").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, selections) }
    }
}
