package com.nazofobi.arrivalalarm

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G170NearbyVisualInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lohneNearbyRendersOneCanonicalFavoriteWithBearingAndRetainsScreenshot() {
        val station = CatalogStop(
            id = "lohne-station",
            providerId = "gtfs",
            name = "Lohne Bahnhof",
            latitude = 52.665,
            longitude = 8.237,
        )
        val platformOne = CatalogStop(
            id = "lohne-platform-1",
            providerId = "gtfs",
            name = "Lohne Bahnhof",
            latitude = 52.66501,
            longitude = 8.23701,
            parentStationId = station.id,
        )
        val platformTwo = CatalogStop(
            id = "lohne-platform-2",
            providerId = "gtfs",
            name = "Lohne Bahnhof",
            latitude = 52.66502,
            longitude = 8.23702,
            parentStationId = station.id,
        )
        val nearby = NearbyStationCanonicalizer.canonicalize(
            latitude = 52.6645,
            longitude = 8.237,
            candidates = listOf(
                NearbyStop(platformOne, 1),
                NearbyStop(station, 2),
                NearbyStop(platformTwo, 3),
            ),
            limit = 5,
        )
        assertEquals(1, nearby.size)
        assertEquals(station.id, nearby.single().stop.id)

        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
                        nearby = nearby,
                        nearbySource = "GTFS local",
                        nearbyMessage = null,
                        recentSearches = emptyList(),
                        favoriteStopIds = setOf(station.id),
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
                        mode = HomeSearchMode.HOME,
                    )
                }
            }
        }

        rule.onNodeWithTag("nearby-stop-0")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("★ Lohne Bahnhof", substring = true)
            .assertTextContains("°", substring = true)
        assertEquals(1, rule.onAllNodesWithTag("nearby-stop-0").fetchSemanticsNodes().size)
        assertTrue(rule.onAllNodesWithTag("nearby-stop-1").fetchSemanticsNodes().isEmpty())

        rule.waitForIdle()
        val output = "/sdcard/Download/arrival-alarm-ui-qa/g170-lohne-nearby.png"
        shell("mkdir -p /sdcard/Download/arrival-alarm-ui-qa")
        shell("screencap -p $output")
        val size = shell("stat -c %s $output").trim().toLongOrNull() ?: 0L
        assertTrue("G-170 screenshot must be non-empty", size > 0L)
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.bufferedReader().readText()
        }
    }
}
