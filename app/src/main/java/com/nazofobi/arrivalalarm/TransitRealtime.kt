package com.nazofobi.arrivalalarm

import org.json.JSONArray
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

// GTFS-RT Delay is a signed integer. optInt() silently coerces strings,
// booleans and malformed data to zero, falsely claiming on-time evidence.
private fun JSONObject.firstValue(vararg keys: String): Any? {
    for (key in keys) {
        if (has(key)) return opt(key)
    }
    return null
}

private fun JSONObject.firstObject(vararg keys: String): JSONObject? {
    for (key in keys) {
        optJSONObject(key)?.let { return it }
    }
    return null
}

private fun JSONObject.firstArray(vararg keys: String): JSONArray? {
    for (key in keys) {
        optJSONArray(key)?.let { return it }
    }
    return null
}

private fun JSONObject.firstNonBlankString(vararg keys: String): String? {
    for (key in keys) {
        val value = firstValue(key) as? String ?: continue
        if (value.isNotBlank()) return value
    }
    return null
}

private fun parseProviderEpochSeconds(value: Any?): Long? {
    val timestamp = when (value) {
        is Int -> value.toLong()
        is Long -> value
        // ProtoJSON represents uint64 as a decimal string. Accept only canonical
        // ASCII decimal digits; reject signs, whitespace, fractions and overflow.
        is String -> value.takeIf { text ->
            text.isNotEmpty() && text.all { digit -> digit in '0'..'9' }
        }?.toLongOrNull()
        else -> null
    }
    return timestamp?.takeIf { it > 0L }
}

private fun parseRealtimeDelay(event: JSONObject?): Int? {
    val value = event?.firstValue("Delay", "delay") ?: return null
    return when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        else -> null
    }
}

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
        val header = root.firstObject("Header", "header") ?: return null
        // VBN's published sample uses PascalCase and a JSON number; the live endpoint
        // also emits canonical ProtoJSON lowerCamelCase with uint64 as a decimal string.
        val timestamp = parseProviderEpochSeconds(header.firstValue("Timestamp", "timestamp")) ?: return null
        val entities = root.firstArray("Entity", "entity") ?: return RealtimeSnapshot(timestamp, emptyList())
        val updates = buildList {
            for (i in 0 until entities.length()) {
                val tripUpdate = entities.optJSONObject(i)?.firstObject("TripUpdate", "tripUpdate") ?: continue
                val trip = tripUpdate.firstObject("Trip", "trip") ?: continue
                val tripId = trip.firstNonBlankString("TripId", "tripId") ?: continue
                val relationship = trip.firstNonBlankString("ScheduleRelationship", "scheduleRelationship")
                val cancelled = relationship.equals("Canceled", true) ||
                    relationship.equals("Cancelled", true)
                val stops = tripUpdate.firstArray("StopTimeUpdate", "stopTimeUpdate")
                var delay: Int? = null
                var platform: String? = null
                if (stops != null) {
                    for (j in 0 until stops.length()) {
                        val stop = stops.optJSONObject(j) ?: continue
                        if (delay == null) {
                            val departure = stop.firstObject("Departure", "departure")
                            val arrival = stop.firstObject("Arrival", "arrival")
                            delay = parseRealtimeDelay(departure) ?: parseRealtimeDelay(arrival)
                        }
                        if (platform == null) {
                            platform = stop.firstNonBlankString("StopPlatform", "stopPlatform", "Platform", "platform")
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
        val now = clock.nowEpochSeconds()
        val snapshotTime = snapshot.fetchedAtEpochSeconds
        // A provider clock far ahead of the device does not constitute fresh evidence.
        // Keep the static journey without applying untrusted delay/cancellation updates.
        if (now < 0L || snapshotTime <= 0L ||
            (snapshotTime > now && snapshotTime - now > MAX_PROVIDER_FUTURE_SKEW_SECONDS)
        ) {
            return TransitPlanWithRealtime(staticJourney, TransitRealtimeOverlay(
                RealtimeFreshness.UNAVAILABLE, status = "Canlı veri kullanılamıyor • statik rota"))
        }
        val age = (now - snapshotTime).coerceAtLeast(0L)
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
