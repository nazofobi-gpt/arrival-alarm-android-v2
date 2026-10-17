package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsConditionalRevalidationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sourceUrl = "https://feed.example.invalid/germany.zip"

    @After fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test fun notModifiedRetainsFullSqliteGraphAndEmitsCachedCount() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        try {
            val first = FakeGtfsHttpConnection(
                URL(sourceUrl), HttpURLConnection.HTTP_OK, validFeed(),
                etag = "\"g185-v1\"",
                lastModified = "Sat, 17 Oct 2026 10:00:00 GMT",
            )
            var firstCount = -1
            index.importFeedWithConnection(sourceUrl, { firstCount = it }, first)
            assertEquals(10_000, firstCount)
            assertEquals(1, first.streamReads)
            assertTrue(index.isReady())
            val before = snapshot(index)
            assertEquals("\"g185-v1\"", index.sourceVersion())
            assertEquals("Known Good Bahnhof", before.knownStopName)

            // Provider revalidation must not reopen the ZIP or modify any SQLite state.
            val unchanged = FakeGtfsHttpConnection(
                URL(sourceUrl), HttpURLConnection.HTTP_NOT_MODIFIED, byteArrayOf(),
            )
            var cachedCount = -1
            index.importFeedWithConnection(sourceUrl, { cachedCount = it }, unchanged)
            assertEquals("\"g185-v1\"", unchanged.getRequestProperty("If-None-Match"))
            assertEquals(
                "Sat, 17 Oct 2026 10:00:00 GMT",
                unchanged.getRequestProperty("If-Modified-Since"),
            )
            assertEquals(10_000, cachedCount)
            assertEquals(0, unchanged.streamReads)
            assertEquals(before, snapshot(index))

            // A 304 from a different source must fail closed rather than trust old data.
            val otherSource = "https://feed.example.invalid/unrelated.zip"
            val invalid304 = FakeGtfsHttpConnection(
                URL(otherSource), HttpURLConnection.HTTP_NOT_MODIFIED, byteArrayOf(),
            )
            try {
                index.importFeedWithConnection(otherSource, { fail("Unexpected count callback") }, invalid304)
                fail("Unconditional HTTP 304 must not be accepted")
            } catch (_: IllegalStateException) {
                // No validators are sent for a different URL.
            }
            assertEquals(null, invalid304.getRequestProperty("If-None-Match"))
            assertEquals(0, invalid304.streamReads)
            assertEquals(before, snapshot(index))
        } finally {
            index.close()
        }
    }

    private fun snapshot(index: NationwideTransitIndex): Snapshot {
        val db = index.readableDatabase
        return Snapshot(
            ready = index.isReady(),
            fetchedAt = index.fetchedAt(),
            version = index.sourceVersion(),
            stopCount = index.stopCount(),
            sqliteStops = count(db, "stops"),
            sqliteRoutes = count(db, "routes"),
            sqliteTrips = count(db, "trips"),
            sqliteStopTimes = count(db, "stop_times"),
            knownStopName = db.rawQuery(
                "SELECT name FROM stops WHERE id=?",
                arrayOf("known-good"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            },
            searchable = index.search("Known Good Bahnhof", 5).any { it.id == "known-good" },
        )
    }

    private fun count(db: android.database.sqlite.SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun validFeed(): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            add(zip, "stops.txt", buildString {
                appendLine("stop_id,stop_name,stop_lat,stop_lon")
                appendLine("known-good,Known Good Bahnhof,52.0,8.0")
                for (i in 1 until 10_000) {
                    appendLine("fixture-$i,Fixture Stop $i,52.0,8.0")
                }
            })
            add(zip, "routes.txt",
                "route_id,route_short_name,route_long_name,route_type\nroute-1,R1,Fixture Route,3\n")
            add(zip, "trips.txt",
                "route_id,service_id,trip_id,trip_headsign\nroute-1,service-1,trip-1,Known Good Bahnhof\n")
            add(zip, "stop_times.txt",
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
                    "trip-1,08:00:00,08:00:00,known-good,1\n" +
                    "trip-1,08:05:00,08:05:00,fixture-1,2\n")
            add(zip, "calendar.txt",
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                    "service-1,1,1,1,1,1,1,1,20260101,20261231\n")
        }
        bytes.toByteArray()
    }

    private fun add(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(body.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private data class Snapshot(
        val ready: Boolean,
        val fetchedAt: Long,
        val version: String,
        val stopCount: Int,
        val sqliteStops: Int,
        val sqliteRoutes: Int,
        val sqliteTrips: Int,
        val sqliteStopTimes: Int,
        val knownStopName: String,
        val searchable: Boolean,
    )

    private class FakeGtfsHttpConnection(
        url: URL,
        private val status: Int,
        private val bytes: ByteArray,
        private val etag: String? = null,
        private val lastModified: String? = null,
    ) : HttpURLConnection(url) {
        var streamReads: Int = 0
            private set

        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String?): String? = when {
            name.equals("ETag", ignoreCase = true) -> etag
            name.equals("Last-Modified", ignoreCase = true) -> lastModified
            else -> null
        }
        override fun getInputStream(): InputStream {
            streamReads++
            assertFalse("304 must never expose a feed stream", status == HttpURLConnection.HTTP_NOT_MODIFIED)
            return ByteArrayInputStream(bytes)
        }
    }
}
