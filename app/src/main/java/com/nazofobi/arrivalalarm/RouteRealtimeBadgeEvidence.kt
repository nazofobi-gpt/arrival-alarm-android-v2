package com.nazofobi.arrivalalarm

/**
 * Stop deltas and platforms are not proof of live data without a fresh,
 * matched provider observation. In particular, cached stop values must not
 * override STALE, UNAVAILABLE, or FRESH_NO_MATCH route-level evidence.
 */
internal fun routeHasVerifiedRealtimeDelta(
    routeOffline: Boolean,
    state: RouteRealtimeState?,
    suppliedDelta: Boolean,
): Boolean =
    !routeOffline && state == RouteRealtimeState.FRESH_MATCHED && suppliedDelta

internal fun routeHasVerifiedRealtimePlatform(
    routeOffline: Boolean,
    state: RouteRealtimeState?,
    actualPlatform: String?,
): Boolean =
    state == RouteRealtimeState.FRESH_MATCHED &&
        routeHasTrustedRealtimePlatform(routeOffline, actualPlatform)

/** A cached cancelled-trip ID alone does not prove a fresh provider cancellation. */
internal fun routeHasVerifiedRealtimeCancellation(
    routeOffline: Boolean,
    state: RouteRealtimeState?,
    suppliedCancellation: Boolean,
): Boolean =
    routeHasVerifiedRealtimeDelta(
        routeOffline = routeOffline,
        state = state,
        suppliedDelta = suppliedCancellation,
    )


/**
 * A visible stop deviation needs the SAME stop event's planned and observed
 * instants plus fresh matched online evidence. Minutes are rounded only for
 * display; unknown, on-time (<30 s), implausible (>24 h) and malformed times
 * produce no label. This is a timetable delta, not a vehicle location.
 */
internal fun verifiedStopTimeDeviationMinutes(
    routeOffline: Boolean,
    state: RouteRealtimeState?,
    actualIso: String?,
    plannedIso: String?,
): Int? {
    if (!routeHasVerifiedRealtimeDelta(
            routeOffline = routeOffline,
            state = state,
            suppliedDelta = actualIso != null && plannedIso != null,
        )
    ) return null
    val actual = actualIso.toTransitEpochSecondsOrNull() ?: return null
    val planned = plannedIso.toTransitEpochSecondsOrNull() ?: return null
    val deltaSeconds = actual - planned
    if (deltaSeconds !in -86_400L..86_400L) return null
    val roundedMinutes = if (deltaSeconds >= 0L) {
        (deltaSeconds + 30L) / 60L
    } else {
        -((-deltaSeconds + 30L) / 60L)
    }
    return roundedMinutes.toInt().takeIf { it != 0 }
}
