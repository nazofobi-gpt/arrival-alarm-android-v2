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
): List<Departure> {
    // An unset or out-of-range device clock cannot establish an upcoming cancellation.
    if (!nowEpochSeconds.isDisplayableDepartureEpochSeconds() || limit <= 0) return emptyList()
    return departures.asSequence()
    .filter {
        it.cancelled && it.scheduledEpochSeconds.isDisplayableDepartureEpochSeconds() &&
            it.scheduledEpochSeconds >= nowEpochSeconds
    }
    // Trip IDs and scheduled times can collide across routes and feeds.
    // Retain distinct visible services while deduplicating repeated updates.
    .distinctBy {
        listOf(
            it.tripId, it.scheduledEpochSeconds, it.routeId, it.line, it.direction,
            it.realtimeTripStartDate, it.realtimeTripStartTime,
            it.gtfsInstance, it.boardingStopSequence,
        )
    }
    .sortedBy { it.scheduledEpochSeconds }
    .take(limit.coerceAtLeast(0))
    .toList()
}
