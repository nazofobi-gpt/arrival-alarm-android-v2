package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local GTFS acceptance slice: offline search must use the committed SQLite
 * index after the source ZIP disappears and the database helper is recreated.
 * This does not replace Germany Full provider or real-device field acceptance.
 */
@RunWith(AndroidJUnit4::class)
class G173OfflineSearchReopenInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val feed = File(context.cacheDir, "g173-offline-reopen.zip")

    @After fun cleanup() {
        feed.delete()
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test fun committedStopsLinesAndTripsRemainSearchableWithoutSourceZip() {
        context.deleteDatabase("nationwide_transit.db")
        createOfflineFixture(feed)

        val before = NationwideTransitIndex(context)
        val stopIds: Set<String>
        try {
            before.importFeed(feed.toURI().toURL().toString()) { }
            assertTrue(before.isReady())
            assertEquals(10_000, before.stopCount())
            stopIds = before.search("Lohne", 5).map { it.id }.toSet()
            assertEquals(setOf("lohne-hbf", "lohne-sued"), stopIds)
            assertTrue(before.searchRouteAndTripLocations("RB58", 5).any {
                it.kind == TransitLocationKind.LINE && it.routeId == "rb-58"
            })
            assertTrue(before.searchRouteAndTripLocations("Lohne", 5).any {
                it.kind == TransitLocationKind.TRIP && it.tripId == "trip-lohne"
            })
        } finally {
            before.close()
        }

        // No source archive or network is available to the reopened helper.
        assertTrue(feed.delete())
        val after = NationwideTransitIndex(context)
        try {
            assertTrue(after.isReady())
            assertEquals(10_000, after.stopCount())
            assertEquals(stopIds, after.search("Lohne", 5).map { it.id }.toSet())
            assertTrue(after.searchRouteAndTripLocations("RB58", 5).any {
                it.kind == TransitLocationKind.LINE && it.routeId == "rb-58"
            })
            assertTrue(after.searchRouteAndTripLocations("Lohne", 5).any {
                it.kind == TransitLocationKind.TRIP && it.tripId == "trip-lohne"
            })
            assertEquals(
                "lohne-hbf",
                after.nearest(52.665, 8.237, 1).single().stop.id,
            )
        } finally {
            after.close()
        }
    }

    private fun createOfflineFixture(destination: File) {
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            put(zip, "stops.txt", buildString {
                appendLine("stop_id,stop_name,stop_lat,stop_lon")
                appendLine("lohne-hbf,Lohne Hauptbahnhof,52.665,8.237")
                appendLine("lohne-sued,Lohne Sued,52.660,8.240")
                for (i in 1 until 9_999) {
                    appendLine("fixture-$i,Fixture Station $i,50.0,8.0")
                }
            })
            put(zip, "routes.txt",
                "route_id,route_short_name,route_long_name,route_type\n" +
                    "rb-58,RB58,Lohne Local,2\n")
            put(zip, "trips.txt",
                "route_id,service_id,trip_id,trip_headsign\n" +
                    "rb-58,daily,trip-lohne,Lohne Sued\n")
            put(zip, "stop_times.txt",
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
                    "trip-lohne,08:00:00,08:00:00,lohne-hbf,1\n" +
                    "trip-lohne,08:05:00,08:05:00,lohne-sued,2\n")
            put(zip, "calendar.txt",
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                    "daily,1,1,1,1,1,1,1,20260101,20261231\n")
        }
    }

    private fun put(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(body.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
