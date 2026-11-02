package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentSearchRemovalInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun removingOneEntryKeepsOthersAndSurvivesPreferenceReload() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("g183_recent_search_remove_test", Context.MODE_PRIVATE)
        val original = prefs.getString("queries", null)
        try {
            check(prefs.edit().putString("queries", "Bremen\u001FLohne").commit())
            rule.setContent {
                var recents by remember { mutableStateOf(listOf("Bremen", "Lohne")) }
                MaterialTheme {
                    HomeSearchPanel(
                        selectingOrigin = true,
                        query = "",
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
                        recentSearches = recents,
                        favoriteStopIds = emptySet(),
                        onSelectOriginTarget = {},
                        onSelectDestinationTarget = {},
                        onQueryChange = {},
                        onSearch = {},
                        onSearchResultSelected = {},
                        onRecentSearchSelected = {},
                        onRemoveRecentSearch = { query ->
                            recents = RecentSearchHistory.removeAndPersist(prefs, recents, query)
                        },
                        onClearRecentSearches = {},
                        onUseCurrentLocation = {},
                        onMapStopSelected = {},
                        onMapPointSelected = {},
                        onMapError = {},
                        onNearbyStopSelected = {},
                        mode = HomeSearchMode.SEARCH,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
            rule.onNodeWithTag("search-recent-remove-0").performScrollTo().performClick()
            rule.onNodeWithTag("search-recent-0").assertTextContains("Lohne", substring = true)
            rule.runOnIdle {
                assertEquals(listOf("Lohne"), restoredRecentSearchQueries(prefs.getString("queries", null)))
            }
            rule.onNodeWithTag("search-recent-remove-0").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(null, prefs.getString("queries", null))
                assertEquals(emptyList<String>(), restoredRecentSearchQueries(prefs.getString("queries", null)))
            }
        } finally {
            val restore = prefs.edit()
            if (original == null) restore.remove("queries") else restore.putString("queries", original)
            restore.commit()
        }
    }

    @Test
    fun disablingHistoryClearsSavedQueriesAndPreservesSearchIndependence() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("g183_recent_search_toggle_test", Context.MODE_PRIVATE)
        val original = prefs.getString("queries", null)
        val originalEnabled = prefs.getBoolean("history_enabled", true)
        try {
            check(prefs.edit().putBoolean("history_enabled", true).putString("queries", "Bremen").commit())
            var searchCalls = 0
            rule.setContent {
                var enabled by remember { mutableStateOf(true) }
                var recents by remember { mutableStateOf(listOf("Bremen")) }
                MaterialTheme {
                    HomeSearchPanel(
                        selectingOrigin = true,
                        query = "",
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
                        recentSearches = recents,
                        recentHistoryEnabled = enabled,
                        onRecentHistoryEnabledChange = { newValue ->
                            if (RecentSearchHistory.setEnabledAndPersist(prefs, newValue)) {
                                enabled = newValue
                                if (!newValue) recents = emptyList()
                            }
                        },
                        favoriteStopIds = emptySet(),
                        onSelectOriginTarget = {},
                        onSelectDestinationTarget = {},
                        onQueryChange = {},
                        onSearch = { searchCalls++ },
                        onSearchResultSelected = {},
                        onRecentSearchSelected = { searchCalls++ },
                        onClearRecentSearches = {},
                        onUseCurrentLocation = {},
                        onMapStopSelected = {},
                        onMapPointSelected = {},
                        onMapError = {},
                        onNearbyStopSelected = {},
                        mode = HomeSearchMode.SEARCH,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
            rule.onNodeWithTag("search-history-enabled-toggle").performScrollTo().performClick()
            rule.onNodeWithTag("search-recent-0").assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(false, RecentSearchHistory.isEnabled(prefs))
                assertEquals(null, prefs.getString("queries", null))
                assertEquals(0, searchCalls)
            }
            rule.onNodeWithTag("search-history-enabled-toggle").performScrollTo().performClick()
            rule.onNodeWithTag("search-recent-0").assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(true, RecentSearchHistory.isEnabled(prefs))
                assertEquals(null, prefs.getString("queries", null))
                assertEquals(0, searchCalls)
            }
        } finally {
            val editor = prefs.edit().putBoolean("history_enabled", originalEnabled)
            if (original == null) editor.remove("queries") else editor.putString("queries", original)
            editor.commit()
        }
    }

}
