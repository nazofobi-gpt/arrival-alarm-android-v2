package com.nazofobi.arrivalalarm

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.cos

/** Disk-backed stop index for the nationwide GTFS feed. Keeps production search/nearby queries off heap. */
class NationwideTransitIndex(context: Context) : SQLiteOpenHelper(context, "nationwide_transit.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stops (id TEXT PRIMARY KEY, provider_id TEXT NOT NULL, name TEXT NOT NULL, name_folded TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL)")
        db.execSQL("CREATE INDEX stops_name_idx ON stops(name_folded)")
        db.execSQL("CREATE INDEX stops_lat_lon_idx ON stops(lat, lon)")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun replaceStops(stops: Sequence<CatalogStop>, sourceVersion: String, fetchedAt: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("stops", null, null)
            val statement = writableDatabase.compileStatement("INSERT INTO stops(id,provider_id,name,name_folded,lat,lon) VALUES(?,?,?,?,?,?)")
            stops.forEach { stop ->
                statement.clearBindings()
                statement.bindString(1, stop.id)
                statement.bindString(2, stop.providerId)
                statement.bindString(3, stop.name)
                statement.bindString(4, stop.name.lowercase())
                statement.bindDouble(5, stop.latitude)
                statement.bindDouble(6, stop.longitude)
                statement.executeInsert()
            }
            putMetadata("source_version", sourceVersion)
            putMetadata("fetched_at", fetchedAt)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun searchStops(query: String, limit: Int = 20): List<CatalogStop> {
        val folded = query.trim().lowercase()
        if (folded.isEmpty()) return emptyList()
        return readableDatabase.rawQuery(
            "SELECT id,provider_id,name,lat,lon FROM stops WHERE name_folded LIKE ? ORDER BY CASE WHEN name_folded LIKE ? THEN 0 ELSE 1 END,name LIMIT ?",
            arrayOf("%$folded%", "$folded%", limit.coerceIn(1, 100).toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(CatalogStop(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getDouble(3), cursor.getDouble(4)))
            }
        }
    }

    fun nearestStops(latitude: Double, longitude: Double, limit: Int = 5): List<NearbyStop> {
        val latDelta = 0.35
        val lonDelta = 0.35 / cos(Math.toRadians(latitude)).coerceAtLeast(0.2)
        val candidates = readableDatabase.rawQuery(
            "SELECT id,provider_id,name,lat,lon FROM stops WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT 2000",
            arrayOf((latitude-latDelta).toString(), (latitude+latDelta).toString(), (longitude-lonDelta).toString(), (longitude+lonDelta).toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(CatalogStop(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getDouble(3), cursor.getDouble(4)))
            }
        }
        return candidates.map { stop -> NearbyStop(stop, GeoMath.distanceMeters(latitude, longitude, stop.latitude, stop.longitude).toInt()) }
            .sortedBy { it.distanceMeters }
            .take(limit)
    }

    fun metadata(key: String): String? = readableDatabase.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun putMetadata(key: String, value: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES(?,?)", arrayOf(key, value))
    }
}

private object GeoMath {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2-lat1)
        val dl = Math.toRadians(lon2-lon1)
        val a = kotlin.math.sin(dp/2)*kotlin.math.sin(dp/2) + kotlin.math.cos(p1)*kotlin.math.cos(p2)*kotlin.math.sin(dl/2)*kotlin.math.sin(dl/2)
        return 2*r*kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1-a))
    }
}