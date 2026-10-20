package com.nazofobi.arrivalalarm

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            realtime = RouteRealtimeInfo(state = RouteRealtimeState.FRESH_MATCHED),
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
        rule.onNodeWithTag("live-trip-remaining-stops").assertTextContains("1")
        rule.onNodeWithTag("live-trip-distance").assertTextContains("430", substring = true)
        rule.onNodeWithTag("live-trip-audio").performScrollTo().assertIsDisplayed()
    }
    @Test
    fun staleRealtimeDoesNotShowAnUnverifiedCurrentStop() {
        val route = RouteOption(
            id = "route-stale",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
            line = "RE 9",
            direction = "Bremen Hbf",
            departure = "08:00",
            arrival = "08:43",
            walkingMinutes = 0,
            transfers = 0,
            realtime = RouteRealtimeInfo(state = RouteRealtimeState.STALE),
            stops = listOf(
                RouteStop(
                    id = "a", name = "Lohne",
                    plannedDeparture = "2026-09-26T07:58:00+02:00",
                    departure = "2026-09-26T08:00:00+02:00",
                ),
                RouteStop(
                    id = "b", name = "Diepholz",
                    plannedArrival = "2026-09-26T08:13:00+02:00",
                    plannedDeparture = "2026-09-26T08:14:00+02:00",
                    arrival = "2026-09-26T08:15:00+02:00",
                    departure = "2026-09-26T08:16:00+02:00",
                ),
                RouteStop(
                    id = "c", name = "Bremen Hbf",
                    plannedArrival = "2026-09-26T08:43:00+02:00",
                    arrival = "2026-09-26T08:45:00+02:00",
                ),
            ),
        )
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LiveTripPanel(
                        selectedRoute = route,
                        journey = JourneyUiState(phase = JourneyPhase.ARMED),
                        guidanceState = GuidanceReliabilityState(),
                        routeOffline = false,
                        nowEpochSeconds =
                            "2026-09-26T08:15:30+02:00".toTransitEpochSecondsOrNull()!!,
                    )
                }
            }
        }

        rule.onNodeWithTag("live-trip-previous").assertTextContains("Diepholz", substring = true)
        rule.onNodeWithTag("live-trip-next").assertTextContains("Bremen Hbf", substring = true)
    }


    @Test
    fun scheduleBoundariesNeverClaimTheVehicleIsBetweenStops() {
        val route = RouteOption(
            id = "boundary-route",
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
                    id = "origin", name = "Lohne",
                    plannedDeparture = "2026-09-26T08:00:00+02:00",
                ),
                RouteStop(
                    id = "destination", name = "Bremen Hbf",
                    plannedArrival = "2026-09-26T08:45:00+02:00",
                ),
            ),
        )
        val now = mutableLongStateOf(
            "2026-09-26T07:50:00+02:00".toTransitEpochSecondsOrNull()!!
        )
        val unknownProgressText = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.live_trip_progress_unavailable)

        rule.setContent {
            MaterialTheme {
                LiveTripPanel(
                    selectedRoute = route,
                    journey = JourneyUiState(phase = JourneyPhase.ARMED),
                    guidanceState = GuidanceReliabilityState(),
                    routeOffline = false,
                    nowEpochSeconds = now.longValue,
                )
            }
        }

        // The first departure has not occurred: no previous stop, hence no "between" claim.
        rule.onNodeWithTag("live-trip-current")
            .assertTextContains(unknownProgressText, substring = true)
        rule.onNodeWithTag("live-trip-remaining-stops").assertTextContains("2")

        // Once the last scheduled arrival is in the past there is no next stop either.
        // Timetable evidence must not be misrepresented as the vehicle's GPS position.
        rule.runOnUiThread {
            now.longValue = "2026-09-26T09:00:00+02:00".toTransitEpochSecondsOrNull()!!
        }
        rule.waitForIdle()
        rule.onNodeWithTag("live-trip-current")
            .assertTextContains(unknownProgressText, substring = true)
        rule.onNodeWithTag("live-trip-remaining-stops").assertTextContains("0")

    }


    @Test
    fun malformedDestinationStopTimesAreNotPresentedAsValidArrivalClocks() {
        val candidateTime = mutableStateOf("2026-09-26T08:99:00+02:00")
        rule.setContent {
            MaterialTheme {
                val route = RouteOption(
                    id = "bad-target-time",
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
                            id = "destination",
                            name = "Bremen Hbf",
                            plannedArrival = candidateTime.value,
                        ),
                    ),
                )
                LiveTripPanel(
                    selectedRoute = route,
                    journey = JourneyUiState(phase = JourneyPhase.ARMED),
                    guidanceState = GuidanceReliabilityState(),
                    routeOffline = false,
                    nowEpochSeconds = 0L,
                )
            }
        }

        // A malformed provider minute must not be shown as an arrival ETA.
        rule.onNodeWithTag("live-trip-target-time").assertDoesNotExist()
        rule.onNodeWithTag("live-trip-remaining-stops").assertDoesNotExist()

        // Nor should an implausibly large service hour be treated as a clock.
        rule.runOnUiThread {
            candidateTime.value = "2026-09-26T99:30:00+02:00"
        }
        rule.waitForIdle()
        rule.onNodeWithTag("live-trip-target-time").assertDoesNotExist()

        // Retain valid scheduled destination times after the fail-closed cases.
        rule.runOnUiThread {
            candidateTime.value = "2026-09-26T09:30:00+02:00"
        }
        rule.waitForIdle()
        rule.onNodeWithTag("live-trip-target-time")
            .assertTextContains("09:30", substring = true)
    }

}
