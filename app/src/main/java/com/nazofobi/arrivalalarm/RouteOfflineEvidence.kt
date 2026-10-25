package com.nazofobi.arrivalalarm

/**
 * A cached realtime field is not proof of a current provider observation.
 * When a route is offline, only explicitly scheduled values may be displayed.
 * Absence of a scheduled value must remain unknown, not fall back to stale realtime.
 */
// Blank provider platform fields are missing evidence, not usable values.
// Preserve platform labels; presentation formatting is owned by the UI.
private fun String?.usableRouteEvidence(): String? = this?.takeIf { it.isNotBlank() }

// Malformed provider clocks must not mask a valid scheduled fallback.
// Normalize incidental whitespace so RouteJourneyPanel.routeClock can display the
// same value that is accepted here, including GTFS extended service-day hours.
// A free-form provider prefix ending in T (e.g. "garbageT08:42") is not
// an ISO local datetime, even when routeClock could extract the trailing clock.
private val ROUTE_ISO_LOCAL_CLOCK = Regex(
    """\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])T\d{2}:\d{2}(:\d{2})?"""
)

private fun String?.usableRouteTime(): String? =
    this?.trim()?.takeIf { value ->
        value.isNotEmpty() &&
            (!value.contains('T') || ROUTE_ISO_LOCAL_CLOCK.matches(value)) &&
            routeClock(value) != null
    }

internal fun routeEvidenceTime(actual: String?, planned: String?, routeOffline: Boolean): String? =
    if (routeOffline) planned.usableRouteTime()
    else actual.usableRouteTime() ?: planned.usableRouteTime()

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
    val observedClock = observed.usableRouteTime()?.let(::routeClock) ?: return false
    val scheduledClock = scheduled.usableRouteTime()?.let(::routeClock) ?: return false
    return observedClock != scheduledClock
}

internal fun routeHasTrustedRealtime(routeOffline: Boolean, suppliedRealtime: Boolean): Boolean =
    !routeOffline && suppliedRealtime

/** A missing/blank provider platform must never receive a live platform badge. */
internal fun routeHasTrustedRealtimePlatform(routeOffline: Boolean, actualPlatform: String?): Boolean =
    routeHasTrustedRealtime(routeOffline, !actualPlatform.isNullOrBlank())
