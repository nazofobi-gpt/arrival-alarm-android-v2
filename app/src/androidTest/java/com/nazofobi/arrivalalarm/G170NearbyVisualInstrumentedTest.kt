package com.nazofobi.arrivalalarm

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class G170NearbyVisualInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun canonicalNearbyKeepsPlatformsHiddenUntilExpanded() {
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
        assertEquals(
            listOf(platformOne.id, platformTwo.id),
            nearby.single().childStops.map { it.id },
        )

        setNearbyContent(nearby, favoriteStopIds = setOf(station.id))

        rule.onNodeWithTag("nearby-stop-0")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("★ Lohne Bahnhof", substring = true)
            .assertTextContains("°", substring = true)
        assertEquals(1, rule.onAllNodesWithTag("nearby-stop-0").fetchSemanticsNodes().size)
        assertTrue(rule.onAllNodesWithTag("nearby-stop-1").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("nearby-stop-0-child-0").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("nearby-stop-0-expand")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.onNodeWithTag("nearby-stop-0-child-0")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Lohne Bahnhof", substring = true)
        rule.onNodeWithTag("nearby-stop-0-child-1")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun realGermanyFeedLohneNearbyHasOneCanonicalStationAndRetainsScreenshot() {
        val archivePath = InstrumentationRegistry.getArguments()
            .getString("germanyFullPath")
            .orEmpty()
        if (archivePath.isBlank()) return
        val archive = File(archivePath)
        assertTrue(
            "Germany Full-derived G-170 archive missing: $archivePath",
            archive.isFile && archive.length() > 0L,
        )
        val lohne = lohneFixture(archive)

        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val nearby = try {
            index.importFeed(archive.toURI().toString()) { }
            assertTrue("G-170 real feed import must be ready", index.isReady())
            index.nearestCanonicalStations(
                latitude = lohne.latitude,
                longitude = lohne.longitude,
                limit = 8,
            )
        } finally {
            index.close()
        }

        assertTrue("real Germany feed must expose Lohne nearby rows", nearby.isNotEmpty())
        val matching = nearby.filter {
            it.stop.name.equals(lohne.label, ignoreCase = true)
        }
        assertEquals(
            "Lohne station must appear once after station-level canonicalization",
            1,
            matching.size,
        )
        val station = matching.single()
        assertTrue(
            "real Lohne canonical station must retain expandable child stop/platform rows",
            station.childStops.isNotEmpty(),
        )
        assertTrue("real Lohne canonical station must expose bearing", station.bearingDegrees != null)
        val stationIndex = nearby.indexOfFirst { it.stop.id == station.stop.id }
        assertTrue("real Lohne canonical row must be visible in top 8", stationIndex in 0..7)

        setNearbyContent(nearby, favoriteStopIds = setOf(station.stop.id))

        rule.onNodeWithTag("nearby-stop-$stationIndex")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(station.stop.name, substring = true)
            .assertTextContains("°", substring = true)
        assertTrue(
            rule.onAllNodesWithTag("nearby-stop-$stationIndex-child-0")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        rule.onNodeWithTag("nearby-stop-$stationIndex-expand")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.onNodeWithTag("nearby-stop-$stationIndex-child-0")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("nearby-stop-$stationIndex-expand")
            .performScrollTo()
            .performClick()
        assertTrue(
            rule.onAllNodesWithTag("nearby-stop-$stationIndex-child-0")
                .fetchSemanticsNodes()
                .isEmpty()
        )

        captureScreenshot("/sdcard/Download/arrival-alarm-ui-qa/g170-lohne-nearby.png")
    }

    private fun setNearbyContent(
        nearby: List<NearbyStop>,
        favoriteStopIds: Set<String>,
    ) {
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
                        favoriteStopIds = favoriteStopIds,
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
    }

    private data class LohneFixture(
        val label: String,
        val latitude: Double,
        val longitude: Double,
    )

    private fun lohneFixture(archive: File): LohneFixture =
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry("g175_acceptance.json")
                ?: error("g175_acceptance.json missing from G-170 real-feed archive")
            val payload = zip.getInputStream(entry).bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            val origin = payload
                .getJSONObject("cases")
                .getJSONObject("lohne-achim")
                .getJSONObject("origin")
            LohneFixture(
                label = origin.getString("name"),
                latitude = origin.getDouble("lat"),
                longitude = origin.getDouble("lon"),
            )
        }

    private fun captureScreenshot(path: String) {
        rule.waitForIdle()
        shell("mkdir -p /sdcard/Download/arrival-alarm-ui-qa")
        shell("screencap -p $path")
        val size = shell("stat -c %s $path").trim().toLongOrNull() ?: 0L
        assertTrue("G-170 real-feed screenshot must be non-empty", size > 0L)
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
