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
    routeHasVerifiedRealtimeDelta(
        routeOffline = routeOffline,
        state = state,
        suppliedDelta = !actualPlatform.isNullOrBlank(),
    )

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
