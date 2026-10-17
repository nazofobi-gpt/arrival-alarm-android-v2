package com.nazofobi.arrivalalarm

/**
 * Provider-neutral last-known-good cache for G-140. The cache never upgrades stale data to realtime:
 * consumers receive isOfflineCache=true and scheduled times remain the fallback truth.
 */
data class CachedTransitPlan(
    val key: String,
    val routeOptions: List<RouteOption>,
    val departures: List<Departure>,
    val savedAtEpochSeconds: Long,
)

interface TransitCacheBackingStore {
    fun load(): List<CachedTransitPlan>
    fun save(plans: List<CachedTransitPlan>)
}

object NoOpTransitCacheBackingStore : TransitCacheBackingStore {
    override fun load(): List<CachedTransitPlan> = emptyList()
    override fun save(plans: List<CachedTransitPlan>) = Unit
}

class OfflineTransitCache(
    private val maxEntries: Int = 8,
    private val backingStore: TransitCacheBackingStore = NoOpTransitCacheBackingStore,
) {
    private val entries = LinkedHashMap<String, CachedTransitPlan>()
    // A zero or negative capacity must disable retention, not silently keep one trip.
    private val capacity = maxEntries.coerceAtLeast(0)

    init {
        // Persisted records may predate the realtime-stripping write contract.
        // Never reintroduce live cancellation, platform or ETA data after process restore.
        try {
            backingStore.load().takeLast(capacity).forEach { plan ->
                val safe = plan.forOfflineCache()
                entries[safe.key] = safe
            }
        } catch (_: RuntimeException) {
            // Corrupt or unavailable disk state must not crash offline routing.
            // Reject the entire partial restore rather than expose unverified entries.
            entries.clear()
        }
    }

    fun put(plan: CachedTransitPlan) {
        val cacheSafe = plan.forOfflineCache()
        entries.remove(cacheSafe.key)
        entries[cacheSafe.key] = cacheSafe
        while (entries.size > capacity) entries.remove(entries.keys.first())
        try {
            backingStore.save(entries.values.toList())
        } catch (_: RuntimeException) {
            // Retain a scheduled-only fallback for this session even if disk is full.
            // Persistence is not guaranteed after a failed save.
        }
    }

    fun get(key: String): CachedTransitPlan? = entries[key]

    fun snapshot(key: String, sourceLabel: String = "Offline cache"): TransitExperienceSnapshot? =
        entries[key]?.let { plan ->
            TransitExperienceSnapshot(
                departures = plan.departures.map { it.copy(realtimeEpochSeconds = null) },
                alerts = emptyList(),
                vehicles = emptyList(),
                sourceLabel = sourceLabel,
                isOfflineCache = true,
            )
        }

    fun routeOptions(key: String): List<RouteOption> = entries[key]?.routeOptions.orEmpty()
}

/** Normalize both freshly saved and legacy restored entries to scheduled-only evidence. */
private fun CachedTransitPlan.forOfflineCache(): CachedTransitPlan =
    copy(
        routeOptions = routeOptions.map { it.forOfflineCache() },
        departures = departures.map { departure ->
            departure.copy(
                realtimeEpochSeconds = null,
                cancelled = false,
                platform = null,
                realtimeTripStartDate = null,
                realtimeTripStartTime = null,
            )
        },
    )

private fun RouteOption.forOfflineCache(): RouteOption =
    // Legacy JSON stores actual stop times/platforms but omits RouteRealtimeInfo.
    // The planned fields remain as the authoritative scheduled-only fallback.
    if (realtime == null && stops.none {
            it.arrival != null || it.departure != null || it.platform != null
        }
    ) {
        this
    } else {
        copy(
            stops = stops.map { stop ->
                stop.copy(
                    arrival = null,
                    departure = null,
                    platform = null,
                )
            },
            realtime = null,
        )
    }
