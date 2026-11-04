package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
}
