package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteStopsHomePanelTest {
    @get:Rule val rule = createComposeRule()

    private val stop = CatalogStop("lohne", "gtfs-de-full", "Lohne Bahnhof", 52.665, 8.237)

    @Test
    fun savedFavoriteOpensRealStopDepartureBoardWithoutNearbyGps() {
        var opened: CatalogStop? = null
        rule.setContent {
            MaterialTheme {
                FavoriteStopsHomePanel(
                    favoriteIds = setOf(stop.id),
                    stops = listOf(stop),
                    onOpenDepartures = { opened = it },
                    onOpenSearch = {},
                )
            }
        }
        rule.onNodeWithTag("home-favorite-stop-0").performClick()
        rule.runOnIdle { assertEquals(stop, opened) }
    }

    @Test
    fun unavailableBookmarkNeverNavigatesToPhantomStop() {
        var opened = false
        rule.setContent {
            MaterialTheme {
                FavoriteStopsHomePanel(
                    favoriteIds = setOf("old-feed-stop"),
                    stops = listOf(stop),
                    onOpenDepartures = { opened = true },
                    onOpenSearch = {},
                )
            }
        }
        rule.onNodeWithTag("home-favorites-unavailable").assertExists()
        rule.onNodeWithTag("home-favorite-stop-0").assertDoesNotExist()
        rule.runOnIdle { assertEquals(false, opened) }
    }

    @Test
    fun emptyFavoritesCanOpenExistingSearchFlow() {
        var search = 0
        rule.setContent {
            MaterialTheme {
                FavoriteStopsHomePanel(
                    favoriteIds = emptySet(),
                    stops = emptyList(),
                    onOpenDepartures = {},
                    onOpenSearch = { search++ },
                )
            }
        }
        rule.onNodeWithTag("home-favorites-search").performClick()
        rule.runOnIdle { assertEquals(1, search) }
    }

    @Test
    fun bookmarkedStopIdsResolveFromOfflineIndexAndIgnoreMissingOrCoordinateIds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("nationwide_transit.db")
        try {
            NationwideTransitIndex(context).use { index ->
                val database = index.writableDatabase
                database.execSQL(
                    "INSERT INTO stops(id,name,lat,lon) VALUES(?,?,?,?)",
                    arrayOf("lohne", "Lohne Bahnhof", 52.665, 8.237),
                )
                database.execSQL("INSERT INTO metadata(key,value) VALUES('ready','1')")
                assertEquals(
                    listOf("lohne"),
                    index.favoriteStopsById(listOf("missing", "coord:1", "lohne")).map { it.id },
                )
                assertTrue(index.favoriteStopsById(listOf("lohne"), limit = 0).isEmpty())
                database.execSQL("UPDATE metadata SET value='0' WHERE key='ready'")
                assertTrue(index.favoriteStopsById(listOf("lohne")).isEmpty())
            }
        } finally {
            context.deleteDatabase("nationwide_transit.db")
        }
    }
}
