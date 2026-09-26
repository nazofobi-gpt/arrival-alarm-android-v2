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

    init {
        backingStore.load().takeLast(maxEntries.coerceAtLeast(1)).forEach { plan ->
            entries[plan.key] = plan
        }
    }

    fun put(plan: CachedTransitPlan) {
        entries.remove(plan.key)
        entries[plan.key] = plan
        while (entries.size > maxEntries.coerceAtLeast(1)) entries.remove(entries.keys.first())
        backingStore.save(entries.values.toList())
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
