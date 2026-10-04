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

@RunWith(AndroidJUnit4::class)
class NationwideTransitSearchInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanup() { context.deleteDatabase("nationwide_transit.db") }

    @Test fun importedStopIsSearchableThroughFtsAndPublicApi() {
        context.deleteDatabase("nationwide_transit.db")
        val feed = File(context.cacheDir, "g168-search-valid.zip")
        writeFeed(feed)
        val index = NationwideTransitIndex(context)
        index.importFeed(feed.toURI().toURL().toString()) { }

        val db = index.readableDatabase
        assertEquals(10_000, scalarInt(db, "SELECT COUNT(*) FROM stops"))
        assertEquals(10_000, scalarInt(db, "SELECT COUNT(*) FROM stop_search"))
        assertEquals(
            1,
            scalarInt(
                db,
                "SELECT COUNT(*) FROM stop_search WHERE stop_search MATCH ?",
                arrayOf("known* good* bahnhof*"),
            ),
        )
        assertEquals(
            1,
            scalarInt(
                db,
                "SELECT COUNT(*) FROM stop_search f JOIN stops s ON s.id=f.stop_id WHERE stop_search MATCH ?",
                arrayOf("known* good* bahnhof*"),
            ),
        )
        assertEquals(listOf("known-good"), index.search("Known Good Bahnhof", 5).map { it.id })
        assertEquals(listOf("known-good"), index.search("known good bahn", 5).map { it.id })

        index.close()
        assertTrue(feed.delete())
    }

    private fun scalarInt(
        db: android.database.sqlite.SQLiteDatabase,
        sql: String,
        args: Array<String>? = null,
    ): Int = db.rawQuery(sql, args).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun writeFeed(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            put(zip, "stops.txt", buildString {
                appendLine("stop_id,stop_name,stop_lat,stop_lon")
                appendLine("known-good,Known Good Bahnhof,52.0,8.0")
                for (i in 1 until 10_000) appendLine("fixture-$i,Fixture Stop $i,52.0,8.0")
            })
            put(zip, "routes.txt", "route_id,route_short_name,route_long_name,route_type\nroute-1,R1,Fixture Route,3\n")
            put(zip, "trips.txt", "route_id,service_id,trip_id,trip_headsign\nroute-1,service-1,trip-1,Known Good Bahnhof\n")
            put(zip, "stop_times.txt", "trip_id,arrival_time,departure_time,stop_id,stop_sequence\ntrip-1,08:00:00,08:00:00,known-good,1\ntrip-1,08:05:00,08:05:00,fixture-1,2\n")
            put(zip, "calendar.txt", "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nservice-1,1,1,1,1,1,1,1,20260101,20261231\n")
        }
    }

    private fun put(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(body.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
