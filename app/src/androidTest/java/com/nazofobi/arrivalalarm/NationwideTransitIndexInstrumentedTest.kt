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
            "stop_times", "calendar", "calendar_dates", "transfers", "shapes", "feed_info"
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
        assertEquals(3, db.version)
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
        assertTrue(stopColumns.containsAll(setOf("location_type", "platform_code", "wheelchair_boarding")))

        val scheduleTables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'",
            null,
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertTrue(scheduleTables.containsAll(setOf("agency", "routes", "trips", "stop_times", "calendar", "calendar_dates", "transfers", "shapes", "feed_info")))
        assertEquals(3, db.version)
        helper.close()
    }
}
