package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NationwideNearestRankingInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun denseSouthernStopsCannotDisplaceCloserNorthernStopsAtSqlLimit() {
        context.deleteDatabase("nationwide_transit.db")
        NationwideTransitIndex(context).use { index ->
            val db = index.writableDatabase
            db.beginTransaction()
            try {
                val insert = db.compileStatement(
                    "INSERT INTO stops(id,name,lat,lon) VALUES(?,?,?,?)"
                )
                // All 6001 decoys are within the first 5 km bounding box.
                // The legacy indexed scan (latitude ascending, LIMIT 6000) excludes
                // both nearer northern stops before the haversine sort can run.
                repeat(6001) { i ->
                    insert.clearBindings()
                    insert.bindString(1, "decoy-$i")
                    insert.bindString(2, "Farther stop")
                    insert.bindDouble(3, 51.9700)
                    insert.bindDouble(4, 8.0)
                    insert.executeInsert()
                }
                db.execSQL(
                    "INSERT INTO stops(id,name,lat,lon) VALUES('near-1','Nearest',52.0001,8.0)"
                )
                db.execSQL(
                    "INSERT INTO stops(id,name,lat,lon) VALUES('near-2','Second nearest',52.0002,8.0)"
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val nearby = index.nearest(52.0, 8.0, limit = 2)
            assertEquals(listOf("near-1", "near-2"), nearby.map { it.stop.id })
            assertTrue(nearby[0].distanceMeters < nearby[1].distanceMeters)

            val stations = index.nearestCanonicalStations(52.0, 8.0, limit = 2)
            assertEquals(listOf("near-1", "near-2"), stations.map { it.stop.id })
        }
    }
}
