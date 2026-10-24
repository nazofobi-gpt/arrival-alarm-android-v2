package com.nazofobi.arrivalalarm

/**
 * A cached realtime field is not proof of a current provider observation.
 * When a route is offline, only explicitly scheduled values may be displayed.
 * Absence of a scheduled value must remain unknown, not fall back to stale realtime.
 */
internal fun routeEvidenceTime(actual: String?, planned: String?, routeOffline: Boolean): String? =
    if (routeOffline) planned else actual ?: planned

internal fun routeEvidencePlatform(actual: String?, planned: String?, routeOffline: Boolean): String? =
    if (routeOffline) planned else actual ?: planned

internal fun routeHasTrustedRealtime(routeOffline: Boolean, suppliedRealtime: Boolean): Boolean =
    !routeOffline && suppliedRealtime
