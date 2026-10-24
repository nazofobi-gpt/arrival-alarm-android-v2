package com.nazofobi.arrivalalarm

/** Provider capabilities are explicit so unsupported realtime data is never fabricated. */
data class TransitProviderCapabilities(
    val realtimeDepartures: Boolean = false,
    val serviceAlerts: Boolean = false,
    val liveVehiclePositions: Boolean = false,
    val accessibility: Boolean = false
)

/** Keep provider clocks inside the finite, non-overflowing display range (UTC year 1..9999). */
internal const val MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS = 253_402_300_799L
internal fun Long.isDisplayableDepartureEpochSeconds(): Boolean =
    this in 1L..MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS

data class Departure(
    val tripId: String,
    val line: String,
    val direction: String,
    val scheduledEpochSeconds: Long,
    val realtimeEpochSeconds: Long? = null,
    val cancelled: Boolean = false,
    val platform: String? = null,
    val routeId: String? = null,
    val realtimeTripStartDate: String? = null,
    val realtimeTripStartTime: String? = null,
    // Only concrete scheduled GTFS departures expose evidence for later user confirmation.
    val gtfsInstance: GtfsTripInstanceIdentity? = null,
    val boardingStopSequence: Int? = null,
) {
    // A non-positive provider timestamp is malformed/unavailable, not a real
    // departure in 1970. Preserve the known scheduled departure instead.
    val effectiveEpochSeconds: Long get() = realtimeEpochSeconds?.takeIf { it.isDisplayableDepartureEpochSeconds() } ?: scheduledEpochSeconds
    val isRealtime: Boolean get() = realtimeEpochSeconds?.isDisplayableDepartureEpochSeconds() == true
}

/** Identity is stable across realtime delay updates and distinguishes frequency instances. */
internal fun Departure.sameScheduledInstanceAs(other: Departure): Boolean =
    tripId == other.tripId &&
        scheduledEpochSeconds == other.scheduledEpochSeconds &&
        routeId == other.routeId &&
        line == other.line &&
        direction == other.direction &&
        realtimeTripStartDate == other.realtimeTripStartDate &&
        realtimeTripStartTime == other.realtimeTripStartTime &&
        gtfsInstance == other.gtfsInstance &&
        boardingStopSequence == other.boardingStopSequence

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
        // An unset device clock cannot establish which scheduled services are upcoming.
        if (!nowEpochSeconds.isDisplayableDepartureEpochSeconds()) emptyList()
        else departures.asSequence()
            .map { if (capabilities.realtimeDepartures) it else it.copy(realtimeEpochSeconds = null) }
            .filterNot { it.cancelled }
            // A selectable service must have a nonblank trip ID, and attached
            // GTFS proof must agree with its visible trip and route identity.
            // Legacy departures without a GTFS instance remain visible.
            .filter { departure ->
                departure.tripId.isNotBlank() &&
                    (departure.gtfsInstance?.let { instance ->
                        instance.tripId == departure.tripId &&
                            (departure.routeId == null || departure.routeId == instance.routeId)
                    } != false)
            }
            // A valid realtime update cannot make an invalid scheduled identity selectable.
            .filter { it.scheduledEpochSeconds.isDisplayableDepartureEpochSeconds() }
            .filter { it.effectiveEpochSeconds >= nowEpochSeconds }
            .sortedBy { it.effectiveEpochSeconds }
            // Identical provider rows must not consume distinct departure slots.
            // Keep differing realtime evidence separate until reconciliation is proven.
            .distinct()
            .take(limit.coerceAtLeast(0))
            .toList()

    fun alerts(values: List<ServiceAlert>): List<ServiceAlert> = if (capabilities.serviceAlerts) values else emptyList()
    fun vehicles(values: List<LiveVehicle>): List<LiveVehicle> {
        if (!capabilities.liveVehiclePositions) return emptyList()
        // A provider capability flag is not proof that every coordinate is
        // usable. Reject corrupt vehicle IDs/positions before map presentation.
        return values.filter { vehicle ->
            vehicle.tripId.isNotBlank() &&
                vehicle.point.latitude.isFinite() &&
                vehicle.point.latitude in -90.0..90.0 &&
                vehicle.point.longitude.isFinite() &&
                vehicle.point.longitude in -180.0..180.0
        }
    }

    /** Exclude only the selected departure, not every service of its GTFS template. */
    fun alternateDepartures(values: List<Departure>, selected: Departure, nowEpochSeconds: Long): List<Departure> =
        departureBoard(values.filterNot { it.sameScheduledInstanceAs(selected) }, nowEpochSeconds)

    fun alternateDepartures(values: List<Departure>, selectedTripId: String, nowEpochSeconds: Long): List<Departure> =
        departureBoard(values.filterNot { it.tripId == selectedTripId }, nowEpochSeconds)

    fun walkingTransferMinutes(baseMinutes: Int, preference: AccessibilityPreference): Int {
        val penalty = if (preference.stepFreeOnly && capabilities.accessibility) {
            preference.extraTransferMinutes.coerceAtLeast(0).toLong()
        } else 0L
        // Saturate before converting: Int overflow must not turn a long transfer into 0 minutes.
        return (baseMinutes.toLong() + penalty).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}

/** Small deterministic store contract: Android persistence can replace this without changing UX semantics. */
class TransitRecentsStore(
    private val maxItems: Int = 8,
    favoriteIds: Collection<String> = emptyList(),
    recentTripIds: Collection<String> = emptyList(),
    private val onChanged: ((favorites: List<String>, recents: List<String>) -> Unit)? = null,
) {
    private val favorites = linkedSetOf<String>().apply { addAll(favoriteIds.filter(String::isNotBlank)) }
    private val recents = ArrayDeque<String>().apply {
        recentTripIds.filter(String::isNotBlank).distinct().take(maxItems.coerceAtLeast(0)).forEach { addLast(it) }
    }

    fun isFavorite(stopId: String): Boolean = stopId.isNotBlank() && stopId in favorites

    fun toggleFavorite(stopId: String): Boolean {
        if (stopId.isBlank()) return false
        if (!favorites.add(stopId)) favorites.remove(stopId)
        publish()
        return stopId in favorites
    }

    fun favorites(): List<String> = favorites.toList()

    fun recordTrip(tripId: String) {
        if (tripId.isBlank()) return
        recents.remove(tripId)
        recents.addFirst(tripId)
        while (recents.size > maxItems.coerceAtLeast(0)) recents.removeLast()
        publish()
    }

    fun recents(): List<String> = recents.toList()

    /** Clear local trip history without changing the user's saved favorite stops. */
    fun clearRecents() {
        if (recents.isEmpty()) return
        recents.clear()
        publish()
    }

    private fun publish() {
        try {
            onChanged?.invoke(favorites(), recents())
        } catch (_: RuntimeException) {
            // Persistence failures must not undo the current-session favorite
            // or recent-trip choice. A later change may retry the full snapshot.
        }
    }
}
