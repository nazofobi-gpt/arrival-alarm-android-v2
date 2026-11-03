package com.nazofobi.arrivalalarm

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.speech.RecognizerIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class G237VoiceSearchIconInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showSearchPanel(onQueryChange: (String) -> Unit = {}) {
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
                    onQueryChange = onQueryChange,
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
    fun optionalVoiceSearchControlHasAccessibleLabelAndDoesNotRequireMicrophone() {
        showSearchPanel()
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.voice_search_action)
        rule.onNodeWithTag("catalog-voice-search")
            .assertIsDisplayed()
            .assertContentDescriptionEquals(label)
    }

    @Test
    fun recognizedPlaceFillsManualSearchOnlyAfterExplicitAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val recognized = Intent().putStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS, arrayListOf("Bremen Hbf"),
        )
        val monitor = Instrumentation.ActivityMonitor(
            IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            Instrumentation.ActivityResult(Activity.RESULT_OK, recognized),
            true,
        )
        val query = AtomicReference<String?>(null)
        val callbackCount = AtomicInteger()
        instrumentation.addMonitor(monitor)
        try {
            showSearchPanel {
                query.set(it)
                callbackCount.incrementAndGet()
            }
            assertEquals(0, callbackCount.get())
            rule.onNodeWithTag("catalog-voice-search").performClick()
            rule.waitUntil(7_000) { query.get() == "Bremen Hbf" }
            assertTrue(instrumentation.checkMonitorHit(monitor, 1))
            assertEquals(1, callbackCount.get())
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun canceledSpeechKeepsExistingTypedSearchUnchanged() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = Instrumentation.ActivityMonitor(
            IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
            true,
        )
        val callbackCount = AtomicInteger()
        instrumentation.addMonitor(monitor)
        try {
            showSearchPanel { callbackCount.incrementAndGet() }
            rule.onNodeWithTag("catalog-voice-search").performClick()
            rule.waitForIdle()
            assertTrue(instrumentation.checkMonitorHit(monitor, 1))
            assertEquals(0, callbackCount.get())
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
