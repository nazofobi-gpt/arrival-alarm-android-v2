package com.nazofobi.arrivalalarm

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt

class NationwideTransitIndex(context: Context) : SQLiteOpenHelper(context, "nationwide_transit.db", null, 2) {
    companion object {
        private const val PROVIDER_ID = "gtfs-de-full"
        private const val READY_KEY = "ready"
        private const val FETCHED_KEY = "fetched_at"
        private const val COUNT_KEY = "stop_count"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE stops(id TEXT PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, parent_station TEXT)")
        db.execSQL("CREATE INDEX idx_stops_lat_lon ON stops(lat, lon)")
        db.execSQL("CREATE VIRTUAL TABLE stop_search USING fts4(stop_id, name, tokenize=unicode61)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS stop_search")
        db.execSQL("DROP TABLE IF EXISTS stops")
        db.execSQL("DROP TABLE IF EXISTS metadata")
        onCreate(db)
    }

    fun isReady(): Boolean = metadata(READY_KEY) == "1"
    fun fetchedAt(): Long = metadata(FETCHED_KEY)?.toLongOrNull() ?: 0L
    fun stopCount(): Int = metadata(COUNT_KEY)?.toIntOrNull() ?: 0

    fun importStops(url: String, onCount: (Int) -> Unit) {
        val db = writableDatabase
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ArrivalAlarmAndroid/0.1")
        }
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error("GTFS indirme HTTP $code")
        }

        var imported = 0
        db.beginTransaction()
        try {
            db.delete("stops", null, null)
            db.delete("stop_search", null, null)
            val stopInsert = db.compileStatement("INSERT OR REPLACE INTO stops(id,name,lat,lon,parent_station) VALUES(?,?,?,?,?)")
            val searchInsert = db.compileStatement("INSERT INTO stop_search(stop_id,name) VALUES(?,?)")
            ZipInputStream(BufferedInputStream(connection.inputStream, 128 * 1024)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && entry.name.substringAfterLast('/') != "stops.txt") {
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                if (entry == null) error("GTFS stops.txt bulunamadı")
                val reader = BufferedReader(InputStreamReader(zip, StandardCharsets.UTF_8), 128 * 1024)
                val header = parseCsvLine(reader.readLine() ?: error("GTFS stops.txt boş"))
                val idIndex = header.indexOf("stop_id")
                val nameIndex = header.indexOf("stop_name")
                val latIndex = header.indexOf("stop_lat")
                val lonIndex = header.indexOf("stop_lon")
                val parentIndex = header.indexOf("parent_station")
                if (listOf(idIndex, nameIndex, latIndex, lonIndex).any { it < 0 }) error("GTFS stops.txt zorunlu sütunları eksik")

                while (true) {
                    val line = reader.readLine() ?: break
                    val row = parseCsvLine(line)
                    val maxRequired = maxOf(idIndex, nameIndex, latIndex, lonIndex)
                    if (row.size <= maxRequired) continue
                    val id = row[idIndex].trim()
                    val name = row[nameIndex].trim()
                    val lat = row[latIndex].toDoubleOrNull()
                    val lon = row[lonIndex].toDoubleOrNull()
                    if (id.isBlank() || name.isBlank() || lat == null || lon == null) continue
                    stopInsert.clearBindings()
                    stopInsert.bindString(1, id)
                    stopInsert.bindString(2, name)
                    stopInsert.bindDouble(3, lat)
                    stopInsert.bindDouble(4, lon)
                    if (parentIndex >= 0 && row.size > parentIndex && row[parentIndex].isNotBlank()) stopInsert.bindString(5, row[parentIndex]) else stopInsert.bindNull(5)
                    stopInsert.executeInsert()

                    searchInsert.clearBindings()
                    searchInsert.bindString(1, id)
                    searchInsert.bindString(2, name)
                    searchInsert.executeInsert()

                    imported++
                    if (imported % 50_000 == 0) onCount(imported)
                }
            }
            if (imported < 10_000) error("GTFS durak sayısı beklenenden düşük: $imported")
            putMetadata(db, READY_KEY, "1")
            putMetadata(db, FETCHED_KEY, (System.currentTimeMillis() / 1000).toString())
            putMetadata(db, COUNT_KEY, imported.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            connection.disconnect()
        }
        onCount(imported)
    }

    fun search(query: String, limit: Int): List<CatalogStop> {
        val tokens = query.lowercase(Locale.GERMANY).split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val match = tokens.joinToString(" AND ") { "$it*" }
        val sql = "SELECT s.id,s.name,s.lat,s.lon FROM stop_search f JOIN stops s ON s.id=f.stop_id WHERE stop_search MATCH ? LIMIT ?"
        return readableDatabase.rawQuery(sql, arrayOf(match, limit.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(CatalogStop(c.getString(0), PROVIDER_ID, c.getString(1), c.getDouble(2), c.getDouble(3)))
            }
        }
    }

    fun nearest(latitude: Double, longitude: Double, limit: Int): List<NearbyStop> {
        val radii = listOf(5_000.0, 20_000.0, 80_000.0)
        for (radius in radii) {
            val latDelta = radius / 111_320.0
            val lonDelta = radius / (111_320.0 * cos(Math.toRadians(latitude)).absoluteValue.coerceAtLeast(0.2))
            val sql = "SELECT id,name,lat,lon FROM stops WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT 6000"
            val candidates = readableDatabase.rawQuery(
                sql,
                arrayOf((latitude - latDelta).toString(), (latitude + latDelta).toString(), (longitude - lonDelta).toString(), (longitude + lonDelta).toString()),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val stop = CatalogStop(c.getString(0), PROVIDER_ID, c.getString(1), c.getDouble(2), c.getDouble(3))
                        val distance = haversineMeters(latitude, longitude, stop.latitude, stop.longitude).roundToInt()
                        if (distance <= radius) add(NearbyStop(stop, distance))
                    }
                }
            }.sortedBy { it.distanceMeters }.take(limit)
            if (candidates.size >= limit || (candidates.isNotEmpty() && radius == radii.last())) return candidates
        }
        return emptyList()
    }

    private fun metadata(key: String): String? = readableDatabase.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    private fun putMetadata(db: SQLiteDatabase, key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES(?,?)", arrayOf(key, value))
    }
}

