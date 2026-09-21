package com.nazofobi.arrivalalarm

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** VBN GTFS-Realtime public feed metadata, verified against the provider developer page. */
object VbnRealtimeProvenance {
    const val JSON_URL = "http://gtfsr.vbn.de/gtfsr_connect.json"
    const val PROTOBUF_URL = "http://gtfsr.vbn.de/gtfsr_connect.bin"
    const val LICENSE = "CC BY-SA 4.0"
    const val PROVIDER = "Verkehrsverbund Bremen/Niedersachsen (VBN)"
}

enum class RealtimeFreshness { FRESH, STALE, UNAVAILABLE }

data class RealtimeUpdate(
    val tripId: String,
    val delaySeconds: Int? = null,
    val cancelled: Boolean = false,
    val platform: String? = null,
)

data class RealtimeSnapshot(val fetchedAtEpochSeconds: Long, val updates: List<RealtimeUpdate>)

interface RealtimeTransitSource { fun fetch(): RealtimeSnapshot? }
fun interface EpochClock { fun nowEpochSeconds(): Long }

/** Concrete, bounded adapter for the documented VBN JSON GTFS-RT shape. */
class VbnJsonRealtimeSource(
    private val endpoint: String = VbnRealtimeProvenance.JSON_URL,
    private val connectTimeoutMs: Int = 4_000,
    private val readTimeoutMs: Int = 4_000,
    private val loader: ((String) -> String)? = null,
) : RealtimeTransitSource {
    override fun fetch(): RealtimeSnapshot? = runCatching { parse(load(endpoint)) }.getOrNull()

    internal fun parse(json: String): RealtimeSnapshot? {
        val root = JSONObject(json)
        val timestamp = root.optJSONObject("Header")?.optLong("Timestamp", -1L) ?: -1L
        if (timestamp <= 0L) return null
        val entities = root.optJSONArray("Entity") ?: return RealtimeSnapshot(timestamp, emptyList())
        val updates = buildList {
            for (i in 0 until entities.length()) {
                val tripUpdate = entities.optJSONObject(i)?.optJSONObject("TripUpdate") ?: continue
                val trip = tripUpdate.optJSONObject("Trip") ?: continue
                val tripId = trip.optString("TripId").takeIf { it.isNotBlank() } ?: continue
                val cancelled = trip.optString("ScheduleRelationship").equals("Canceled", true) ||
                    trip.optString("ScheduleRelationship").equals("Cancelled", true)
                val stops = tripUpdate.optJSONArray("StopTimeUpdate")
                var delay: Int? = null
                var platform: String? = null
                if (stops != null) {
                    for (j in 0 until stops.length()) {
                        val stop = stops.optJSONObject(j) ?: continue
                        if (delay == null) {
                            val departure = stop.optJSONObject("Departure")
                            val arrival = stop.optJSONObject("Arrival")
                            delay = when {
                                departure?.has("Delay") == true -> departure.optInt("Delay")
                                arrival?.has("Delay") == true -> arrival.optInt("Delay")
                                else -> null
                            }
                        }
                        if (platform == null) {
                            platform = stop.optString("StopPlatform", "").takeIf { it.isNotBlank() }
                                ?: stop.optString("Platform", "").takeIf { it.isNotBlank() }
                        }
                    }
                }
                add(RealtimeUpdate(tripId, delay, cancelled, platform))
            }
        }
        return RealtimeSnapshot(timestamp, updates)
    }

    private fun load(url: String): String = loader?.invoke(url) ?: (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        requestMethod = "GET"
        useCaches = false
        try {
            if (responseCode !in 200..299) error("HTTP $responseCode")
            inputStream.bufferedReader().use { it.readText() }
        } finally { disconnect() }
    }
}

data class TransitRealtimeOverlay(
    val freshness: RealtimeFreshness,
    val lastUpdatedEpochSeconds: Long? = null,
    val delaySeconds: Int? = null,
    val cancelled: Boolean = false,
    val platform: String? = null,
    val status: String,
)

data class TransitPlanWithRealtime(val journey: TransitJourney, val realtime: TransitRealtimeOverlay)

class OverlayTransitRepository(
    private val staticRepository: StaticTransitRepository,
    private val realtimeSource: RealtimeTransitSource,
    private val clock: EpochClock,
    private val staleAfterSeconds: Long = 180,
) {
    fun plan(startId: String, destinationId: String): TransitPlanWithRealtime? {
        val staticJourney = staticRepository.plan(startId, destinationId) ?: return null
        val snapshot = runCatching { realtimeSource.fetch() }.getOrNull()
            ?: return TransitPlanWithRealtime(staticJourney, TransitRealtimeOverlay(
                RealtimeFreshness.UNAVAILABLE, status = "Canlı veri kullanılamıyor • statik rota"))
        val age = (clock.nowEpochSeconds() - snapshot.fetchedAtEpochSeconds).coerceAtLeast(0)
        if (age > staleAfterSeconds) return TransitPlanWithRealtime(staticJourney, TransitRealtimeOverlay(
            RealtimeFreshness.STALE, snapshot.fetchedAtEpochSeconds, status = "Canlı veri eski • statik rota"))
        val exactTripId = staticJourney.legs.singleOrNull()?.line?.takeIf { it.startsWith("gtfs-trip:") }?.removePrefix("gtfs-trip:")
        val update = exactTripId?.let { id -> snapshot.updates.firstOrNull { it.tripId == id } }
        return TransitPlanWithRealtime(staticJourney, TransitRealtimeOverlay(
            freshness = RealtimeFreshness.FRESH,
            lastUpdatedEpochSeconds = snapshot.fetchedAtEpochSeconds,
            delaySeconds = update?.delaySeconds,
            cancelled = update?.cancelled ?: false,
            platform = update?.platform,
            status = when {
                update == null -> "Canlı veri güncel • eşleşen sefer yok • statik rota"
                update.cancelled -> "Sefer iptal bilgisi • statik rota korunuyor"
                update.delaySeconds != null -> "Canlı gecikme: ${update.delaySeconds / 60} dk"
                update.platform != null -> "Canlı peron: ${update.platform}"
                else -> "Canlı veri güncel"
            },
        ))
    }
}
