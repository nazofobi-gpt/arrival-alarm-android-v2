package com.nazofobi.arrivalalarm

/** Provider capabilities are explicit so unsupported realtime data is never fabricated. */
data class TransitProviderCapabilities(
    val realtimeDepartures: Boolean = false,
    val serviceAlerts: Boolean = false,
    val liveVehiclePositions: Boolean = false,
    val accessibility: Boolean = false
)

data class Departure(
    val tripId: String,
    val line: String,
    val direction: String,
    val scheduledEpochSeconds: Long,
    val realtimeEpochSeconds: Long? = null,
    val cancelled: Boolean = false,
    val platform: String? = null
) {
    val effectiveEpochSeconds: Long get() = realtimeEpochSeconds ?: scheduledEpochSeconds
    val isRealtime: Boolean get() = realtimeEpochSeconds != null
}

data class ServiceAlert(val id: String, val title: String, val detail: String)
data class LiveVehicle(val tripId: String, val point: GeoPoint)
data class AccessibilityPreference(val stepFreeOnly: Boolean = false, val extraTransferMinutes: Int = 0)

data class TransitExperienceSnapshot(
    val departures: List<Departure>,
    val alerts: List<ServiceAlert>,
    val vehicles: List<LiveVehicle>,
    val sourceLabel: String,
    val isOfflineCache: Boolean
)

class TransitExperienceEngine(private val capabilities: TransitProviderCapabilities) {
    fun departureBoard(departures: List<Departure>, nowEpochSeconds: Long, limit: Int = 8): List<Departure> =
        departures.asSequence()
            .map { if (capabilities.realtimeDepartures) it else it.copy(realtimeEpochSeconds = null) }
            .filterNot { it.cancelled }
            .filter { it.effectiveEpochSeconds >= nowEpochSeconds }
            .sortedBy { it.effectiveEpochSeconds }
            .take(limit.coerceAtLeast(0))
            .toList()

    fun alerts(values: List<ServiceAlert>): List<ServiceAlert> = if (capabilities.serviceAlerts) values else emptyList()
    fun vehicles(values: List<LiveVehicle>): List<LiveVehicle> = if (capabilities.liveVehiclePositions) values else emptyList()

    fun alternateDepartures(values: List<Departure>, selectedTripId: String, nowEpochSeconds: Long): List<Departure> =
        departureBoard(values, nowEpochSeconds).filterNot { it.tripId == selectedTripId }

    fun walkingTransferMinutes(baseMinutes: Int, preference: AccessibilityPreference): Int =
        (baseMinutes + if (preference.stepFreeOnly && capabilities.accessibility) preference.extraTransferMinutes else 0).coerceAtLeast(0)
}

/** Small deterministic store contract: Android persistence can replace this without changing UX semantics. */
class TransitRecentsStore(
    private val maxItems: Int = 8,
    favoriteIds: Collection<String> = emptyList(),
    recentTripIds: Collection<String> = emptyList(),
    private val onChanged: ((favorites: List<String>, recents: List<String>) -> Unit)? = null,
) {
    private val favorites = linkedSetOf<String>().apply { addAll(favoriteIds) }
    private val recents = ArrayDeque<String>().apply {
        recentTripIds.take(maxItems.coerceAtLeast(0)).forEach { addLast(it) }
    }

    fun isFavorite(stopId: String): Boolean = stopId in favorites

    fun toggleFavorite(stopId: String): Boolean {
        if (!favorites.add(stopId)) favorites.remove(stopId)
        publish()
        return stopId in favorites
    }

    fun favorites(): List<String> = favorites.toList()

    fun recordTrip(tripId: String) {
        recents.remove(tripId)
        recents.addFirst(tripId)
        while (recents.size > maxItems.coerceAtLeast(0)) recents.removeLast()
        publish()
    }

    fun recents(): List<String> = recents.toList()

    private fun publish() {
        onChanged?.invoke(favorites(), recents())
    }
}
