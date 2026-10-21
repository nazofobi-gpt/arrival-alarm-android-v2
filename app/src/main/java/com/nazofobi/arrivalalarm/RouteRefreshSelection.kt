package com.nazofobi.arrivalalarm

/**
 * A route id is a UI key, not proof that the same physical departure still exists.
 *
 * Retain a selected itinerary after refresh only with matching, source-backed
 * trip IDs, scheduled boarding instant, ordered stop roles and coordinates.
 * Live delay, platform and status changes intentionally do not affect identity.
 * A provider without sufficient evidence requires explicit reselection.
 */
private const val MAX_ROUTE_DEPARTURE_EPOCH_MILLIS = 253_402_300_799_999L

/** Route identity cannot be proved with timestamps beyond supported UTC year 9999. */
internal fun retainSelectedRouteAfterRefresh(
    selectedRouteId: String?,
    previousOptions: List<RouteOption>,
    updatedOptions: List<RouteOption>,
): String? {
    val id = selectedRouteId ?: return null
    val previous = previousOptions.singleOrNull { it.id == id } ?: return null
    val updated = updatedOptions.singleOrNull { it.id == id } ?: return null

    if (
        previous.tripIds.isEmpty() ||
        previous.tripIds.any { it.isBlank() } ||
        previous.tripIds != updated.tripIds
    ) return null

    // GTFS trip_id may recur each service day; trip_id alone is not a trip instance.
    val boardingInstant = previous.departureEpochMillis ?: return null
    if (
        boardingInstant <= 0L ||
        boardingInstant > MAX_ROUTE_DEPARTURE_EPOCH_MILLIS ||
        updated.departureEpochMillis != boardingInstant
    ) return null

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
    if (previous.stops.any { it.id.isNullOrBlank() } || updated.stops.any { it.id.isNullOrBlank() }) {
        return null
    }
    if (previous.stops.indices.any { index ->
            val old = previous.stops[index]
            val now = updated.stops[index]
            old.id != now.id ||
                old.latitude != now.latitude ||
                old.longitude != now.longitude
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
