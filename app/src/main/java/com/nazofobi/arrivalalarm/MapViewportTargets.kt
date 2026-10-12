package com.nazofobi.arrivalalarm

// MapLibre's Web Mercator tiles cannot display either pole.
private const val MAX_MERCATOR_LATITUDE = 85.05112878

internal fun isRenderableMapPoint(point: MapPoint): Boolean =
    point.latitude.isFinite() &&
        point.longitude.isFinite() &&
        point.latitude in -MAX_MERCATOR_LATITUDE..MAX_MERCATOR_LATITUDE &&
        point.longitude in -180.0..180.0

/**
 * The user-selected endpoints take precedence over the surrounding map center.
 * Distinct coordinates ensure identical origin/destination points do not create
 * a degenerate two-point camera bounds update.
 */
internal fun selectionMapCameraTargets(
    center: MapPoint,
    origin: MapPoint?,
    destination: MapPoint?,
): List<MapPoint> {
    val endpoints = listOfNotNull(origin, destination)
        .filter(::isRenderableMapPoint)
        .distinctBy { it.latitude to it.longitude }
    return endpoints.ifEmpty {
        listOf(center).filter(::isRenderableMapPoint)
    }
}
