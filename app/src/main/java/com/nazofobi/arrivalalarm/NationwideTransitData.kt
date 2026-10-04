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
    val realtimeState: StationRealtimeState? = null,
)

sealed interface NationwideLookupResolution<out T> {
    data class Results<T>(
        val values: List<T>,
        val sourceLabel: String,
        val freshness: TransitProviderFreshness? = null,
    ) : NationwideLookupResolution<T>

    data class NoResult(
        val sourceLabel: String? = null,
        val freshness: TransitProviderFreshness? = null,
    ) : NationwideLookupResolution<Nothing>

    data class Stale<T>(
        val values: List<T>,
        val sourceLabel: String,
        val freshness: TransitProviderFreshness,
    ) : NationwideLookupResolution<T>

    data class ProviderUnavailable(
        val reason: TransitProviderUnavailableReason,
        val retryable: Boolean,
        val sourceLabel: String? = null,
    ) : NationwideLookupResolution<Nothing>
}

enum class NationwideJourneySource {
    LOCAL_STATIC,
    LIVE_FALLBACK,
}

sealed interface NationwideJourneyResolution {
    data class Results(
        val options: List<RouteOption>,
        val source: NationwideJourneySource,
    ) : NationwideJourneyResolution

    data class NoRoute(
        val localOutcome: LocalStaticJourneyOutcome,
    ) : NationwideJourneyResolution

    data class ProviderUnavailable(
        val localOutcome: LocalStaticJourneyOutcome,
        val message: String,
    ) : NationwideJourneyResolution
}

