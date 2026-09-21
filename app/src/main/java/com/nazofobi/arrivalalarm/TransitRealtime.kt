package com.nazofobi.arrivalalarm

/**
 * VBN realtime provenance resolved 2026-09-21 from the VBN developer information page.
 * The VBN publishes GTFS-Realtime every ~60 s under CC BY-SA 4.0. The documented
 * direct feeds are intentionally metadata here: transport stays behind RealtimeTransitSource.
 */
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
    /** Null means the feed/adapter supplied no supported platform update. */
    val platform: String? = null,
)

data class RealtimeSnapshot(
    val fetchedAtEpochSeconds: Long,
    val updates: List<RealtimeUpdate>,
)

interface RealtimeTransitSource {
    /** Returns null for timeout, transport failure, auth failure, or unusable schema. */
    fun fetch(): RealtimeSnapshot?
}

fun interface EpochClock { fun nowEpochSeconds(): Long }

data class TransitRealtimeOverlay(
    val freshness: RealtimeFreshness,
    val lastUpdatedEpochSeconds: Long? = null,
    val delaySeconds: Int? = null,
    val cancelled: Boolean = false,
    val platform: String? = null,
    val status: String,
)

data class TransitPlanWithRealtime(
    val journey: TransitJourney,
    val realtime: TransitRealtimeOverlay,
)

/**
 * Static itinerary is always the source of truth. Realtime may annotate it, never replace it.
 * A missing/failed/stale source therefore cannot remove a usable static journey.
 */
class OverlayTransitRepository(
    private val staticRepository: StaticTransitRepository,
    private val realtimeSource: RealtimeTransitSource,
    private val clock: EpochClock,
    private val staleAfterSeconds: Long = 180,
) {
    fun plan(startId: String, destinationId: String): TransitPlanWithRealtime? {
        val staticJourney = staticRepository.plan(startId, destinationId) ?: return null
        val snapshot = runCatching { realtimeSource.fetch() }.getOrNull()
            ?: return TransitPlanWithRealtime(
                staticJourney,
                TransitRealtimeOverlay(
                    freshness = RealtimeFreshness.UNAVAILABLE,
                    status = "Canlı veri kullanılamıyor • statik rota",
                ),
            )

        val age = (clock.nowEpochSeconds() - snapshot.fetchedAtEpochSeconds).coerceAtLeast(0)
        if (age > staleAfterSeconds) {
            return TransitPlanWithRealtime(
                staticJourney,
                TransitRealtimeOverlay(
                    freshness = RealtimeFreshness.STALE,
                    lastUpdatedEpochSeconds = snapshot.fetchedAtEpochSeconds,
                    status = "Canlı veri eski • statik rota",
                ),
            )
        }

        // The authored static fixture has no production GTFS trip id yet. An overlay is only
        // applied when a caller can supply an exact matching id; never guess from line names.
        val exactTripId = staticJourney.legs.singleOrNull()?.let { leg ->
            if (leg.line.startsWith("gtfs-trip:")) leg.line.removePrefix("gtfs-trip:") else null
        }
        val update = exactTripId?.let { id -> snapshot.updates.firstOrNull { it.tripId == id } }
        return TransitPlanWithRealtime(
            staticJourney,
            TransitRealtimeOverlay(
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
            ),
        )
    }
}
