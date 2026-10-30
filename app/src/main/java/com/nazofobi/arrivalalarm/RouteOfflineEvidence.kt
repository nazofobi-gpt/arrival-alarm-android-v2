package com.nazofobi.arrivalalarm

/**
 * A cached realtime field is not proof of a current provider observation.
 * When a route is offline, only explicitly scheduled values may be displayed.
 * Absence of a scheduled value must remain unknown, not fall back to stale realtime.
 */
// Blank or control-bearing provider platforms are not trustworthy evidence.
// Preserve ordinary labels; reject control/format characters that could spoof
// the visible platform or disturb the departure board's accessibility text.
private fun String?.usableRouteEvidence(): String? = this?.takeIf { value ->
    value.isNotBlank() && value.none { ch ->
        Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT.toInt()
    }
}

// Malformed provider clocks must not mask a valid scheduled fallback.
// Normalize incidental whitespace so RouteJourneyPanel.routeClock can display the
// same value that is accepted here, including GTFS extended service-day hours.
// A free-form provider prefix ending in T (e.g. "garbageT08:42") is not
// an ISO local datetime, even when routeClock could extract the trailing clock.
private val ROUTE_ISO_LOCAL_CLOCK = Regex(
    """\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])T\d{2}:\d{2}(:\d{2})?"""
)

/** Reject impossible provider calendar dates before extracting the clock. */
private fun isValidRouteIsoClock(value: String): Boolean {
    if (!ROUTE_ISO_LOCAL_CLOCK.matches(value)) return false
    val year = value.substring(0, 4).toInt()
    if (year == 0) return false
    // An ISO local date-time uses civil hours (00..23). Standalone GTFS
    // service-day clocks may use 24+ and are validated separately.
    val hour = value.substring(11, 13).toInt()
    if (hour !in 0..23) return false
    val month = value.substring(5, 7).toInt()
    val day = value.substring(8, 10).toInt()
    val maxDay = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day <= maxDay
}

private fun String?.usableRouteTime(): String? =
    this?.trim()?.takeIf { value ->
        value.isNotEmpty() &&
            (!value.contains('T') || isValidRouteIsoClock(value)) &&
            routeClock(value) != null
    }

/** The route card and detail header must use the same validated clock as stop rows. */
internal fun routeSummaryEvidenceClock(actual: String?, planned: String?, routeOffline: Boolean): String? =
    routeClock(routeEvidenceTime(actual, planned, routeOffline))

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
    // Route clocks omit the calendar day. Two independently validated ISO local
    // instants on different dates are not the same departure, even when HH:mm
    // matches. Do not infer a day shift from a bare GTFS service-day clock.
    val observedDate = observed.takeIf(::isValidRouteIsoClock)?.substring(0, 10)
    val scheduledDate = scheduled.takeIf(::isValidRouteIsoClock)?.substring(0, 10)
    if (observedDate != null && scheduledDate != null && observedDate != scheduledDate) return true
    return observedClock != scheduledClock
}

internal fun routeHasTrustedRealtime(routeOffline: Boolean, suppliedRealtime: Boolean): Boolean =
    !routeOffline && suppliedRealtime

/** A missing/blank provider platform must never receive a live platform badge. */
internal fun routeHasTrustedRealtimePlatform(routeOffline: Boolean, actualPlatform: String?): Boolean =
    routeHasTrustedRealtime(routeOffline, actualPlatform.usableRouteEvidence() != null)
