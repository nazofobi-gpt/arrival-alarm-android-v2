package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import kotlin.math.*

sealed interface NationwideDataState {
    data object Idle : NationwideDataState
    data class Loading(val message: String) : NationwideDataState
    data class Ready(val stopCount: Int, val fetchedAtEpochSeconds: Long) : NationwideDataState
    data class Error(val message: String) : NationwideDataState
}

class NationwideTransitGateway(
    context: Context,
    private val feedUrl: String = "https://download.gtfs.de/germany/free/latest.zip",
    private val liveApi: GermanyLiveTransitApi = GermanyLiveTransitApi(),
) {
    private val appContext = context.applicationContext
    private val store = GermanyStopStore(appContext)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun currentState(): NationwideDataState =
        if (store.isReady()) NationwideDataState.Ready(store.stopCount(), store.fetchedAt()) else NationwideDataState.Idle

    fun ensureNationwideIndex(callback: (NationwideDataState) -> Unit) {
        if (store.isReady()) {
            callback(NationwideDataState.Ready(store.stopCount(), store.fetchedAt()))
            return
        }
        callback(NationwideDataState.Loading("Almanya tam durak verisi hazırlanıyor"))
        io.execute {
            val state = runCatching {
                store.importStops(feedUrl) { count ->
                    if (count % 50_000 == 0) main.post {
                        callback(NationwideDataState.Loading("Almanya durakları indeksleniyor: $count"))
                    }
                }
                NationwideDataState.Ready(store.stopCount(), store.fetchedAt())
            }.getOrElse { NationwideDataState.Error(it.message ?: "Durak verisi yüklenemedi") }
            main.post { callback(state) }
        }
    }

    fun searchStops(query: String, limit: Int = 20, callback: (List<CatalogStop>, String?) -> Unit) {
        val q = query.trim()
        if (q.length < 2) {
            callback(emptyList(), "En az 2 karakter yaz")
            return
        }
        io.execute {
            val local = if (store.isReady()) store.search(q, limit) else emptyList()
            val result = if (local.isNotEmpty()) local else runCatching { liveApi.searchStops(q, limit) }.getOrDefault(emptyList())
            val source = when {
                local.isNotEmpty() -> "GTFS Deutschland yerel indeks"
                result.isNotEmpty() -> "Canlı Almanya araması"
                else -> null
            }
            main.post { callback(result, source) }
        }
    }

    fun nearbyStops(latitude: Double, longitude: Double, limit: Int = 8, callback: (List<NearbyStop>, String?) -> Unit) {
        io.execute {
            val local = if (store.isReady()) store.nearest(latitude, longitude, limit) else emptyList()
            val result = if (local.isNotEmpty()) local else runCatching { liveApi.nearbyStops(latitude, longitude, limit) }.getOrDefault(emptyList())
            val source = when {
                local.isNotEmpty() -> "GTFS Deutschland yerel indeks"
                result.isNotEmpty() -> "Canlı Almanya araması"
                else -> null
            }
            main.post { callback(result, source) }
        }
    }

    fun close() {
        io.shutdownNow()
        store.close()
    }
}

class GermanyLiveTransitApi(
    private val baseUrl: String = "https://v6.db.transport.rest",
    private val connectTimeoutMs: Int = 6_000,
    private val readTimeoutMs: Int = 8_000,
) {
    fun searchStops(query: String, limit: Int): List<CatalogStop> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val json = get("$baseUrl/locations?query=$encoded&results=$limit&stops=true&addresses=false&poi=false")
        return parseStops(JSONArray(json), limit)
    }

    fun nearbyStops(latitude: Double, longitude: Double, limit: Int): List<NearbyStop> {
        val results = max(limit, 12)
        val json = get("$baseUrl/locations/nearby?latitude=$latitude&longitude=$longitude&results=$results&distance=50000&stops=true&poi=false")
        return parseStops(JSONArray(json), results)
            .map { NearbyStop(it, haversineMeters(latitude, longitude, it.latitude, it.longitude).roundToInt()) }
            .sortedBy { it.distanceMeters }
            .take(limit)
    }

    internal fun parseStops(array: JSONArray, limit: Int): List<CatalogStop> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("type") != "stop" && item.optString("type") != "station") continue
            val location = item.optJSONObject("location") ?: continue
            val lat = location.optDouble("latitude", Double.NaN)
            val lon = location.optDouble("longitude", Double.NaN)
            val name = item.optString("name").trim()
            val id = item.optString("id").ifBlank { location.optString("id") }
            if (name.isBlank() || id.isBlank() || !lat.isFinite() || !lon.isFinite()) continue
            add(CatalogStop("db:$id", "db-live", name, lat, lon))
            if (size >= limit) break
        }
    }

    private fun get(url: String): String = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "ArrivalAlarmAndroid/0.1")
        try {
            if (responseCode !in 200..299) error("Transit API HTTP $responseCode")
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }
}

class AndroidCurrentLocation(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val main = Handler(Looper.getMainLooper())

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun request(callback: (Result<GeoPoint>) -> Unit) {
        if (!hasPermission()) {
            callback(Result.failure(SecurityException("Konum izni gerekli")))
            return
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            callback(Result.failure(IllegalStateException("Konum servisi kapalı")))
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(providers.first(), null, context.mainExecutor) { location ->
                    callback(location?.toGeoPoint()?.let { Result.success(it) }
                        ?: Result.failure(IllegalStateException("Güncel konum alınamadı")))
                }
            } else {
                val best = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                    .maxByOrNull { it.time }
                main.post {
                    callback(best?.toGeoPoint()?.let { Result.success(it) }
                        ?: Result.failure(IllegalStateException("Konum henüz hazır değil")))
                }
            }
        }.onFailure { callback(Result.failure(it)) }
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}

private class GermanyStopStore(context: Context) : SQLiteOpenHelper(context, "germany_stops.db", null, 1) {
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

internal fun parseCsvLine(line: String): List<String> {
    val out = ArrayList<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                field.append('"')
                i++
            }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> {
                out += field.toString()
                field.setLength(0)
            }
            else -> field.append(ch)
        }
        i++
    }
    out += field.toString()
    return out
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
