package com.nazofobi.arrivalalarm

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NationwideTransitIndexInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun freshSchemaContainsFullScheduleGraph() {
        context.deleteDatabase("nationwide_transit.db")
        val helper = NationwideTransitIndex(context)
        val db = helper.writableDatabase

        val expected = setOf(
            "metadata", "stops", "stop_search", "agency", "routes", "trips",
            "stop_times", "calendar", "calendar_dates", "transfers", "shapes", "levels",
            "pathways", "frequencies", "attributions", "feed_info"
        )
        val actual = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table','view')",
            null,
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

        assertTrue(actual.containsAll(expected))
        assertEquals(4, db.version)
        helper.close()
    }

    @Test
    fun v3UpgradePreservesGraphAndRelaxesLinkedTransferStops() {
        context.deleteDatabase("nationwide_transit.db")
        val path = context.getDatabasePath("nationwide_transit.db")
        path.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(path, null)
        legacy.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO metadata(key,value) VALUES('ready','1')")
        legacy.execSQL("INSERT INTO metadata(key,value) VALUES('schema_version','3')")
        legacy.execSQL(
            "CREATE TABLE trips(trip_id TEXT PRIMARY KEY, route_id TEXT NOT NULL, service_id TEXT NOT NULL, headsign TEXT, direction_id INTEGER, shape_id TEXT, wheelchair_accessible INTEGER, bikes_allowed INTEGER)"
        )
        legacy.execSQL(
            "CREATE TABLE transfers(from_stop_id TEXT NOT NULL, to_stop_id TEXT NOT NULL, transfer_type INTEGER, min_transfer_time INTEGER, from_route_id TEXT, to_route_id TEXT, from_trip_id TEXT, to_trip_id TEXT)"
        )
        legacy.execSQL(
            "INSERT INTO trips(trip_id,route_id,service_id,headsign) VALUES('legacy-trip','R','S','Legacy')"
        )
        legacy.execSQL(
            "INSERT INTO transfers(from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_trip_id,to_trip_id) VALUES('A','B',2,120,'legacy-trip','next-trip')"
        )
        legacy.version = 3
        legacy.close()

        val helper = NationwideTransitIndex(context)
        val db = helper.writableDatabase

        assertEquals(4, db.version)
        assertEquals(
            "legacy-trip",
            db.rawQuery("SELECT trip_id FROM trips WHERE trip_id='legacy-trip'", null).use {
                assertTrue(it.moveToFirst())
                it.getString(0)
            },
        )
        assertEquals(
            "A|B|2|120",
            db.rawQuery(
                "SELECT from_stop_id,to_stop_id,transfer_type,min_transfer_time FROM transfers LIMIT 1",
                null,
            ).use {
                assertTrue(it.moveToFirst())
                "${it.getString(0)}|${it.getString(1)}|${it.getInt(2)}|${it.getInt(3)}"
            },
        )

        val tripColumns = db.rawQuery("PRAGMA table_info(trips)", null).use { cursor ->
            buildSet {
                val name = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(name))
            }
        }
        assertTrue(tripColumns.contains("block_id"))

        val nullableTransferColumns = db.rawQuery("PRAGMA table_info(transfers)", null).use { cursor ->
            buildMap {
                val name = cursor.getColumnIndexOrThrow("name")
                val notNull = cursor.getColumnIndexOrThrow("notnull")
                while (cursor.moveToNext()) put(cursor.getString(name), cursor.getInt(notNull))
            }
        }
        assertEquals(0, nullableTransferColumns["from_stop_id"])
        assertEquals(0, nullableTransferColumns["to_stop_id"])

        val indexes = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index'",
            null,
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertTrue(indexes.contains("idx_trips_block_service"))
        assertTrue(indexes.contains("idx_transfers_from_trip_type"))

        db.execSQL(
            "INSERT INTO transfers(from_stop_id,to_stop_id,transfer_type,from_trip_id,to_trip_id) VALUES(NULL,NULL,4,'legacy-trip','next-trip')"
        )
        assertEquals(
            1,
            db.rawQuery(
                "SELECT COUNT(*) FROM transfers WHERE transfer_type=4 AND from_stop_id IS NULL AND to_stop_id IS NULL",
                null,
            ).use { it.moveToFirst(); it.getInt(0) },
        )
        assertEquals(
            "4",
            db.rawQuery("SELECT value FROM metadata WHERE key='schema_version'", null).use {
                assertTrue(it.moveToFirst())
                it.getString(0)
            },
        )
        helper.close()
    }

    @Test
    fun v2UpgradePreservesStopsAndAddsScheduleTables() {
        context.deleteDatabase("nationwide_transit.db")
        val path = context.getDatabasePath("nationwide_transit.db")
        path.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(path, null)
        legacy.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        legacy.execSQL("CREATE TABLE stops(id TEXT PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, parent_station TEXT)")
        legacy.execSQL("CREATE INDEX idx_stops_lat_lon ON stops(lat, lon)")
        legacy.execSQL("CREATE INDEX idx_stops_parent ON stops(parent_station)")
        legacy.execSQL("CREATE VIRTUAL TABLE stop_search USING fts4(stop_id, name, tokenize=unicode61)")
        legacy.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station) VALUES('legacy-stop','Legacy Bahnhof',52.0,8.0,NULL)")
        legacy.version = 2
        legacy.close()

        val helper = NationwideTransitIndex(context)
        val db = helper.writableDatabase

        val legacyCount = db.rawQuery(
            "SELECT COUNT(*) FROM stops WHERE id='legacy-stop'",
            null,
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(1, legacyCount)

        val stopColumns = db.rawQuery("PRAGMA table_info(stops)", null).use { cursor ->
            buildSet {
                val name = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(name))
            }
        }
        assertTrue(
            stopColumns.containsAll(
                setOf(
                    "location_type", "platform_code", "wheelchair_boarding",
                    "stop_code", "zone_id", "stop_timezone", "level_id",
                )
            )
        )

        val scheduleTables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'",
            null,
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertTrue(
            scheduleTables.containsAll(
                setOf(
                    "agency", "routes", "trips", "stop_times", "calendar", "calendar_dates",
                    "transfers", "shapes", "levels", "pathways", "frequencies", "attributions", "feed_info",
                )
            )
        )
        assertEquals(4, db.version)
        helper.close()
    }
}
