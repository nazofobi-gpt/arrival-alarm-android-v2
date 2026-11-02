package com.nazofobi.arrivalalarm

/**
 * Reverse an unarmed manual journey. Never reinterpret an armed or arrived
 * alarm's destination as its origin; the user must cancel that alarm first.
 */
internal fun routeEndpointsForSwap(
    origin: MapPoint?,
    destination: MapPoint?,
    phase: JourneyPhase,
): Pair<MapPoint, MapPoint>? {
    if (phase != JourneyPhase.DESTINATION_SELECTED || origin == null || destination == null) {
        return null
    }
    if (!origin.latitude.isFinite() || origin.latitude !in -90.0..90.0 ||
        !origin.longitude.isFinite() || origin.longitude !in -180.0..180.0 ||
        !destination.latitude.isFinite() || destination.latitude !in -90.0..90.0 ||
        !destination.longitude.isFinite() || destination.longitude !in -180.0..180.0
    ) {
        return null
    }
    return destination to origin
}
