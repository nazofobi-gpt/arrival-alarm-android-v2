package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSearchFavoriteActionsInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun nearbyStopCanBeFavoritedWithoutSelectingIt() {
        val stop = CatalogStop(
            id = "lohne-station",
            providerId = "gtfs",
            name = "Lohne Bahnhof",
            latitude = 52.665,
            longitude = 8.237,
        )
        var toggledFavoriteId: String? = null
        var selectedNearby: NearbyStop? = null

        showPanel(
            mode = HomeSearchMode.HOME,
            nearby = listOf(NearbyStop(stop, distanceMeters = 250)),
            onToggleFavoriteStop = { toggledFavoriteId = it },
            onNearbyStopSelected = { selectedNearby = it },
        )

        rule.onNodeWithTag("nearby-stop-0-favorite")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.runOnIdle {
            assertEquals(stop.id, toggledFavoriteId)
            assertEquals(null, selectedNearby)
        }
    }

    @Test
    fun searchStopCanBeFavoritedWithoutSelectingIt() {
        val stop = CatalogStop(
            id = "lohne-station",
            providerId = "gtfs",
            name = "Lohne Bahnhof",
            latitude = 52.665,
            longitude = 8.237,
        )
        val result = TransitLocationResult(
            key = "stop:lohne-station",
            kind = TransitLocationKind.STOP,
            label = "Lohne Bahnhof",
            latitude = stop.latitude,
            longitude = stop.longitude,
            stop = stop,
        )
        var toggledFavoriteId: String? = null
        var selectedResult: TransitLocationResult? = null

        showPanel(
            mode = HomeSearchMode.SEARCH,
            searchResults = listOf(result),
            onToggleFavoriteStop = { toggledFavoriteId = it },
            onSearchResultSelected = { selectedResult = it },
        )

        rule.onNodeWithTag("search-result-0-favorite")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.runOnIdle {
            assertEquals(stop.id, toggledFavoriteId)
            assertEquals(null, selectedResult)
        }
    }

    private fun showPanel(
        mode: HomeSearchMode,
        nearby: List<NearbyStop> = emptyList(),
        searchResults: List<TransitLocationResult> = emptyList(),
        onToggleFavoriteStop: (String) -> Unit,
        onNearbyStopSelected: (NearbyStop) -> Unit = {},
        onSearchResultSelected: (TransitLocationResult) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeSearchPanel(
                        selectingOrigin = true,
                        query = "Lo",
                        searchBusy = false,
                        searchResults = searchResults,
                        searchSource = "GTFS",
                        searchMessage = null,
                        locationMessage = null,
                        origin = null,
                        destination = null,
                        mapMessage = null,
                        nearby = nearby,
                        nearbySource = "GTFS local",
                        nearbyMessage = null,
                        recentSearches = emptyList(),
                        favoriteStopIds = emptySet(),
                        onToggleFavoriteStop = onToggleFavoriteStop,
                        onSelectOriginTarget = {},
                        onSelectDestinationTarget = {},
                        onQueryChange = {},
                        onSearch = {},
                        onSearchResultSelected = onSearchResultSelected,
                        onRecentSearchSelected = {},
                        onClearRecentSearches = {},
                        onUseCurrentLocation = {},
                        onMapStopSelected = {},
                        onMapPointSelected = {},
                        onMapError = {},
                        onNearbyStopSelected = onNearbyStopSelected,
                        mode = mode,
                    )
                }
            }
        }
    }
}
