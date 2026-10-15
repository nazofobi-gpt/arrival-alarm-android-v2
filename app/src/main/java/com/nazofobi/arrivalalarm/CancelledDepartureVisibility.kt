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
    // Some feeds omit trip IDs. Two anonymous lines leaving at the same time are
    // different cancellations, not a single service identified by ("", scheduled).
    .distinctBy { departure ->
        if (departure.tripId.isNotBlank()) {
            listOf("trip", departure.tripId, departure.scheduledEpochSeconds.toString())
        } else {
            listOf(
                "anonymous",
                departure.routeId.orEmpty(),
                departure.line,
                departure.direction,
                departure.scheduledEpochSeconds.toString(),
            )
        }
    }
    .sortedBy { it.scheduledEpochSeconds }
    .take(limit.coerceAtLeast(0))
    .toList()
