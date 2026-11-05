package com.nazofobi.arrivalalarm

/**
 * Evidence-backed departure from the origin for an estimated walk to the first stop.
 * This is an informational estimate, not a navigable pedestrian route or a reminder.
 *
 * Use the *planned first-stop departure* only, not a potentially stale realtime
 * overlay or a device-local HH:mm clock (which is ambiguous across DST).
 */
internal fun plannedWalkStartEpochMillis(option: RouteOption, safetyBufferMinutes: Int = 0): Long? {
    if (safetyBufferMinutes !in 0..15 || safetyBufferMinutes % 5 != 0) return null
    val walking = option.accessWalking ?: return null
    val walkSeconds = walking.walkSeconds.takeIf { it in 1..14_400 } ?: return null
    val firstStop = option.stops.firstOrNull() ?: return null
    // A walk estimate for another boarding stop must never become this trip's leave time.
    if (firstStop.id.isNullOrBlank() || firstStop.id != walking.stopId) return null
    val plannedDeparture = firstStop.plannedDeparture.toTransitEpochSecondsOrNull() ?: return null
    return runCatching {
        Math.subtractExact(
            Math.multiplyExact(plannedDeparture, 1_000L),
            Math.multiplyExact(
                Math.addExact(walkSeconds.toLong(), safetyBufferMinutes.toLong() * 60L),
                1_000L,
            ),
        )
    }.getOrNull()
}

/**
 * Time remaining until the evidenced walk start. The small bounded look-ahead
 * is for a glanceable in-app countdown, not an alarm or background reminder.
 * Round upward: one remaining second must not appear as zero minutes.
 */
internal fun plannedWalkMinutesRemaining(
    option: RouteOption,
    nowMillis: Long,
    safetyBufferMinutes: Int = 0,
): Long? {
    val start = plannedWalkStartEpochMillis(option, safetyBufferMinutes) ?: return null
    val remainingMillis = runCatching { Math.subtractExact(start, nowMillis) }.getOrNull()
        ?: return null
    if (remainingMillis <= 0L || remainingMillis > 180L * 60_000L) return null
    return (remainingMillis - 1L) / 60_000L + 1L
}

/** Planned-only estimate; no inference about realtime vehicle location or actual walk speed. */
internal enum class PlannedWalkWarning { WALK_START_PASSED, PLANNED_DEPARTURE_PASSED }

/** Null when source time or access-walk evidence is absent or still in the future. */
internal fun plannedWalkWarning(
    option: RouteOption,
    nowMillis: Long,
    safetyBufferMinutes: Int = 0,
): PlannedWalkWarning? {
    val start = plannedWalkStartEpochMillis(option, safetyBufferMinutes) ?: return null
    val seconds = option.accessWalking?.walkSeconds ?: return null
    val plannedDeparture = runCatching {
        Math.addExact(
            start,
            Math.multiplyExact(seconds.toLong() + safetyBufferMinutes.toLong() * 60L, 1_000L),
        )
    }.getOrNull() ?: return null
    return when {
        nowMillis >= plannedDeparture -> PlannedWalkWarning.PLANNED_DEPARTURE_PASSED
        nowMillis >= start -> PlannedWalkWarning.WALK_START_PASSED
        else -> null
    }
}

/** Explicit external walking directions, only for a source-identified first boarding stop.
 * No user's origin coordinates are included; Google Maps chooses the origin itself.
 */
internal fun plannedWalkDirectionsUrl(option: RouteOption): String? {
    if (plannedWalkStartEpochMillis(option) == null) return null
    val evidence = option.accessWalking ?: return null
    val first = option.stops.firstOrNull() ?: return null
    if (first.id.isNullOrBlank() || first.id != evidence.stopId) return null
    val lat = first.latitude ?: return null
    val lon = first.longitude ?: return null
    if (!lat.isFinite() || !lon.isFinite() ||
        lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return "https://www.google.com/maps/dir/?api=1&destination=${lat}%2C${lon}&travelmode=walking&dir_action=navigate"
}
