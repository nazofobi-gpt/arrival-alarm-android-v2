package com.nazofobi.arrivalalarm

/**
 * A cached realtime field is not proof of a current provider observation.
 * When a route is offline, only explicitly scheduled values may be displayed.
 * Absence of a scheduled value must remain unknown, not fall back to stale realtime.
 */
// Blank provider fields are missing evidence, not usable live or scheduled values.
// Preserve the original nonblank text; presentation formatting is owned by the UI.
private fun String?.usableRouteEvidence(): String? = this?.takeIf { it.isNotBlank() }

internal fun routeEvidenceTime(actual: String?, planned: String?, routeOffline: Boolean): String? =
    if (routeOffline) planned.usableRouteEvidence()
    else actual.usableRouteEvidence() ?: planned.usableRouteEvidence()

internal fun routeEvidencePlatform(actual: String?, planned: String?, routeOffline: Boolean): String? =
    if (routeOffline) planned.usableRouteEvidence()
    else actual.usableRouteEvidence() ?: planned.usableRouteEvidence()

/**
 * Only displayable provider clocks can prove a real-time deviation.
 * Missing or malformed clocks must never color an unknown time as live.
 */
internal fun routeHasUsableRealtimeDelta(actual: String?, planned: String?): Boolean {
    val observed = actual?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val scheduled = planned?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    // Compare the visible minute, not provider text precision (08:15 vs 08:15:00).
    // A seconds-only difference must not create a misleading realtime-change badge.
    val observedClock = routeClock(observed) ?: return false
    val scheduledClock = routeClock(scheduled) ?: return false
    return observedClock != scheduledClock
}

internal fun routeHasTrustedRealtime(routeOffline: Boolean, suppliedRealtime: Boolean): Boolean =
    !routeOffline && suppliedRealtime

/** A missing/blank provider platform must never receive a live platform badge. */
internal fun routeHasTrustedRealtimePlatform(routeOffline: Boolean, actualPlatform: String?): Boolean =
    routeHasTrustedRealtime(routeOffline, !actualPlatform.isNullOrBlank())
