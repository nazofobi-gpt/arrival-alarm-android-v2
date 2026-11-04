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
