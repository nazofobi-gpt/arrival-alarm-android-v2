package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteStopDeviationUiTest {
    @get:Rule val rule = createComposeRule()

    private fun render(
        state: RouteRealtimeState,
        offline: Boolean = false,
        cancelled: Boolean = false,
    ) {
        val origin = MapPoint(52.665, 8.237, "Lohne")
        val destination = MapPoint(53.083, 8.813, "Bremen Hbf")
        val route = RouteOption(
            id = "verified-stop-delta",
            origin = origin,
            destination = destination,
            line = "RE 9",
            direction = "Bremen Hbf",
            departure = "2026-11-02T18:05:00Z",
            arrival = "2026-11-02T18:57:00Z",
            walkingMinutes = 0,
            transfers = 0,
            realtime = RouteRealtimeInfo(
                state = state,
                cancelledTripIds = if (cancelled) listOf("trip-cancelled") else emptyList(),
            ),
            stops = listOf(
                RouteStop(
                    id = "a", name = "Lohne",
                    departure = "2026-11-02T18:05:00Z",
                    plannedDeparture = "2026-11-02T18:00:00Z",
                ),
                RouteStop(
                    id = "b", name = "Bremen Hbf",
                    arrival = "2026-11-02T18:57:00Z",
                    plannedArrival = "2026-11-02T19:00:00Z",
                ),
            ),
        )
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RouteJourneyPanel(
                        origin = origin,
                        destination = destination,
                        routeOptions = listOf(route),
                        routeBusy = false,
                        routeStatus = null,
                        routeOffline = offline,
                        selectedRouteId = route.id,
                        journey = JourneyUiState(phase = JourneyPhase.DESTINATION_SELECTED),
                        onRefreshRoutes = {},
                        onSelectRoute = {},
                        onArmAlarm = {},
                        onCancelAlarm = {},
                        mode = RouteJourneyMode.DETAIL,
                    )
                }
            }
        }
    }

    @Test fun freshMatchedStopShowsDelayAndEarlyMinutesInJourneyDetail() {
        render(RouteRealtimeState.FRESH_MATCHED)
        rule.onNodeWithTag("journey-stop-deviation-0")
            .performScrollTo().assertTextContains("5", substring = true)
        rule.onNodeWithTag("journey-stop-deviation-1")
            .performScrollTo().assertTextContains("3", substring = true)
    }

    private fun assertNoStopDeviation() {
        rule.onNodeWithTag("journey-stop-deviation-0").assertDoesNotExist()
        rule.onNodeWithTag("journey-stop-deviation-1").assertDoesNotExist()
    }

    @Test fun staleRouteNeverShowsStopDeviation() {
        render(RouteRealtimeState.STALE)
        assertNoStopDeviation()
    }

    @Test fun offlineRouteNeverShowsStopDeviation() {
        render(RouteRealtimeState.FRESH_MATCHED, offline = true)
        assertNoStopDeviation()
    }

    @Test fun cancelledRouteNeverShowsStopDeviation() {
        render(RouteRealtimeState.FRESH_MATCHED, cancelled = true)
        assertNoStopDeviation()
    }
}
