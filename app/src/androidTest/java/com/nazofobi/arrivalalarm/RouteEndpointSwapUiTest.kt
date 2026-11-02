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
class RouteEndpointSwapUiTest {
    @get:Rule val rule = createComposeRule()

    private fun showSearch(
        origin: MapPoint?,
        destination: MapPoint?,
        canSwap: Boolean,
        onSwap: () -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                HomeSearchPanel(
                    selectingOrigin = false,
                    query = "",
                    searchBusy = false,
                    searchResults = emptyList(),
                    searchSource = null,
                    searchMessage = null,
                    locationMessage = null,
                    origin = origin,
                    destination = destination,
                    mapMessage = null,
                    nearby = emptyList(),
                    nearbySource = null,
                    nearbyMessage = null,
                    recentSearches = emptyList(),
                    favoriteStopIds = emptySet(),
                    onSelectOriginTarget = {},
                    onSelectDestinationTarget = {},
                    canSwapEndpoints = canSwap,
                    onSwapEndpoints = onSwap,
                    onQueryChange = {},
                    onSearch = {},
                    onSearchResultSelected = {},
                    onRecentSearchSelected = {},
                    onClearRecentSearches = {},
                    onUseCurrentLocation = {},
                    onMapStopSelected = {},
                    onMapPointSelected = {},
                    onMapError = {},
                    onNearbyStopSelected = {},
                    mode = HomeSearchMode.SEARCH,
                )
            }
        }
    }

    @Test
    fun completeUnarmedRouteHasAccessibleOneTapSwapAction() {
        var requests = 0
        showSearch(
            MapPoint(52.665, 8.237, "Lohne"),
            MapPoint(53.083, 8.813, "Bremen Hbf"),
            canSwap = true,
            onSwap = { requests++ },
        )
        rule.onNodeWithTag("swap-route-endpoints").assertExists().performClick()
        rule.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun missingEndpointNeverShowsSwapAction() {
        showSearch(MapPoint(52.665, 8.237, "Lohne"), null, canSwap = true)
        rule.onNodeWithTag("swap-route-endpoints").assertDoesNotExist()
    }

    @Test
    fun armedRouteCannotOfferSwapAction() {
        showSearch(
            MapPoint(52.665, 8.237, "Lohne"),
            MapPoint(53.083, 8.813, "Bremen Hbf"),
            canSwap = false,
        )
        rule.onNodeWithTag("swap-route-endpoints").assertDoesNotExist()
    }
}
