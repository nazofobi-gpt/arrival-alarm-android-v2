package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertTextContains
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsLineDetailInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test fun actualGtfsStopOrderAndDirectionAreReadWithoutSettingJourney() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val db = index.writableDatabase
        db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('A','Alpha',52.0,8.0)")
        db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('B','Beta',52.1,8.1)")
        db.execSQL("INSERT INTO stops(id,name,lat,lon) VALUES('C','Gamma',52.2,8.2)")
        db.execSQL("INSERT INTO routes(route_id,short_name,route_type) VALUES('R','RE1',2)")
        // T0 sorts ahead of T1 but has only one stop; a valid T1 must still represent Beta.
        db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) VALUES('T0','R','S','Beta',0)")
        db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) VALUES('T1','R','S','Beta',0)")
        db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) VALUES('T2','R','S','Alpha',1)")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T0',1,'A')")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T1',20,'B')")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T1',10,'A')")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T2',1,'C')")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T2',2,'A')")
        // Distinct source encodings of the same unnamed direction must render
        // one option. Otherwise the 8-variant cap could hide another direction.
        db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) VALUES('T3','R','S',NULL,2)")
        db.execSQL("INSERT INTO trips(trip_id,route_id,service_id,headsign,direction_id) VALUES('T4','R','S','',2)")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T3',1,'A')")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T3',2,'B')")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T4',1,'A')")
        db.execSQL("INSERT INTO stop_times(trip_id,stop_sequence,stop_id) VALUES('T4',2,'B')")

        val preview = inspectGtfsLineDetail(index, "R")
        assertNotNull(preview)
        assertEquals("RE1", preview!!.lineName)
        assertEquals(listOf("Alpha", "Beta"), preview.directions[0].stopNames)
        assertEquals(listOf("A", "B"), preview.directions[0].stops.map { it.id })
        assertEquals("Beta", preview.directions[0].headsign)
        assertEquals(listOf("Gamma", "Alpha"), preview.directions[1].stopNames)
        assertEquals(3, preview.directions.size)
        assertEquals("", preview.directions[2].headsign)
        assertEquals(listOf("Alpha", "Beta"), preview.directions[2].stopNames)
        assertFalse(preview.additionalDirectionsOmitted)
        assertNull(inspectGtfsLineDetail(index, "not-a-real-route"))

        // No active feed: a fabricated Ready preview is never returned.
        assertNull(loadGtfsLineDetail(context, "R", "not-loaded"))
        db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('ready','1')")
        db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('source_version','test-gtfs-v1')")
        assertNull(loadGtfsLineDetail(context, "R", "old-feed-version"))
        assertEquals(3, loadGtfsLineDetail(context, "R", "test-gtfs-v1")?.directions?.size)
        index.close()
        val validBoardStop = loadGtfsLineBoardStop(context, "R", "B", "test-gtfs-v1")
        assertEquals("B", validBoardStop?.id)
        assertEquals("Beta", validBoardStop?.name)
        assertNull(loadGtfsLineBoardStop(context, "R", "B", "old-feed"))
        assertNull(loadGtfsLineBoardStop(context, "wrong-route", "B", "test-gtfs-v1"))
        assertNull(loadGtfsLineBoardStop(context, "R", "unverified-stop", "test-gtfs-v1"))
    }

    @Test fun readOnlyLinePreviewDisplaysActualDirectionsAndCloseAction() {
        val preview = GtfsLineDetail(
            lineName = "RE1",
            directions = listOf(GtfsLineDirection("Beta", listOf("Alpha", "Beta"), false)),
            additionalDirectionsOmitted = false,
        )
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                GtfsLineDetailDialog("RE1", preview, false) { dismissed = true }
            }
        }
        composeRule.onNodeWithTag("gtfs-line-detail-dialog").assertExists()
        composeRule.onNodeWithTag("gtfs-line-select-direction-0").assertExists()
        composeRule.onNodeWithTag("gtfs-line-stops-0").assertExists()
        composeRule.onNodeWithTag("gtfs-line-close").assertExists()
        assertFalse(dismissed)
        composeRule.onNodeWithTag("gtfs-line-close").performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun directionPickerRendersOnlyTheSelectedGtfsStopOrder() {
        val preview = GtfsLineDetail(
            lineName = "RE1",
            directions = listOf(
                GtfsLineDirection("Beta", listOf("Alpha", "Beta"), false),
                GtfsLineDirection("Alpha", listOf("Gamma", "Alpha"), false),
            ),
            additionalDirectionsOmitted = false,
        )
        composeRule.setContent {
            MaterialTheme {
                GtfsLineDetailDialog("RE1", preview, false) {}
            }
        }
        composeRule.onNodeWithTag("gtfs-line-stops-0").assertExists()
        composeRule.onNodeWithTag("gtfs-line-stops-1").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-select-direction-1").performClick()
        composeRule.onNodeWithTag("gtfs-line-stops-1").assertExists()
        composeRule.onNodeWithTag("gtfs-line-stops-1").assertTextContains("Gamma", substring = true)
        composeRule.onNodeWithTag("gtfs-line-stops-0").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-select-direction-0").performClick()
        composeRule.onNodeWithTag("gtfs-line-stops-0").assertTextContains("Alpha", substring = true)
    }

    @Test fun filterFindsActualStopAndPreservesSequenceAcrossDirectionChange() {
        val preview = GtfsLineDetail(
            lineName = "RE1",
            directions = listOf(
                GtfsLineDirection("Beta", listOf("Alpha", "Beta", "Gamma"), false),
                GtfsLineDirection("Delta", listOf("Gamma", "Delta"), false),
            ),
            additionalDirectionsOmitted = false,
        )
        composeRule.setContent {
            MaterialTheme { GtfsLineDetailDialog("RE1", preview, false) {} }
        }
        composeRule.onNodeWithTag("gtfs-line-stop-filter").performTextInput("bEtA")
        composeRule.onNodeWithTag("gtfs-line-stops-0")
            .assertTextContains("2. Beta", substring = true)
        composeRule.onNodeWithTag("gtfs-line-select-direction-1").performClick()
        composeRule.onNodeWithTag("gtfs-line-no-matches").assertExists()
        composeRule.onNodeWithTag("gtfs-line-stops-1").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-stop-filter").performTextClearance()
        composeRule.onNodeWithTag("gtfs-line-no-matches").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-stops-1")
            .assertTextContains("1. Gamma", substring = true)
    }
    @Test fun sameNamedDistinctGtfsStopsRemainIndividuallySelectable() {
        val preview = GtfsLineDetail(
            lineName = "RE1",
            directions = listOf(
                GtfsLineDirection(
                    "Central", listOf("Central", "Central", "Other"), false,
                    stops = listOf(
                        GtfsLineStop("platform-A", "Central"),
                        GtfsLineStop("platform-B", "Central"),
                        GtfsLineStop("other", "Other"),
                    ),
                ),
            ),
            additionalDirectionsOmitted = false,
            sourceVersion = "test-gtfs-v1",
        )
        var selectedStopId: String? = null
        composeRule.setContent {
            MaterialTheme {
                GtfsLineDetailDialog(
                    lineLabel = "RE1", detail = preview, loading = false,
                    onOpenDepartures = { selectedStopId = it }, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("gtfs-line-stop-filter").performTextInput("Central")
        composeRule.onNodeWithTag("gtfs-line-open-departures").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-open-departures-0").assertExists()
        composeRule.onNodeWithTag("gtfs-line-open-departures-1").assertExists().performClick()
        composeRule.runOnIdle { assertEquals("platform-B", selectedStopId) }
    }

    @Test fun shortGtfsLineOffersImmediateBoardAccessWithoutTyping() {
        val preview = GtfsLineDetail(
            lineName = "RE1",
            directions = listOf(
                GtfsLineDirection(
                    "Beta", listOf("Alpha", "Beta"), false,
                    stops = listOf(GtfsLineStop("A", "Alpha"), GtfsLineStop("B", "Beta")),
                ),
            ),
            additionalDirectionsOmitted = false,
            sourceVersion = "test-gtfs-v1",
        )
        var selectedStopId: String? = null
        composeRule.setContent {
            MaterialTheme {
                GtfsLineDetailDialog(
                    lineLabel = "RE1", detail = preview, loading = false,
                    onOpenDepartures = { selectedStopId = it }, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("gtfs-line-open-departures-0").assertExists()
        composeRule.onNodeWithTag("gtfs-line-open-departures-1").assertExists().performClick()
        composeRule.runOnIdle { assertEquals("B", selectedStopId) }
    }

    @Test fun longGtfsLineOpensTerminalBoardsWithoutTypingButFiltersIntermediateStops() {
        val stops = (1..9).map { GtfsLineStop("id-$it", "Stop-$it") }
        val preview = GtfsLineDetail(
            lineName = "RE1",
            directions = listOf(
                GtfsLineDirection("Beta", stops.map { it.name }, false, stops = stops),
            ),
            additionalDirectionsOmitted = false,
            sourceVersion = "test-gtfs-v1",
        )
        var selectedStopId: String? = null
        composeRule.setContent {
            MaterialTheme {
                GtfsLineDetailDialog(
                    lineLabel = "RE1", detail = preview, loading = false,
                    onOpenDepartures = { selectedStopId = it }, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("gtfs-line-open-departures").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-open-departures-4").assertExists()
        composeRule.onNodeWithTag("gtfs-line-open-departures-7").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-next-intermediate").assertExists().performScrollTo().performClick()
        composeRule.onNodeWithTag("gtfs-line-open-departures-7").assertExists().performClick()
        composeRule.runOnIdle { assertEquals("id-8", selectedStopId) }
        composeRule.onNodeWithTag("gtfs-line-open-departures-4").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-previous-intermediate").assertExists().performScrollTo().performClick()
        composeRule.onNodeWithTag("gtfs-line-open-departures-4").assertExists()
        composeRule.onNodeWithTag("gtfs-line-open-departures-0").assertExists().performClick()
        composeRule.runOnIdle { assertEquals("id-1", selectedStopId) }
        composeRule.onNodeWithTag("gtfs-line-open-departures-8").assertExists().performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("id-9", selectedStopId) }
        composeRule.onNodeWithTag("gtfs-line-stop-filter").performTextInput("Stop-5")
        composeRule.onNodeWithTag("gtfs-line-open-departures").assertExists().performClick()
        composeRule.runOnIdle { assertEquals("id-5", selectedStopId) }
    }

    @Test fun circularGtfsLineShowsOneBoardButtonForSameSourceTerminal() {
        val stops = (1..9).map { index ->
            if (index == 9) GtfsLineStop("id-1", "Stop-1")
            else GtfsLineStop("id-$index", "Stop-$index")
        }
        val preview = GtfsLineDetail(
            lineName = "Ring",
            directions = listOf(
                GtfsLineDirection("Ring", stops.map { it.name }, false, stops = stops),
            ),
            additionalDirectionsOmitted = false,
            sourceVersion = "test-gtfs-v1",
        )
        var selectedStopId: String? = null
        composeRule.setContent {
            MaterialTheme {
                GtfsLineDetailDialog(
                    lineLabel = "Ring", detail = preview, loading = false,
                    onOpenDepartures = { selectedStopId = it }, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("gtfs-line-open-departures-0").assertExists().performClick()
        composeRule.onNodeWithTag("gtfs-line-open-departures-8").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("id-1", selectedStopId) }
    }

    @Test fun uniquelyFilteredGtfsStopOpensSourceBoundDepartureBoardOnlyOnTap() {
        val preview = GtfsLineDetail(
            lineName = "RE1",
            directions = listOf(
                GtfsLineDirection(
                    "Beta", listOf("Alpha", "Beta", "Gamma"), false,
                    stops = listOf(
                        GtfsLineStop("A", "Alpha"),
                        GtfsLineStop("B", "Beta"),
                        GtfsLineStop("C", "Gamma"),
                    ),
                ),
            ),
            additionalDirectionsOmitted = false,
            sourceVersion = "test-gtfs-v1",
        )
        var selectedStopId: String? = null
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                GtfsLineDetailDialog(
                    lineLabel = "RE1", detail = preview, loading = false,
                    onOpenDepartures = { selectedStopId = it },
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeRule.onNodeWithTag("gtfs-line-open-departures").assertDoesNotExist()
        composeRule.onNodeWithTag("gtfs-line-stop-filter").performTextInput("bEtA")
        composeRule.onNodeWithTag("gtfs-line-open-departures").assertExists().performClick()
        composeRule.runOnIdle {
            assertEquals("B", selectedStopId)
            assertFalse(dismissed)
        }
    }
}
