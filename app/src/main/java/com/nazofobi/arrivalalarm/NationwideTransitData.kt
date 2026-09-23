package com.nazofobi.arrivalalarm

import android.Manifest
import android.annotation.SuppressLint
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
    private val store = NationwideTransitIndex(appContext)
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

    @SuppressLint("MissingPermission")
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

internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
