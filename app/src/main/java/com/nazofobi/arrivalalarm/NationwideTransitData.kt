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
import org.json.JSONObject
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
    data class Ready(val stopCount: Int, val fetchedAtEpochSeconds: Long, val sourceVersion: String) : NationwideDataState
    data class Error(val message: String) : NationwideDataState
}

enum class TransitLocationKind { STOP, ADDRESS, POI }

data class TransitLocationResult(
    val key: String,
    val kind: TransitLocationKind,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val stop: CatalogStop? = null,
) {
    val point: MapPoint get() = MapPoint(latitude, longitude, label)
}

data class StationDeparturesSnapshot(
    val departures: List<Departure> = emptyList(),
    val alerts: List<ServiceAlert> = emptyList(),
    val sourceLabel: String? = null,
    val fetchedAtEpochSeconds: Long = 0L,
    val error: String? = null,
)

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
        if (store.isReady()) NationwideDataState.Ready(store.stopCount(), store.fetchedAt(), store.sourceVersion()) else NationwideDataState.Idle

    fun ensureNationwideIndex(callback: (NationwideDataState) -> Unit) {
        if (store.isReady()) {
            callback(NationwideDataState.Ready(store.stopCount(), store.fetchedAt(), store.sourceVersion()))
            return
        }
        callback(NationwideDataState.Loading(appContext.getString(R.string.data_loading)))
        io.execute {
            val state = runCatching {
                store.importStops(feedUrl) { count ->
                    if (count % 50_000 == 0) main.post {
                        callback(NationwideDataState.Loading(appContext.getString(R.string.data_indexing, count)))
                    }
                }
                NationwideDataState.Ready(store.stopCount(), store.fetchedAt(), store.sourceVersion())
            }.getOrElse { NationwideDataState.Error(it.message ?: appContext.getString(R.string.data_load_failed)) }
            main.post { callback(state) }
        }
    }

    fun searchStops(query: String, limit: Int = 20, callback: (List<CatalogStop>, String?) -> Unit) {
        val q = query.trim()
        if (q.length < 2) {
            callback(emptyList(), appContext.getString(R.string.min_two_chars))
            return
        }
        io.execute {
            val local = if (store.isReady()) store.search(q, limit) else emptyList()
            val result = if (local.isNotEmpty()) local else runCatching { liveApi.searchStops(q, limit) }.getOrDefault(emptyList())
            val source = when {
                local.isNotEmpty() -> appContext.getString(R.string.source_gtfs_local)
                result.isNotEmpty() -> appContext.getString(R.string.source_live_germany)
                else -> null
            }
            main.post { callback(result, source) }
        }
    }

    fun searchLocations(
        query: String,
        limit: Int = 20,
        callback: (List<TransitLocationResult>, String?) -> Unit,
    ) {
        val q = query.trim()
        if (q.length < 2) {
            callback(emptyList(), appContext.getString(R.string.min_two_chars))
            return
        }
        io.execute {
            val localStops = if (store.isReady()) store.search(q, limit) else emptyList()
            val local = localStops.map { stop ->
                TransitLocationResult(
                    key = "stop:${stop.id}",
                    kind = TransitLocationKind.STOP,
                    label = stop.name,
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                    stop = stop,
                )
            }
            val live = runCatching { liveApi.searchLocations(q, limit) }.getOrDefault(emptyList())
            val merged = (local + live)
                .distinctBy { result ->
                    if (result.kind == TransitLocationKind.STOP && result.stop != null) {
                        "stop:${result.stop.id}"
                    } else {
                        "${result.kind}:${result.label.lowercase(Locale.ROOT)}:" +
                            "${"%.5f".format(Locale.ROOT, result.latitude)}:" +
                            "${"%.5f".format(Locale.ROOT, result.longitude)}"
                    }
                }
                .take(limit)
            val source = when {
                local.isNotEmpty() && live.isNotEmpty() ->
                    "${appContext.getString(R.string.source_gtfs_local)} + ${appContext.getString(R.string.source_live_germany)}"
                local.isNotEmpty() -> appContext.getString(R.string.source_gtfs_local)
                live.isNotEmpty() -> appContext.getString(R.string.source_live_germany)
                else -> null
            }
            main.post { callback(merged, source) }
        }
    }

    fun nearbyStops(latitude: Double, longitude: Double, limit: Int = 8, callback: (List<NearbyStop>, String?) -> Unit) {
        io.execute {
            val local = if (store.isReady()) store.nearest(latitude, longitude, limit) else emptyList()
            val result = if (local.isNotEmpty()) local else runCatching { liveApi.nearbyStops(latitude, longitude, limit) }.getOrDefault(emptyList())
            val source = when {
                local.isNotEmpty() -> appContext.getString(R.string.source_gtfs_local)
                result.isNotEmpty() -> appContext.getString(R.string.source_live_germany)
                else -> null
            }
            main.post { callback(result, source) }
        }
    }

    fun stationDepartures(
        stop: CatalogStop,
        limit: Int = 8,
        callback: (StationDeparturesSnapshot) -> Unit,
    ) {
        io.execute {
            val snapshot = runCatching {
                val liveStop = if (stop.id.startsWith("db:")) {
                    stop
                } else {
                    liveApi.searchStops(stop.name, 8)
                        .minByOrNull { candidate ->
                            haversineMeters(
                                stop.latitude,
                                stop.longitude,
                                candidate.latitude,
                                candidate.longitude,
                            )
                        }
                        ?.takeIf { candidate ->
                            haversineMeters(
                                stop.latitude,
                                stop.longitude,
                                candidate.latitude,
                                candidate.longitude,
                            ) <= 1_000.0
                        }
                        ?: error("Live stop mapping unavailable")
                }
                liveApi.departures(liveStop.id, limit)
            }.getOrElse { error ->
                StationDeparturesSnapshot(
                    error = error.message ?: appContext.getString(R.string.unknown_error),
                )
            }
            main.post { callback(snapshot) }
        }
    }

    fun journeyOptions(
        origin: MapPoint,
        destination: MapPoint,
        limit: Int = 3,
        callback: (List<RouteOption>, String) -> Unit,
    ) {
        io.execute {
            val result = runCatching { liveApi.journeys(origin, destination, limit) }
            val options = result.getOrDefault(emptyList())
            val status = when {
                options.isNotEmpty() -> appContext.getString(R.string.route_live_static)
                result.isFailure -> appContext.getString(
                    R.string.route_live_unavailable,
                    result.exceptionOrNull()?.message ?: appContext.getString(R.string.unknown_error),
                )
                else -> appContext.getString(R.string.route_not_found)
            }
            main.post { callback(options, status) }
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
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {
    fun searchStops(query: String, limit: Int): List<CatalogStop> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val json = get("$baseUrl/locations?query=$encoded&results=$limit&stops=true&addresses=false&poi=false")
        return parseStops(JSONArray(json), limit)
    }

    fun searchLocations(query: String, limit: Int): List<TransitLocationResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val json = get(
            "$baseUrl/locations?query=$encoded&results=$limit&stops=true&addresses=true&poi=true&language=de&pretty=false"
        )
        return parseLocations(JSONArray(json), limit)
    }

    fun nearbyStops(latitude: Double, longitude: Double, limit: Int): List<NearbyStop> {
        val results = max(limit, 12)
        val json = get("$baseUrl/locations/nearby?latitude=$latitude&longitude=$longitude&results=$results&distance=50000&stops=true&poi=false")
        return parseStops(JSONArray(json), results)
            .map { NearbyStop(it, haversineMeters(latitude, longitude, it.latitude, it.longitude).roundToInt()) }
            .sortedBy { it.distanceMeters }
            .take(limit)
    }

    fun departures(stopId: String, limit: Int = 8): StationDeparturesSnapshot {
        val rawStopId = stopId.removePrefix("db:")
        require(rawStopId.isNotBlank())
        val encoded = URLEncoder.encode(rawStopId, StandardCharsets.UTF_8.name())
        val json = get(
            "$baseUrl/stops/$encoded/departures?results=${limit.coerceIn(1, 20)}&duration=120&remarks=true&language=de&pretty=false"
        )
        return parseDepartures(JSONArray(json), limit)
    }

    internal fun parseDepartures(
        array: JSONArray,
        limit: Int = 8,
    ): StationDeparturesSnapshot {
        val departures = mutableListOf<Departure>()
        val alerts = linkedMapOf<String, ServiceAlert>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val tripId = item.optString("tripId").takeIf { it.isNotBlank() } ?: continue
            val line = item.optJSONObject("line")
            val lineName = line?.optString("name")?.takeIf { it.isNotBlank() }
                ?: line?.optString("id")?.takeIf { it.isNotBlank() }
                ?: continue
            val direction = item.optString("direction").takeIf { it.isNotBlank() } ?: continue
            val plannedIso = item.optString("plannedWhen").takeIf { it.isNotBlank() }
            val actualIso = item.optString("when").takeIf { it.isNotBlank() }
            val plannedEpoch = plannedIso.toTransitEpochSecondsOrNull()
            val actualEpoch = actualIso.toTransitEpochSecondsOrNull()
            val scheduledEpoch = plannedEpoch ?: actualEpoch ?: continue
            val realtimeEpoch = actualEpoch?.takeIf { plannedEpoch != null && it != plannedEpoch }
            val platform = item.optString("platform").takeIf { it.isNotBlank() }
                ?: item.optString("plannedPlatform").takeIf { it.isNotBlank() }
            val cancelled = item.optBoolean("cancelled", false)

            departures += Departure(
                tripId = tripId,
                line = lineName,
                direction = direction,
                scheduledEpochSeconds = scheduledEpoch,
                realtimeEpochSeconds = realtimeEpoch,
                cancelled = cancelled,
                platform = platform,
            )

            val remarks = item.optJSONArray("remarks")
            if (remarks != null) {
                for (j in 0 until remarks.length()) {
                    val remark = remarks.optJSONObject(j) ?: continue
                    val detail = remark.optString("text").takeIf { it.isNotBlank() }
                        ?: remark.optString("summary").takeIf { it.isNotBlank() }
                        ?: continue
                    val key = remark.optString("code").takeIf { it.isNotBlank() }
                        ?: "$tripId:$j:$detail"
                    if (!alerts.containsKey(key)) {
                        alerts[key] = ServiceAlert(
                            id = key,
                            title = "$lineName → $direction",
                            detail = detail,
                        )
                    }
                }
            }
            if (departures.size >= limit.coerceIn(1, 20)) break
        }
        return StationDeparturesSnapshot(
            departures = departures,
            alerts = alerts.values.toList(),
            sourceLabel = "v6.db.transport.rest",
            fetchedAtEpochSeconds = nowEpochSeconds(),
        )
    }

    fun journeys(origin: MapPoint, destination: MapPoint, limit: Int = 3): List<RouteOption> {
        val fromLabel = URLEncoder.encode(origin.label, StandardCharsets.UTF_8.name())
        val toLabel = URLEncoder.encode(destination.label, StandardCharsets.UTF_8.name())
        val url = buildString {
            append("$baseUrl/journeys?")
            append("from.latitude=${origin.latitude}&from.longitude=${origin.longitude}&from.address=$fromLabel")
            append("&to.latitude=${destination.latitude}&to.longitude=${destination.longitude}&to.address=$toLabel")
            append("&results=${limit.coerceIn(1, 6)}&stopovers=true&language=de&pretty=false")
        }
        return parseJourneys(JSONObject(get(url)), origin, destination, limit)
    }

    internal fun parseJourneys(root: JSONObject, origin: MapPoint, destination: MapPoint, limit: Int): List<RouteOption> {
        val journeys = root.optJSONArray("journeys") ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(journeys.length(), limit.coerceIn(1, 6))) {
                val journey = journeys.optJSONObject(i) ?: continue
                val legs = journey.optJSONArray("legs") ?: continue
                if (legs.length() == 0) continue
                val lines = mutableListOf<String>()
                val tripIds = mutableListOf<String>()
                val stops = mutableListOf<RouteStop>()
                var direction = destination.label
                var walkingMinutes = 0
                var transitLegs = 0
                var departure = ""
                var arrival = ""
                for (j in 0 until legs.length()) {
                    val leg = legs.optJSONObject(j) ?: continue
                    val legDeparture = leg.optString("departure").ifBlank { leg.optString("plannedDeparture") }
                    val legArrival = leg.optString("arrival").ifBlank { leg.optString("plannedArrival") }
                    if (j == 0) departure = displayClock(legDeparture)
                    if (j == legs.length() - 1) arrival = displayClock(legArrival)
                    val line = leg.optJSONObject("line")
                    if (line != null) {
                        val name = line.optString("name").ifBlank { line.optString("id") }
                        if (name.isNotBlank() && name !in lines) lines += name
                        leg.optString("tripId").takeIf { it.isNotBlank() }?.let { tripId ->
                            if (tripId !in tripIds) tripIds += tripId
                        }
                        leg.optJSONArray("stopovers")?.let { stopovers ->
                            for (k in 0 until stopovers.length()) {
                                parseRouteStop(stopovers.optJSONObject(k))?.let(stops::add)
                            }
                        }
                        leg.optString("direction").takeIf { it.isNotBlank() }?.let { direction = it }
                        transitLegs++
                    } else {
                        walkingMinutes += minutesBetween(legDeparture, legArrival)
                    }
                }
                add(
                    RouteOption(
                        id = "journey-$i-$departure-$arrival",
                        origin = origin,
                        destination = destination,
                        line = lines.ifEmpty { listOf("Yürüme") }.joinToString(" → "),
                        direction = direction,
                        departure = departure.ifBlank { "Şimdi" },
                        arrival = arrival.ifBlank { "—" },
                        walkingMinutes = walkingMinutes,
                        transfers = (transitLegs - 1).coerceAtLeast(0),
                        tripIds = tripIds,
                        stops = stops.distinctBy { stop ->
                            stop.id ?: "${stop.name}:${stop.plannedDeparture.orEmpty()}:${stop.plannedArrival.orEmpty()}"
                        },
                        refreshToken = journey.optString("refreshToken").takeIf { it.isNotBlank() },
                        sourceUpdatedAtEpochSeconds = nowEpochSeconds(),
                    )
                )
            }
        }
    }

    internal fun parseRouteStop(stopover: JSONObject?): RouteStop? {
        val item = stopover ?: return null
        val stop = item.optJSONObject("stop") ?: return null
        val name = stop.optString("name").trim()
        if (name.isBlank()) return null
        val location = stop.optJSONObject("location")
        val latitude = location?.optDouble("latitude", Double.NaN)?.takeIf { it.isFinite() }
        val longitude = location?.optDouble("longitude", Double.NaN)?.takeIf { it.isFinite() }
        return RouteStop(
            id = stop.optString("id").takeIf { it.isNotBlank() },
            name = name,
            latitude = latitude,
            longitude = longitude,
            arrival = item.optString("arrival").takeIf { it.isNotBlank() },
            plannedArrival = item.optString("plannedArrival").takeIf { it.isNotBlank() },
            departure = item.optString("departure").takeIf { it.isNotBlank() },
            plannedDeparture = item.optString("plannedDeparture").takeIf { it.isNotBlank() },
        )
    }

    private fun displayClock(value: String): String =
        value.substringAfter('T', value).take(5).ifBlank { value }

    private fun minutesBetween(start: String, end: String): Int {
        fun clock(value: String): Int? {
            val time = value.substringAfter('T', value)
            val hour = time.take(2).toIntOrNull() ?: return null
            val minute = time.drop(3).take(2).toIntOrNull() ?: return null
            return hour * 60 + minute
        }
        val a = clock(start) ?: return 0
        val b = clock(end) ?: return 0
        val delta = if (b >= a) b - a else b + 24 * 60 - a
        return delta.coerceIn(0, 240)
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

    internal fun parseLocations(array: JSONArray, limit: Int): List<TransitLocationResult> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val type = item.optString("type").lowercase(Locale.ROOT)
            val location = item.optJSONObject("location") ?: item
            val latitude = location.optDouble("latitude", Double.NaN)
            val longitude = location.optDouble("longitude", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite()) continue

            val rawName = item.optString("name").trim()
            val rawAddress = item.optString("address").trim()
                .ifBlank { location.optString("address").trim() }
            val kind = when (type) {
                "stop", "station" -> TransitLocationKind.STOP
                "poi" -> TransitLocationKind.POI
                "address" -> TransitLocationKind.ADDRESS
                "location" -> if (item.optBoolean("poi", false)) TransitLocationKind.POI else TransitLocationKind.ADDRESS
                else -> continue
            }
            val label = rawName.ifBlank { rawAddress }.ifBlank { continue }
            val providerId = item.optString("id").ifBlank { location.optString("id") }
            val stop = if (kind == TransitLocationKind.STOP && providerId.isNotBlank()) {
                CatalogStop("db:$providerId", "db-live", label, latitude, longitude)
            } else null
            val key = when {
                stop != null -> "stop:${stop.id}"
                providerId.isNotBlank() -> "${kind.name.lowercase(Locale.ROOT)}:$providerId"
                else -> "${kind.name.lowercase(Locale.ROOT)}:$label:$latitude:$longitude"
            }
            add(
                TransitLocationResult(
                    key = key,
                    kind = kind,
                    label = label,
                    latitude = latitude,
                    longitude = longitude,
                    stop = stop,
                )
            )
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
            callback(Result.failure(SecurityException(context.getString(R.string.location_permission_required))))
            return
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            callback(Result.failure(IllegalStateException(context.getString(R.string.location_service_off))))
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(providers.first(), null, context.mainExecutor) { location ->
                    callback(location?.toGeoPoint()?.let { Result.success(it) }
                        ?: Result.failure(IllegalStateException(context.getString(R.string.current_location_unavailable))))
                }
            } else {
                val best = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                    .maxByOrNull { it.time }
                main.post {
                    callback(best?.toGeoPoint()?.let { Result.success(it) }
                        ?: Result.failure(IllegalStateException(context.getString(R.string.location_not_ready))))
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
