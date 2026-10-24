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

/** A blank provider clock cannot prove a real-time deviation from the schedule. */
internal fun routeHasUsableRealtimeDelta(actual: String?, planned: String?): Boolean =
    !actual.isNullOrBlank() && !planned.isNullOrBlank() && actual != planned

internal fun routeHasTrustedRealtime(routeOffline: Boolean, suppliedRealtime: Boolean): Boolean =
    !routeOffline && suppliedRealtime
