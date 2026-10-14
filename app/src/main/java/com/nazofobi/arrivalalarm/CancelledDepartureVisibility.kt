package com.nazofobi.arrivalalarm

/**
 * Do not let an old cancellation dominate the current station board.
 *
 * Cancellation evidence is still shown separately from selectable departures,
 * but only for departures whose published scheduled time has not passed.
 * A realtime estimate is not a replacement for the cancelled service's
 * scheduled identity.
 */
internal fun upcomingCancelledDepartures(
    departures: List<Departure>,
    nowEpochSeconds: Long,
    limit: Int = 3,
): List<Departure> = departures.asSequence()
    .filter { it.cancelled && it.scheduledEpochSeconds >= nowEpochSeconds }
    // Providers can repeat the same scheduled cancellation: one board card per service.
    .distinctBy { it.tripId to it.scheduledEpochSeconds }
    .sortedBy { it.scheduledEpochSeconds }
    .take(limit.coerceAtLeast(0))
    .toList()
