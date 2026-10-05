package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G168SearchAutocompleteUiInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lineAndTripResultsAreVisibleAndSelectableAsAutocomplete() {
        var selected: TransitLocationResult? = null
        val line = TransitLocationResult(
            key = "line:route-1",
            kind = TransitLocationKind.LINE,
            label = "R1 • Fixture Route",
            latitude = 0.0,
            longitude = 0.0,
            routeId = "route-1",
            autocompleteText = "R1",
        )
        val trip = TransitLocationResult(
            key = "trip:trip-1",
            kind = TransitLocationKind.TRIP,
            label = "R1 → Known Good Bahnhof",
            latitude = 0.0,
            longitude = 0.0,
            tripId = "trip-1",
            autocompleteText = "Known Good Bahnhof",
        )

        rule.setContent {
            MaterialTheme {
                HomeSearchPanel(
                    selectingOrigin = true,
                    query = "R",
                    searchBusy = false,
                    searchResults = listOf(line, trip),
                    searchSource = "GTFS",
                    searchMessage = null,
                    locationMessage = null,
                    origin = null,
                    destination = null,
                    mapMessage = null,
                    nearby = emptyList(),
                    nearbySource = null,
                    nearbyMessage = null,
                    recentSearches = emptyList(),
                    favoriteStopIds = emptySet(),
                    onSelectOriginTarget = {},
                    onSelectDestinationTarget = {},
                    onQueryChange = {},
                    onSearch = {},
                    onSearchResultSelected = { selected = it },
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

        rule.onNodeWithTag("search-result-0")
            .assertIsDisplayed()
            .assertTextContains("Hat", substring = true)
            .assertTextContains("R1", substring = true)
        rule.onNodeWithTag("search-result-1")
            .assertIsDisplayed()
            .assertTextContains("Sefer", substring = true)
            .assertTextContains("Known Good Bahnhof", substring = true)
            .performClick()
        rule.runOnIdle {
            assertEquals("trip-1", selected?.tripId)
        }
    }
}
