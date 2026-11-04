package com.nazofobi.arrivalalarm

/**
 * Evidence-backed departure from the origin for an estimated walk to the first stop.
 * This is an informational estimate, not a navigable pedestrian route or a reminder.
 *
 * Use the *planned first-stop departure* only, not a potentially stale realtime
 * overlay or a device-local HH:mm clock (which is ambiguous across DST).
 */
internal fun plannedWalkStartEpochMillis(option: RouteOption): Long? {
    val walkSeconds = option.accessWalking?.walkSeconds?.takeIf { it in 1..14_400 }
        ?: return null
    val plannedDeparture = option.stops.firstOrNull()?.plannedDeparture
        .toTransitEpochSecondsOrNull() ?: return null
    return runCatching {
        Math.subtractExact(
            Math.multiplyExact(plannedDeparture, 1_000L),
            Math.multiplyExact(walkSeconds.toLong(), 1_000L),
        )
    }.getOrNull()
}

/** Planned-only estimate; no inference about realtime vehicle location or actual walk speed. */
internal enum class PlannedWalkWarning { WALK_START_PASSED, PLANNED_DEPARTURE_PASSED }

/** Null when source time or access-walk evidence is absent or still in the future. */
internal fun plannedWalkWarning(option: RouteOption, nowMillis: Long): PlannedWalkWarning? {
    val start = plannedWalkStartEpochMillis(option) ?: return null
    val seconds = option.accessWalking?.walkSeconds ?: return null
    val plannedDeparture = runCatching {
        Math.addExact(start, Math.multiplyExact(seconds.toLong(), 1_000L))
    }.getOrNull() ?: return null
    return when {
        nowMillis >= plannedDeparture -> PlannedWalkWarning.PLANNED_DEPARTURE_PASSED
        nowMillis >= start -> PlannedWalkWarning.WALK_START_PASSED
        else -> null
    }
}
