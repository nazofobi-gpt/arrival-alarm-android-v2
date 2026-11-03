package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G237SearchFeedbackInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyStaleAndUnavailableMessagesHaveDistinctVisibleStates() {
        var kind by mutableStateOf(SearchFeedbackKind.NO_RESULTS)
        var message by mutableStateOf("No matching stops")

        rule.setContent {
            MaterialTheme {
                HomeSearchPanel(
                    selectingOrigin = false,
                    query = "Bremen",
                    searchBusy = false,
                    searchResults = emptyList(),
                    searchSource = "GTFS",
                    searchMessage = message,
                    searchFeedbackKind = kind,
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

        rule.onNodeWithTag("search-feedback-empty").assertIsDisplayed()
        rule.onNodeWithTag("search-message").assertTextContains("No matching stops")

        rule.runOnIdle {
            kind = SearchFeedbackKind.STALE_DATA
            message = "Old data: confirm times"
        }
        rule.onNodeWithTag("search-feedback-stale").assertIsDisplayed()
        rule.onNodeWithTag("search-message").assertTextContains("Old data: confirm times")

        rule.runOnIdle {
            kind = SearchFeedbackKind.ERROR
            message = "Provider unavailable"
        }
        rule.onNodeWithTag("search-feedback-error").assertIsDisplayed()
        rule.onNodeWithTag("search-message").assertTextContains("Provider unavailable")
    }
}
