package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G237VoiceSearchIconInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun optionalVoiceSearchControlHasAccessibleLabelAndDoesNotRequireMicrophone() {
        rule.setContent {
            MaterialTheme {
                HomeSearchPanel(
                    selectingOrigin = true,
                    query = "Lohne",
                    searchBusy = false,
                    searchResults = emptyList(),
                    searchSource = null,
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

        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.voice_search_action)
        rule.onNodeWithTag("catalog-voice-search")
            .assertIsDisplayed()
            .assertContentDescriptionEquals(label)
    }
}
