package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteShareUiInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val option = RouteOption(
        id = "selected",
        origin = MapPoint(52.66, 8.23, "Lohne"),
        destination = MapPoint(53.08, 8.80, "Bremen"),
        line = "RE 9",
        direction = "Bremen",
        departure = "10:00",
        arrival = "11:00",
        walkingMinutes = 0,
        transfers = 0,
        stops = emptyList(),
    )

    private fun render(selectedId: String?) {
        rule.setContent {
            MaterialTheme {
                RouteJourneyPanel(
                    origin = option.origin,
                    destination = option.destination,
                    routeOptions = listOf(option),
                    routeBusy = false,
                    routeStatus = null,
                    routeOffline = false,
                    selectedRouteId = selectedId,
                    journey = JourneyUiState(JourneyPhase.EMPTY, null, null, null),
                    onRefreshRoutes = {},
                    onSelectRoute = {},
                    onArmAlarm = {},
                    onCancelAlarm = {},
                    mode = RouteJourneyMode.DETAIL,
                )
            }
        }
    }

    @Test fun shareIsAvailableOnlyForActualSelectedRoute() {
        render(option.id)
        rule.onNodeWithTag("journey-share-route").assertExists()
    }

    @Test fun noSelectionHasNoShareSideEffect() {
        render(null)
        rule.onNodeWithTag("journey-share-route").assertDoesNotExist()
    }
}
