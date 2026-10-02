package com.nazofobi.arrivalalarm

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NationwideTransitRollbackInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanup() { context.deleteDatabase("nationwide_transit.db") }

    @Test
    fun failedRefreshPreservesKnownGoodScheduleGraph() {
        context.deleteDatabase("nationwide_transit.db")
        val valid = File(context.cacheDir, "g174-valid.zip")
        val invalid = File(context.cacheDir, "g174-invalid.zip")
        writeValid(valid)
        writeInvalid(invalid)
        val index = NationwideTransitIndex(context)

        index.importFeed(valid.toURI().toURL().toString()) { }
        val db = index.readableDatabase
        val before = snapshot(index, db)
        assertTrue(before.ready)
        assertEquals(10_000, before.stopCount)
        assertEquals(10_000, before.stops)
        assertEquals(1, before.routes)
        assertEquals(1, before.trips)
        assertEquals(2, before.stopTimes)
        assertTrue(before.knownGoodHits.contains("Known Good Bahnhof"))

        assertFailsWith<IllegalStateException> {
            index.importFeed(invalid.toURI().toURL().toString()) { }
        }

        assertEquals(before, snapshot(index, db))
        index.close()
        valid.delete()
        invalid.delete()
    }

    private fun snapshot(index: NationwideTransitIndex, db: SQLiteDatabase) = Snapshot(
        ready = index.isReady(),
        sourceVersion = index.sourceVersion(),
        stopCount = index.stopCount(),
        stops = count(db, "stops"),
        routes = count(db, "routes"),
        trips = count(db, "trips"),
        stopTimes = count(db, "stop_times"),
        knownGoodHits = index.search("Known Good Bahnhof", 5).map { it.name },
    )

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    private fun writeValid(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            put(zip, "stops.txt", buildString {
                appendLine("stop_id,stop_name,stop_lat,stop_lon,parent_station")
                appendLine("known-good,Known Good Bahnhof,52.0,8.0,")
                for (i in 1 until 10_000) appendLine("fixture-$i,Fixture Stop $i,52.0,8.0,")
            })
            put(zip, "routes.txt", "route_id,route_short_name,route_long_name,route_type\nroute-1,R1,Fixture Route,3\n")
            put(zip, "trips.txt", "route_id,service_id,trip_id,trip_headsign\nroute-1,service-1,trip-1,Known Good Bahnhof\n")
            put(zip, "stop_times.txt", "trip_id,arrival_time,departure_time,stop_id,stop_sequence\ntrip-1,08:00:00,08:00:00,known-good,1\ntrip-1,08:05:00,08:05:00,fixture-1,2\n")
            put(zip, "calendar.txt", "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nservice-1,1,1,1,1,1,1,1,20260101,20261231\n")
        }
    }

    private fun writeInvalid(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            put(zip, "stops.txt", "stop_id,stop_name,stop_lat,stop_lon\ninvalid-stop,Invalid Replacement,53.0,9.0\n")
        }
    }

    private fun put(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(body.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private data class Snapshot(
        val ready: Boolean,
        val sourceVersion: String,
        val stopCount: Int,
        val stops: Int,
        val routes: Int,
        val trips: Int,
        val stopTimes: Int,
        val knownGoodHits: List<String>,
    )
}