class NationwideTransitGateway(
    context: Context,
    private val feedUrl: String = "https://download.gtfs.de/germany/free/latest.zip",
    private val liveApi: GermanyLiveTransitApi = GermanyLiveTransitApi(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val liveJourneyLoader: ((MapPoint, MapPoint, Int) -> List<RouteOption>)? = null,
    private val realtimeFetch: (() -> GermanyRealtimeFetchResult)? = null,
) {
    private val appContext = context.applicationContext
    private val store = NationwideTransitIndex(appContext)
    private val staticJourneyPlanner = ProductionStaticJourneyPlanner(
        index = store,
        nowMillis = nowMillis,
    )
    private val realtimeClient = GermanyGtfsRealtimeClient(
        clock = EpochClock { nowMillis() / 1_000L },
    )
    private val realtimeScheduleRepository = GtfsScheduleRepository(store)
    private val realtimeMatcher = GermanyRealtimeMatcher(
        RepositoryGermanyRealtimeStaticData(realtimeScheduleRepository),
    )
    private val realtimeJourneyOverlay = ProductionRealtimeJourneyOverlay(
        fetch = { realtimeFetch?.invoke() ?: realtimeClient.fetch() },
        match = { result, now -> realtimeMatcher.match(result, now) },
        nowEpochSeconds = { nowMillis() / 1_000L },
        isFrequencyTrip = { tripId ->
            realtimeScheduleRepository.frequenciesForTrip(tripId, 1).isNotEmpty()
        },
    )
    private val stationDeparturesPlanner = ProductionStationDeparturesPlanner(
        index = store,
        nowMillis = nowMillis,
    )
    private val realtimeStationOverlay = ProductionRealtimeStationOverlay(
        fetch = { realtimeFetch?.invoke() ?: realtimeClient.fetch() },
        match = { result, now -> realtimeMatcher.match(result, now) },
        nowEpochSeconds = { nowMillis() / 1_000L },
        isFrequencyTrip = { tripId ->
            realtimeScheduleRepository.frequenciesForTrip(tripId, 1).isNotEmpty()
        },
    )
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
        searchStopsResolved(query, limit) { resolution ->
            when (resolution) {
                is NationwideLookupResolution.Results ->
                    callback(resolution.values, resolution.sourceLabel)
                is NationwideLookupResolution.Stale ->
                    callback(resolution.values, resolution.sourceLabel)
                is NationwideLookupResolution.NoResult ->
                    callback(emptyList(), resolution.sourceLabel)
                is NationwideLookupResolution.ProviderUnavailable ->
                    callback(emptyList(), resolution.sourceLabel)
            }
        }
    }

    internal fun searchStopsResolved(
        query: String,
        limit: Int = 20,
        callback: (NationwideLookupResolution<CatalogStop>) -> Unit,
    ) {
        val q = query.trim()
        if (q.length < 2) {
            callback(NationwideLookupResolution.NoResult())
            return
        }
        io.execute {
            val resolution = resolveSearchStops(q, limit)
            main.post { callback(resolution) }
        }
    }

    internal fun resolveSearchStops(
        query: String,
        limit: Int = 20,
    ): NationwideLookupResolution<CatalogStop> {
        val local = if (store.isReady()) store.search(query, limit) else emptyList()
        if (local.isNotEmpty()) {
            return NationwideLookupResolution.Results(
                values = local,
                sourceLabel = appContext.getString(R.string.source_gtfs_local),
            )
        }
        return liveLookup(liveApi.searchStopsOutcome(query, limit))
    }

    fun searchLocations(
        query: String,
        limit: Int = 20,
        callback: (List<TransitLocationResult>, String?) -> Unit,
    ) {
        searchLocationsResolved(query, limit) { resolution ->
            when (resolution) {
                is NationwideLookupResolution.Results ->
                    callback(resolution.values, resolution.sourceLabel)
                is NationwideLookupResolution.Stale ->
                    callback(resolution.values, resolution.sourceLabel)
                is NationwideLookupResolution.NoResult ->
                    callback(emptyList(), resolution.sourceLabel)
                is NationwideLookupResolution.ProviderUnavailable ->
                    callback(emptyList(), resolution.sourceLabel)
            }
        }
    }

    fun searchLocationsResolved(
        query: String,
        limit: Int = 20,
        callback: (NationwideLookupResolution<TransitLocationResult>) -> Unit,
    ) {
        val q = query.trim()
        if (q.length < 2) {
            callback(NationwideLookupResolution.NoResult())
            return
        }
        io.execute {
            val resolution = resolveSearchLocations(q, limit)
            main.post { callback(resolution) }
        }
    }

    internal fun resolveSearchLocations(
        query: String,
        limit: Int = 20,
    ): NationwideLookupResolution<TransitLocationResult> {
        val local = if (store.isReady()) {
            store.search(query, limit).map(::localLocation)
        } else {
            emptyList()
        }
        val live = liveApi.searchLocationsOutcome(query, limit)

        if (local.isNotEmpty()) {
            return when (live) {
                is TransitProviderOutcome.Results -> NationwideLookupResolution.Results(
                    values = mergeLocations(local, live.value, limit),
                    sourceLabel =
                        appContext.getString(R.string.source_gtfs_local) + " + " +
                            appContext.getString(R.string.source_live_germany),
                    freshness = live.freshness,
                )
                else -> NationwideLookupResolution.Results(
                    values = local,
                    sourceLabel = appContext.getString(R.string.source_gtfs_local),
                )
            }
        }
        return liveLookup(live)
    }

    fun nearbyStops(latitude: Double, longitude: Double, limit: Int = 8, callback: (List<NearbyStop>, String?) -> Unit) {
        nearbyStopsResolved(latitude, longitude, limit) { resolution ->
            when (resolution) {
                is NationwideLookupResolution.Results ->
                    callback(resolution.values, resolution.sourceLabel)
                is NationwideLookupResolution.Stale ->
                    callback(resolution.values, resolution.sourceLabel)
                is NationwideLookupResolution.NoResult ->
                    callback(emptyList(), resolution.sourceLabel)
                is NationwideLookupResolution.ProviderUnavailable ->
                    callback(emptyList(), resolution.sourceLabel)
            }
        }
    }

    fun nearbyStopsResolved(
        latitude: Double,
        longitude: Double,
        limit: Int = 8,
        callback: (NationwideLookupResolution<NearbyStop>) -> Unit,
    ) {
        io.execute {
            val resolution = resolveNearbyStops(latitude, longitude, limit)
            main.post { callback(resolution) }
        }
    }

    internal fun resolveNearbyStops(
        latitude: Double,
        longitude: Double,
        limit: Int = 8,
    ): NationwideLookupResolution<NearbyStop> {
        val local = if (store.isReady()) store.nearest(latitude, longitude, limit) else emptyList()
        if (local.isNotEmpty()) {
            return NationwideLookupResolution.Results(
                values = local,
                sourceLabel = appContext.getString(R.string.source_gtfs_local),
            )
        }
        return liveLookup(liveApi.nearbyStopsOutcome(latitude, longitude, limit))
    }

    private fun localLocation(stop: CatalogStop) = TransitLocationResult(
        key = "stop:${stop.id}",
        kind = TransitLocationKind.STOP,
        label = stop.name,
        latitude = stop.latitude,
        longitude = stop.longitude,
        stop = stop,
    )

    private fun mergeLocations(
        local: List<TransitLocationResult>,
        live: List<TransitLocationResult>,
        limit: Int,
    ): List<TransitLocationResult> =
        (local + live)
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

    private fun <T> liveLookup(
        outcome: TransitProviderOutcome<List<T>>,
    ): NationwideLookupResolution<T> = when (outcome) {
        is TransitProviderOutcome.Results -> NationwideLookupResolution.Results(
            values = outcome.value,
            sourceLabel = appContext.getString(R.string.source_live_germany),
            freshness = outcome.freshness,
        )
        is TransitProviderOutcome.NoResult -> NationwideLookupResolution.NoResult(
            sourceLabel = appContext.getString(R.string.source_live_germany),
            freshness = outcome.freshness,
        )
        is TransitProviderOutcome.Stale -> NationwideLookupResolution.Stale(
            values = outcome.value,
            sourceLabel = appContext.getString(R.string.source_live_germany),
            freshness = outcome.freshness,
        )
        is TransitProviderOutcome.Unavailable -> NationwideLookupResolution.ProviderUnavailable(
            reason = outcome.reason,
            retryable = outcome.retryable,
            sourceLabel = outcome.provenance?.sourceLabel
                ?: appContext.getString(R.string.source_live_germany),
        )
    }

    fun stationDepartures(
        stop: CatalogStop,
        limit: Int = 8,
        callback: (StationDeparturesSnapshot) -> Unit,
    ) {
        io.execute {
            val snapshot = resolveStationDepartures(stop, limit)
            main.post { callback(snapshot) }
        }
    }

    internal fun resolveStationDepartures(
        stop: CatalogStop,
        limit: Int = 8,
    ): StationDeparturesSnapshot {
        val local = if (store.isReady() && !stop.id.startsWith("db:")) {
            runCatching {
                stationDeparturesPlanner.plan(stop.id, limit)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (local.isNotEmpty()) {
            val enriched = realtimeStationOverlay.enrich(stop.id, local)
            return StationDeparturesSnapshot(
                departures = enriched.departures,
                alerts = enriched.alerts,
                sourceLabel = localStationDepartureSource(enriched.realtimeState),
                fetchedAtEpochSeconds = enriched.updatedAtEpochSeconds ?: store.fetchedAt(),
                realtimeState = enriched.realtimeState,
            )
        }

        return runCatching {
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
    }

    fun journeyOptions(
        origin: MapPoint,
        destination: MapPoint,
        limit: Int = 3,
        callback: (List<RouteOption>, String) -> Unit,
    ) {
        io.execute {
            val resolution = resolveJourneyOptions(origin, destination, limit)
            val options = when (resolution) {
                is NationwideJourneyResolution.Results -> resolution.options
                is NationwideJourneyResolution.NoRoute,
                is NationwideJourneyResolution.ProviderUnavailable -> emptyList()
            }
            val status = when (resolution) {
                is NationwideJourneyResolution.Results -> when (resolution.source) {
                    NationwideJourneySource.LOCAL_STATIC ->
                        localJourneyStatus(resolution.options)
                    NationwideJourneySource.LIVE_FALLBACK ->
                        appContext.getString(R.string.route_live_static)
                }
                is NationwideJourneyResolution.NoRoute ->
                    appContext.getString(R.string.route_not_found)
                is NationwideJourneyResolution.ProviderUnavailable ->
                    appContext.getString(
                        R.string.route_live_unavailable,
                        resolution.message,
                    )
            }
            main.post { callback(options, status) }
        }
    }

    internal fun resolveJourneyOptions(
        origin: MapPoint,
        destination: MapPoint,
        limit: Int = 3,
    ): NationwideJourneyResolution {
        val localOutcome = runCatching {
            staticJourneyPlanner.plan(origin, destination, limit)
        }.getOrDefault(LocalStaticJourneyOutcome.Unavailable)

        if (localOutcome is LocalStaticJourneyOutcome.Results) {
            return NationwideJourneyResolution.Results(
                options = realtimeJourneyOverlay.enrich(localOutcome.options),
                source = NationwideJourneySource.LOCAL_STATIC,
            )
        }
        if (localOutcome == LocalStaticJourneyOutcome.SameOrigin) {
            return NationwideJourneyResolution.NoRoute(localOutcome)
        }

        val live = runCatching {
            liveJourneyLoader?.invoke(origin, destination, limit)
                ?: liveApi.journeys(origin, destination, limit)
        }
        if (live.isFailure) {
            return NationwideJourneyResolution.ProviderUnavailable(
                localOutcome = localOutcome,
                message = live.exceptionOrNull()?.message
                    ?: appContext.getString(R.string.unknown_error),
            )
        }
        val options = live.getOrDefault(emptyList())
        return if (options.isNotEmpty()) {
            NationwideJourneyResolution.Results(
                options = options,
                source = NationwideJourneySource.LIVE_FALLBACK,
            )
        } else {
            NationwideJourneyResolution.NoRoute(localOutcome)
        }
    }

    private fun localStationDepartureSource(state: StationRealtimeState?): String =
        when (state) {
            StationRealtimeState.FRESH_MATCHED ->
                appContext.getString(R.string.departures_gtfsrt_fresh)
            StationRealtimeState.FRESH_NO_MATCH ->
                appContext.getString(R.string.departures_gtfsrt_no_match)
            StationRealtimeState.STALE ->
                appContext.getString(R.string.departures_gtfsrt_stale)
            StationRealtimeState.UNAVAILABLE ->
                appContext.getString(R.string.departures_gtfsrt_unavailable)
            null -> appContext.getString(R.string.source_gtfs_local)
        }

    private fun localJourneyStatus(options: List<RouteOption>): String =
        when (options.firstOrNull()?.realtime?.state) {
            RouteRealtimeState.FRESH_MATCHED ->
                appContext.getString(R.string.route_gtfsrt_fresh)
            RouteRealtimeState.FRESH_NO_MATCH ->
                appContext.getString(R.string.route_gtfsrt_no_match)
            RouteRealtimeState.STALE ->
                appContext.getString(R.string.route_gtfsrt_stale)
            RouteRealtimeState.UNAVAILABLE ->
                appContext.getString(R.string.route_gtfsrt_unavailable)
            null -> appContext.getString(R.string.source_gtfs_local)
        }

    fun close() {
        io.shutdownNow()
        store.close()
    }
}

private data class GermanyLiveCachePolicy(
    val noStore: Boolean = false,
    val noCache: Boolean = false,
    val maxAgeMillis: Long = 0L,
)

private data class GermanyLiveCachedResponse(
    val body: String,
    val etag: String?,
    val storedAtMillis: Long,
    val policy: GermanyLiveCachePolicy,
)

class GermanyLiveTransitApi(
    private val baseUrl: String = "https://v6.db.transport.rest",
    private val connectTimeoutMs: Int = BoundedHttpTransport.DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = BoundedHttpTransport.DEFAULT_READ_TIMEOUT_MS,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val nowMillis: () -> Long = { nowEpochSeconds() * 1_000L },
    transport: BoundedHttpTransport? = null,
    private val cacheCapacity: Int = 64,
) {
    private val cache = LinkedHashMap<String, GermanyLiveCachedResponse>(16, 0.75f, true)
    private val httpTransport = transport ?: BoundedHttpTransport(
        exchange = HttpExchange { request, connectTimeout, readTimeout ->
            val connection = URL(request.url).openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.requestMethod = request.method
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val headers = connection.headerFields.entries
                    .mapNotNull { (name, values) -> name?.let { it to values.orEmpty() } }
                    .toMap()
                HttpTransportResponse(status = status, headers = headers, body = body)
            } finally {
                connection.disconnect()
            }
        },
        clock = HttpTransportClock { nowMillis() },
        sleeper = HttpTransportSleeper { delayMillis ->
            if (delayMillis > 0L) Thread.sleep(delayMillis)
        },
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = readTimeoutMs,
    )

    init {
        require(cacheCapacity in 1..256)
    }
    fun searchStops(query: String, limit: Int): List<CatalogStop> =
        freshList(searchStopsOutcome(query, limit))

    internal fun searchStopsOutcome(
        query: String,
        limit: Int,
    ): TransitProviderOutcome<List<CatalogStop>> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$baseUrl/locations?query=$encoded&results=$limit&stops=true&addresses=false&poi=false"
        return parseListOutcome(fetchJsonOutcome(url)) { body ->
            parseStops(JSONArray(body), limit)
        }
    }

    fun searchLocations(query: String, limit: Int): List<TransitLocationResult> =
        freshList(searchLocationsOutcome(query, limit))

    internal fun searchLocationsOutcome(
        query: String,
        limit: Int,
    ): TransitProviderOutcome<List<TransitLocationResult>> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url =
            "$baseUrl/locations?query=$encoded&results=$limit&stops=true&addresses=true&poi=true&language=de&pretty=false"
        return parseListOutcome(fetchJsonOutcome(url)) { body ->
            parseLocations(JSONArray(body), limit)
        }
    }

    fun nearbyStops(latitude: Double, longitude: Double, limit: Int): List<NearbyStop> =
        freshList(nearbyStopsOutcome(latitude, longitude, limit))

    internal fun nearbyStopsOutcome(
        latitude: Double,
        longitude: Double,
        limit: Int,
    ): TransitProviderOutcome<List<NearbyStop>> {
        val results = max(limit, 12)
        val url =
            "$baseUrl/locations/nearby?latitude=$latitude&longitude=$longitude&results=$results&distance=50000&stops=true&poi=false"
        return parseListOutcome(fetchJsonOutcome(url)) { body ->
            parseStops(JSONArray(body), results)
                .map {
                    NearbyStop(
                        it,
                        haversineMeters(
                            latitude,
                            longitude,
                            it.latitude,
                            it.longitude,
                        ).roundToInt(),
                    )
                }
                .sortedBy { it.distanceMeters }
                .take(limit)
        }
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

    private fun <T> freshList(
        outcome: TransitProviderOutcome<List<T>>,
    ): List<T> = when (outcome) {
        is TransitProviderOutcome.Results -> outcome.value
        is TransitProviderOutcome.NoResult -> emptyList()
        is TransitProviderOutcome.Stale ->
            error("Transit API stale cache (" + (outcome.freshness.ageSeconds ?: 0L) + "s)")
        is TransitProviderOutcome.Unavailable ->
            error(outcome.detail ?: "Transit API unavailable: " + outcome.reason)
    }

    private fun <T> parseListOutcome(
        raw: TransitProviderOutcome<String>,
        parse: (String) -> List<T>,
    ): TransitProviderOutcome<List<T>> = when (raw) {
        is TransitProviderOutcome.Results -> parsedList(
            body = raw.value,
            provenance = raw.provenance,
            freshness = raw.freshness,
            stale = false,
            parse = parse,
        )
        is TransitProviderOutcome.Stale -> parsedList(
            body = raw.value,
            provenance = raw.provenance,
            freshness = raw.freshness,
            stale = true,
            parse = parse,
        )
        is TransitProviderOutcome.NoResult -> raw
        is TransitProviderOutcome.Unavailable -> raw
    }

    private fun <T> parsedList(
        body: String,
        provenance: TransitProviderProvenance,
        freshness: TransitProviderFreshness,
        stale: Boolean,
        parse: (String) -> List<T>,
    ): TransitProviderOutcome<List<T>> {
        val values = runCatching { parse(body) }.getOrElse {
            return TransitProviderOutcome.Unavailable(
                reason = TransitProviderUnavailableReason.INVALID_RESPONSE,
                retryable = false,
                detail = "Transit provider returned invalid JSON",
                provenance = provenance,
            )
        }
        if (stale) {
            return TransitProviderOutcome.Stale(
                value = values,
                provenance = provenance,
                freshness = freshness,
            )
        }
        if (values.isEmpty()) {
            return TransitProviderOutcome.NoResult(
                provenance = provenance,
                freshness = freshness,
            )
        }
        return TransitProviderOutcome.Results(
            value = values,
            provenance = provenance,
            freshness = freshness,
        )
    }

    private fun get(url: String): String = when (val outcome = fetchJsonOutcome(url)) {
        is TransitProviderOutcome.Results -> outcome.value
        is TransitProviderOutcome.Stale ->
            error("Transit API stale cache (" + (outcome.freshness.ageSeconds ?: 0L) + "s)")
        is TransitProviderOutcome.NoResult -> error("Transit API returned no HTTP representation")
        is TransitProviderOutcome.Unavailable ->
            error(outcome.detail ?: "Transit API unavailable: " + outcome.reason)
    }

    @Synchronized
    internal fun fetchJsonOutcome(url: String): TransitProviderOutcome<String> {
        val now = nowMillis()
        val cached = cache[url]
        if (cached != null && isFresh(cached, now)) {
            return freshOutcome(cached.body, cached.storedAtMillis, now)
        }

        val headers = linkedMapOf(
            "Accept" to "application/json",
            "User-Agent" to "ArrivalAlarmAndroid/0.1",
        )
        cached?.etag?.takeIf { it.isNotBlank() }?.let { headers["If-None-Match"] = it }

        return when (
            val result = httpTransport.execute(HttpTransportRequest(url = url, headers = headers))
        ) {
            is HttpTransportResult.Failure -> staleOrUnavailable(
                cached = cached,
                nowMillis = now,
                reason = TransitProviderUnavailableReason.OFFLINE,
                retryable = true,
                detail = result.message ?: result.kind.name,
            )
            is HttpTransportResult.Response -> handleResponse(url, cached, result.response, now)
        }
    }

    private fun handleResponse(
        url: String,
        cached: GermanyLiveCachedResponse?,
        response: HttpTransportResponse,
        now: Long,
    ): TransitProviderOutcome<String> {
        return when {
        response.status == 304 -> {
            val existing = cached ?: return TransitProviderOutcome.Unavailable(
                reason = TransitProviderUnavailableReason.INVALID_RESPONSE,
                retryable = false,
                detail = "HTTP 304 without cached representation",
                provenance = dbProvenance(null),
            )
            val policy = cachePolicy(response.headers, fallback = existing.policy)
            val refreshed = existing.copy(
                etag = firstHeader(response.headers, "ETag") ?: existing.etag,
                storedAtMillis = now,
                policy = policy,
            )
            if (policy.noStore) cache.remove(url) else putCache(url, refreshed)
            freshOutcome(existing.body, now, now)
        }
        response.status == 200 -> {
            val policy = cachePolicy(response.headers)
            val current = GermanyLiveCachedResponse(
                body = response.body,
                etag = firstHeader(response.headers, "ETag"),
                storedAtMillis = now,
                policy = policy,
            )
            if (policy.noStore) cache.remove(url) else putCache(url, current)
            freshOutcome(response.body, now, now)
        }
        response.status == 429 -> staleOrUnavailable(
            cached = cached,
            nowMillis = now,
            reason = TransitProviderUnavailableReason.RATE_LIMITED,
            retryable = true,
            detail = "Transit API HTTP 429",
        )
        response.status in 500..599 -> staleOrUnavailable(
            cached = cached,
            nowMillis = now,
            reason = TransitProviderUnavailableReason.UPSTREAM_ERROR,
            retryable = true,
            detail = "Transit API HTTP " + response.status,
        )
        response.status in 200..299 -> freshOutcome(response.body, now, now)
        else -> TransitProviderOutcome.Unavailable(
            reason = TransitProviderUnavailableReason.INVALID_RESPONSE,
            retryable = false,
            detail = "Transit API HTTP " + response.status,
            provenance = dbProvenance(now),
        )
        }
    }

    private fun staleOrUnavailable(
        cached: GermanyLiveCachedResponse?,
        nowMillis: Long,
        reason: TransitProviderUnavailableReason,
        retryable: Boolean,
        detail: String,
    ): TransitProviderOutcome<String> {
        if (cached == null) {
            return TransitProviderOutcome.Unavailable(
                reason = reason,
                retryable = retryable,
                detail = detail,
                provenance = dbProvenance(null),
            )
        }
        val ageMillis = elapsedMillis(cached.storedAtMillis, nowMillis)
        return TransitProviderOutcome.Stale(
            value = cached.body,
            provenance = dbProvenance(cached.storedAtMillis),
            freshness = TransitProviderFreshness(
                state = TransitProviderFreshnessState.STALE,
                ageSeconds = ageMillis / 1_000L,
                updatedAtEpochSeconds = cached.storedAtMillis / 1_000L,
            ),
        )
    }

    private fun freshOutcome(
        body: String,
        storedAtMillis: Long,
        nowMillis: Long,
    ): TransitProviderOutcome.Results<String> {
        val ageMillis = elapsedMillis(storedAtMillis, nowMillis)
        return TransitProviderOutcome.Results(
            value = body,
            provenance = dbProvenance(storedAtMillis),
            freshness = TransitProviderFreshness(
                state = TransitProviderFreshnessState.FRESH,
                ageSeconds = ageMillis / 1_000L,
                updatedAtEpochSeconds = storedAtMillis / 1_000L,
            ),
        )
    }

    private fun dbProvenance(storedAtMillis: Long?): TransitProviderProvenance =
        TransitProviderProvenance(
            providerId = ArrivalAlarmTransitProviders.DbEnrichment.id,
            sourceLabel = ArrivalAlarmTransitProviders.DbEnrichment.label,
            fetchedAtEpochSeconds = storedAtMillis?.div(1_000L),
        )

    private fun isFresh(entry: GermanyLiveCachedResponse, now: Long): Boolean {
        if (entry.policy.noCache) return false
        val age = elapsedMillis(entry.storedAtMillis, now)
        return entry.policy.maxAgeMillis > age
    }

    private fun elapsedMillis(thenMillis: Long, nowMillis: Long): Long =
        if (nowMillis <= thenMillis) 0L else nowMillis - thenMillis

    private fun putCache(url: String, entry: GermanyLiveCachedResponse) {
        cache[url] = entry
        while (cache.size > cacheCapacity) {
            val iterator = cache.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    private fun cachePolicy(
        headers: Map<String, List<String>>,
        fallback: GermanyLiveCachePolicy? = null,
    ): GermanyLiveCachePolicy {
        val values = headerValues(headers, "Cache-Control")
        if (values.isEmpty()) return fallback ?: GermanyLiveCachePolicy()

        val directives = values
            .flatMap { value -> value.split(',') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val noStore = directives.any { it.equals("no-store", ignoreCase = true) }
        val noCache = directives.any { it.equals("no-cache", ignoreCase = true) }
        val maxAgeRaw = directives
            .filter { it.substringBefore('=').trim().equals("max-age", ignoreCase = true) }
            .map { it.substringAfter('=', "").trim().trim('"') }
        val parsed = maxAgeRaw.map { raw -> raw.toLongOrNull()?.takeIf { it >= 0L } }
        val maxAgeMillis = when {
            maxAgeRaw.isEmpty() -> 0L
            parsed.any { it == null } -> 0L
            parsed.filterNotNull().distinct().size != 1 -> 0L
            else -> saturatingSecondsToMillis(parsed.filterNotNull().single())
        }
        return GermanyLiveCachePolicy(noStore, noCache, maxAgeMillis)
    }

    private fun headerValues(headers: Map<String, List<String>>, name: String): List<String> =
        headers.entries
            .filter { (header, _) -> header.equals(name, ignoreCase = true) }
            .flatMap { it.value }

    private fun firstHeader(headers: Map<String, List<String>>, name: String): String? =
        headerValues(headers, name).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }

    private fun saturatingSecondsToMillis(seconds: Long): Long =
        if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
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
