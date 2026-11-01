package com.nazofobi.arrivalalarm

/**
 * A route id is a UI key, not proof that the same physical departure still exists.
 *
 * Retain a selected itinerary after refresh only with matching, source-backed
 * trip IDs, scheduled boarding instant, ordered stop roles and coordinates.
 * Live delay, platform and status changes intentionally do not affect identity;
 * static scheduled stop clocks do.
 * A provider without sufficient evidence requires explicit reselection.
 */
private const val MAX_ROUTE_DEPARTURE_EPOCH_MILLIS = 253_402_300_799_999L

// Provider route, trip and stop identifiers must all be usable UI/identity keys.
// Reject invisible controls even when both snapshots repeat the same bad value.
private fun isUsableIdentityKey(value: String?): Boolean =
    value != null && value.isNotBlank() && value.none { ch ->
        Character.isISOControl(ch) ||
            Character.getType(ch) == Character.FORMAT.toInt()
    }

/** Route identity cannot be proved with timestamps beyond supported UTC year 9999. */
internal fun retainSelectedRouteAfterRefresh(
    selectedRouteId: String?,
    previousOptions: List<RouteOption>,
    updatedOptions: List<RouteOption>,
): String? {
    // A blank UI key is not an identifiable itinerary and must never preserve consent.
    // The route-card filter rejects invisible/control provider keys. A refresh
    // must enforce the same rule or it could silently retain an unsafe choice.
    val id = selectedRouteId?.takeIf(::isUsableIdentityKey) ?: return null
    val previous = previousOptions.singleOrNull { it.id == id } ?: return null
    val updated = updatedOptions.singleOrNull { it.id == id } ?: return null

    if (
        previous.tripIds.isEmpty() ||
        previous.tripIds.any { !isUsableIdentityKey(it) } ||
        previous.tripIds != updated.tripIds
    ) return null

    // GTFS trip_id may recur each service day; trip_id alone is not a trip instance.
    val boardingInstant = previous.departureEpochMillis ?: return null
    if (
        boardingInstant <= 0L ||
        boardingInstant > MAX_ROUTE_DEPARTURE_EPOCH_MILLIS ||
        updated.departureEpochMillis != boardingInstant
    ) return null

    // A departure cannot be the same physical journey if either snapshot places
    // arrival before boarding or beyond the supported UTC epoch range.
    // Realtime arrival changes after boarding are still allowed.
    if (listOf(previous, updated).any { option ->
            option.arrivalEpochMillis?.let { arrival ->
                arrival < boardingInstant || arrival > MAX_ROUTE_DEPARTURE_EPOCH_MILLIS
            } == true
        }) return null

    // Matching bad coordinates are not physical trip identity evidence.
    if (listOf(previous, updated).any { option ->
            !knownCoordinatesAreValid(option.origin.latitude, option.origin.longitude) ||
                !knownCoordinatesAreValid(option.destination.latitude, option.destination.longitude) ||
                option.stops.any { !knownCoordinatesAreValid(it.latitude, it.longitude) }
        }) return null

    if (
        previous.origin.latitude != updated.origin.latitude ||
        previous.origin.longitude != updated.origin.longitude ||
        previous.destination.latitude != updated.destination.latitude ||
        previous.destination.longitude != updated.destination.longitude
    ) return null

    // No ordered physical stops means there is no verified stop-role identity.
    // Even matching route IDs and trip IDs must not silently preserve selection.
    if (previous.stops.isEmpty() || updated.stops.isEmpty()) return null
    if (previous.stops.size != updated.stops.size) return null
    if (previous.stops.any { !isUsableIdentityKey(it.id) } ||
        updated.stops.any { !isUsableIdentityKey(it.id) }) {
        return null
    }
    if (previous.stops.indices.any { index ->
            val old = previous.stops[index]
            val now = updated.stops[index]
            old.id != now.id ||
                old.latitude != now.latitude ||
                old.longitude != now.longitude ||
                old.plannedArrival != now.plannedArrival ||
                old.plannedDeparture != now.plannedDeparture
        }
    ) return null

    return id
}

// Optional geometry must be absent as a pair, not partially populated.
private fun knownCoordinatesAreValid(latitude: Double?, longitude: Double?): Boolean =
    (latitude == null && longitude == null) ||
        (latitude != null && longitude != null &&
            latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0)
