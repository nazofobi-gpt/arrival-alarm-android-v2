package com.nazofobi.arrivalalarm

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTripPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun livePanelSeparatesProviderStopTimingFromRealGpsDistance() {
        val route = RouteOption(
            id = "route-1",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
            line = "RE 9",
            direction = "Bremen Hbf",
            departure = "08:00",
            arrival = "08:45",
            walkingMinutes = 0,
            transfers = 0,
            stops = listOf(
                RouteStop(
                    id = "a",
                    name = "Lohne(Oldb)",
                    departure = "2026-09-26T08:00:00+02:00",
                    plannedDeparture = "2026-09-26T07:58:00+02:00",
                ),
                RouteStop(
                    id = "b",
                    name = "Diepholz",
                    arrival = "2026-09-26T08:15:00+02:00",
                    departure = "2026-09-26T08:16:00+02:00",
                    plannedArrival = "2026-09-26T08:13:00+02:00",
                    plannedDeparture = "2026-09-26T08:14:00+02:00",
                ),
                RouteStop(
                    id = "c",
                    name = "Bremen Hbf",
                    arrival = "2026-09-26T08:45:00+02:00",
                    plannedArrival = "2026-09-26T08:43:00+02:00",
                ),
            ),
        )

        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LiveTripPanel(
                    selectedRoute = route,
                    journey = JourneyUiState(
                        phase = JourneyPhase.ARMED,
                        start = GeoPoint(52.665, 8.237),
                        destination = GeoPoint(53.083, 8.813),
                        distanceMeters = 430.0,
                    ),
                    guidanceState = GuidanceReliabilityState(
                        permissionState = GuidancePermissionState.GRANTED,
                        audioRoute = GuidanceAudioRoute.BLUETOOTH_CONNECTED,
                    ),
                    routeOffline = false,
                    nowEpochSeconds = "2026-09-26T08:15:30+02:00".toTransitEpochSecondsOrNull()!!,
                    )
                }
            }
        }

        rule.onNodeWithTag("live-trip-panel").assertIsDisplayed()
        rule.onNodeWithTag("live-trip-current").assertTextContains("Diepholz", substring = true)
        rule.onNodeWithTag("live-trip-next").assertTextContains("Bremen Hbf", substring = true)
        rule.onNodeWithTag("live-trip-distance").assertTextContains("430", substring = true)
        rule.onNodeWithTag("live-trip-audio").performScrollTo().assertIsDisplayed()
    }
}
