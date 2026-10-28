package com.nazofobi.arrivalalarm

/**
 * A locally pinned line belongs to one concrete stop and GTFS route.
 * The length-prefixed key avoids collisions between opaque provider IDs;
 * display names and individual trip IDs are intentionally not used as keys.
 *
 * This is a local bookmark, NOT a notification subscription.
 */
internal fun pinnedStopLineKey(stopId: String, routeId: String?): String? {
    if (stopId.isBlank() || routeId.isNullOrBlank()) return null
    return "${stopId.length}:$stopId${routeId.length}:$routeId"
}
