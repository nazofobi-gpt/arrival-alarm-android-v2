package com.nazofobi.arrivalalarm

/**
 * Keeps the existing local raw-stop-ID preference contract.
 * Map-coordinate placeholders and blank IDs are not real GTFS stops.
 */
internal fun isFavoriteStopIdValid(stopId: String): Boolean =
    stopId.isNotBlank() && !stopId.startsWith("coord:")

internal fun toggleFavoriteStopIds(current: Set<String>, stopId: String): Set<String> {
    if (!isFavoriteStopIdValid(stopId)) return current
    return if (stopId in current) current - stopId else current + stopId
}
