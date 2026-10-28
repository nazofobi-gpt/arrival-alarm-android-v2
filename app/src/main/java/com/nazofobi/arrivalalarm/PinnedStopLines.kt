package com.nazofobi.arrivalalarm

/**
 * A locally pinned line belongs to one concrete stop and GTFS route.
 * The length-prefixed key includes provider scope to isolate identical GTFS IDs;
 * display names and individual trip IDs are intentionally not used as keys.
 *
 * This is a local bookmark, NOT a notification subscription.
 */
internal fun pinnedStopLineKey(stopId: String, routeId: String?, providerId: String?): String? {
    // A coordinate fallback is not a real GTFS stop; it cannot own a line pin.
    if (providerId.isNullOrBlank() || stopId.isBlank() || stopId.startsWith("coord:") || routeId.isNullOrBlank()) return null
    return "${providerId.length}:$providerId${stopId.length}:$stopId${routeId.length}:$routeId"
}
