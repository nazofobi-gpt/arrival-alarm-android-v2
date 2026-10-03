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
import org.junit.Assert.fail
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
        assertEquals(2, before.trips)
        assertEquals(4, before.stopTimes)
        assertEquals("Known Good Bahnhof", before.knownGoodStopName)
        assertEquals(
            listOf(
                "route-1|trip-1|1|known-good",
                "route-1|trip-1|2|fixture-1",
            ),
            before.graphRows,
        )
        assertEquals(listOf("trip-1|block-1", "trip-2|block-1"), before.continuityRows)
        assertEquals(
            listOf(
                "4|<null>|<null>|trip-1|trip-2",
                "4|fixture-1|fixture-2|trip-2|trip-1",
                "5|<null>|<null>|trip-2|trip-1",
            ),
            before.linkedTransferRows,
        )

        try {
            index.importFeed(invalid.toURI().toURL().toString()) { }
            fail("Expected invalid replacement feed import to fail")
        } catch (_: IllegalStateException) {
            // Expected: integrity validation fails after the transaction has begun.
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
        knownGoodStopName = scalarString(db, "SELECT name FROM stops WHERE id = ?", arrayOf("known-good")),
        graphRows = graphRows(db),
        continuityRows = continuityRows(db),
        linkedTransferRows = linkedTransferRows(db),
    )

    private fun graphRows(db: SQLiteDatabase): List<String> =
        db.rawQuery(
            """
            SELECT r.route_id,t.trip_id,st.stop_sequence,st.stop_id
            FROM routes r
            JOIN trips t ON t.route_id=r.route_id
            JOIN stop_times st ON st.trip_id=t.trip_id
            WHERE r.route_id=? AND t.trip_id=?
            ORDER BY st.stop_sequence
            """.trimIndent(),
            arrayOf("route-1", "trip-1"),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add("${cursor.getString(0)}|${cursor.getString(1)}|${cursor.getInt(2)}|${cursor.getString(3)}")
                }
            }
        }

    private fun continuityRows(db: SQLiteDatabase): List<String> =
        db.rawQuery(
            "SELECT trip_id,block_id FROM trips WHERE block_id=? ORDER BY trip_id",
            arrayOf("block-1"),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add("${cursor.getString(0)}|${cursor.getString(1)}")
            }
        }

    private fun linkedTransferRows(db: SQLiteDatabase): List<String> =
        db.rawQuery(
            """
            SELECT transfer_type,from_stop_id,to_stop_id,from_trip_id,to_trip_id
            FROM transfers
            WHERE transfer_type IN (4,5)
            ORDER BY transfer_type,from_trip_id,to_trip_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val fromStop = if (cursor.isNull(1)) "<null>" else cursor.getString(1)
                    val toStop = if (cursor.isNull(2)) "<null>" else cursor.getString(2)
                    add("${cursor.getInt(0)}|$fromStop|$toStop|${cursor.getString(3)}|${cursor.getString(4)}")
                }
            }
        }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    private fun scalarString(db: SQLiteDatabase, sql: String, args: Array<String>): String =
        db.rawQuery(sql, args).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun writeValid(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            put(zip, "stops.txt", buildString {
                appendLine("stop_id,stop_name,stop_lat,stop_lon,parent_station,location_type")
                appendLine("known-good,Known Good Bahnhof,52.0,8.0,,")
                for (i in 1 until 10_000) {
                    val locationType = if (i == 9_999) "1" else ""
                    appendLine("fixture-$i,Fixture Stop $i,52.0,8.0,,$locationType")
                }
            })
            put(zip, "routes.txt", "route_id,route_short_name,route_long_name,route_type\nroute-1,R1,Fixture Route,3\n")
            put(zip, "trips.txt", "route_id,service_id,trip_id,trip_headsign,block_id\nroute-1,service-1,trip-1,Known Good Bahnhof,block-1\nroute-1,service-1,trip-2,Next Vehicle Trip,block-1\n")
            put(zip, "stop_times.txt", "trip_id,arrival_time,departure_time,stop_id,stop_sequence\ntrip-1,08:00:00,08:00:00,known-good,1\ntrip-1,08:05:00,08:05:00,fixture-1,2\ntrip-2,08:06:00,08:06:00,fixture-1,1\ntrip-2,08:15:00,08:15:00,fixture-2,2\n")
            put(zip, "calendar.txt", "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nservice-1,1,1,1,1,1,1,1,20260101,20261231\n")
            put(
                zip,
                "transfers.txt",
                "from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_trip_id,to_trip_id\n" +
                    ",,4,,trip-1,trip-2\n" +
                    ",,5,,trip-2,trip-1\n" +
                    "fixture-1,fixture-2,4,,trip-2,trip-1\n" +
                    "fixture-9999,fixture-2,4,,trip-1,trip-2\n" +
                    "missing,fixture-2,5,,trip-1,trip-2\n" +
                    "known-good,fixture-1,2,120,,\n" +
                    ",,2,60,trip-1,trip-2\n",
            )
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
        val knownGoodStopName: String,
        val graphRows: List<String>,
        val continuityRows: List<String>,
        val linkedTransferRows: List<String>,
    )
}
