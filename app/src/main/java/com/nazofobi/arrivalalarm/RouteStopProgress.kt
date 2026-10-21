package com.nazofobi.arrivalalarm

import java.util.GregorianCalendar
import java.util.TimeZone

enum class TransitTimingBasis {
    SCHEDULED,
    REALTIME,
}

data class RouteStopProgress(
    val previous: RouteStop? = null,
    val current: RouteStop? = null,
    val next: RouteStop? = null,
    val resolvedByProviderTime: Boolean = false,
    val timingBasis: TransitTimingBasis = TransitTimingBasis.SCHEDULED,
    // Null means provider stop-time evidence cannot establish progress.
    // Count is based on ordered stop occurrences, never on possibly repeated stop IDs.
    val remainingStops: Int? = null,
)

/**
 * Resolves only what provider stop times actually support.
 *
 * Between stops, current remains null rather than pretending that the vehicle is physically
 * at a stop. The last passed stop is previous and the first future stop is next.
 */
private const val MIN_TRUSTED_PROGRESS_EPOCH_SECONDS = 946_684_800L // 2000-01-01 UTC
private const val MAX_TRUSTED_PROGRESS_EPOCH_SECONDS = 253_402_300_799L // 9999-12-31 UTC

class RouteStopProgressTracker {
    fun resolve(
        stops: List<RouteStop>,
        nowEpochSeconds: Long,
        trustRealtime: Boolean = true,
    ): RouteStopProgress {
        // An unset/reset device clock must not turn an otherwise valid timetable
        // into a fabricated current/next stop or remaining-stop count.
        if (stops.isEmpty() ||
            nowEpochSeconds < MIN_TRUSTED_PROGRESS_EPOCH_SECONDS ||
            nowEpochSeconds > MAX_TRUSTED_PROGRESS_EPOCH_SECONDS
        ) return RouteStopProgress()

        val windows = stops.map { stop ->
            val actualArrival = if (trustRealtime) stop.arrival.toTransitEpochSecondsOrNull() else null
            val plannedArrival = stop.plannedArrival.toTransitEpochSecondsOrNull()
            val actualDeparture = if (trustRealtime) stop.departure.toTransitEpochSecondsOrNull() else null
            val plannedDeparture = stop.plannedDeparture.toTransitEpochSecondsOrNull()
            val candidateArrival = actualArrival ?: plannedArrival
            val candidateDeparture = actualDeparture ?: plannedDeparture
            // A delayed arrival combined with a scheduled departure can invert the dwell
            // window. Never infer progress from an impossible or contradictory interval.
            val reversedWindow = candidateArrival != null && candidateDeparture != null &&
                candidateArrival > candidateDeparture
            val coherentSchedule = plannedArrival != null && plannedDeparture != null &&
                plannedArrival <= plannedDeparture
            StopWindow(
                stop = stop,
                arrival = when {
                    reversedWindow && coherentSchedule -> plannedArrival
                    reversedWindow -> null
                    else -> candidateArrival
                },
                departure = when {
                    reversedWindow && coherentSchedule -> plannedDeparture
                    reversedWindow -> null
                    else -> candidateDeparture
                },
                hasRealtimeEvidence = !reversedWindow &&
                    ((actualArrival != null && (plannedArrival == null || actualArrival != plannedArrival)) ||
                        (actualDeparture != null && (plannedDeparture == null || actualDeparture != plannedDeparture))),
            )
        }
        if (windows.none { it.arrival != null || it.departure != null }) {
            return RouteStopProgress()
        }

        // Preserve route-order semantics when schedule/realtime timestamps disagree.
        // Two distinct stops must not occupy overlapping provider time windows.
        if (!hasCoherentTimeline(windows)) {
            // Conflicting realtime observations must not erase a coherent planned journey.
            // Recalculate solely from scheduled stops, clearly labeling the timing basis.
            return if (trustRealtime) resolve(stops, nowEpochSeconds, trustRealtime = false)
                else RouteStopProgress()
        }

        val basis = if (windows.any { it.hasRealtimeEvidence }) {
            TransitTimingBasis.REALTIME
        } else {
            TransitTimingBasis.SCHEDULED
        }

        // Inclusive windows may touch at the same provider second. Both stops then
        // match "current"; choosing the first one would fabricate a stop identity.
        // Fall back to scheduled evidence when it can disambiguate the boundary.
        var currentIndex = -1
        for ((index, window) in windows.withIndex()) {
            val arrival = window.arrival
            val departure = window.departure
            val matches = when {
                arrival != null && departure != null -> nowEpochSeconds in arrival..departure
                arrival != null -> nowEpochSeconds == arrival
                departure != null -> nowEpochSeconds == departure
                else -> false
            }
            if (matches) {
                if (currentIndex >= 0) {
                    return if (trustRealtime) resolve(stops, nowEpochSeconds, trustRealtime = false)
                        else RouteStopProgress()
                }
                currentIndex = index
            }
        }
        if (currentIndex >= 0) {
            return RouteStopProgress(
                previous = stops.getOrNull(currentIndex - 1),
                current = stops[currentIndex],
                next = stops.getOrNull(currentIndex + 1),
                resolvedByProviderTime = true,
                timingBasis = basis,
                remainingStops = stops.lastIndex - currentIndex,
            )
        }

        val previousIndex = windows.indexOfLast { window ->
            val passedAt = window.departure ?: window.arrival
            passedAt != null && passedAt <= nowEpochSeconds
        }
        val nextIndex = windows.indexOfFirst { window ->
            val upcomingAt = window.arrival ?: window.departure
            upcomingAt != null && upcomingAt > nowEpochSeconds
        }

        // A timed stop across an untimed segment is not necessarily the immediate
        // previous/next stop. Missing intermediate evidence must remain unknown.
        val unknownNeighbor = when {
            previousIndex >= 0 && nextIndex >= 0 -> nextIndex != previousIndex + 1
            previousIndex < 0 && nextIndex >= 0 -> nextIndex != 0
            previousIndex >= 0 && nextIndex < 0 -> previousIndex != stops.lastIndex
            else -> false
        }
        if (unknownNeighbor) return RouteStopProgress()

        return RouteStopProgress(
            previous = stops.getOrNull(previousIndex),
            current = null,
            next = stops.getOrNull(nextIndex),
            resolvedByProviderTime = true,
            timingBasis = basis,
            remainingStops = if (nextIndex >= 0) stops.size - nextIndex else 0,
        )
    }

    private fun hasCoherentTimeline(windows: List<StopWindow>): Boolean {
        var priorEnd: Long? = null
        for (window in windows) {
            val start = window.arrival ?: window.departure
            val end = window.departure ?: window.arrival
            if (start != null && end != null && end < start) return false
            val previous = priorEnd
            if (start != null && previous != null && start < previous) return false
            if (end != null) priorEnd = end
        }
        return true
    }

    private data class StopWindow(
        val stop: RouteStop,
        val arrival: Long?,
        val departure: Long?,
        val hasRealtimeEvidence: Boolean,
    )
}

internal fun String?.toTransitEpochSecondsOrNull(): Long? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = ISO_TRANSIT_TIMESTAMP.matchEntire(value) ?: return null
    return runCatching {
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val hour = match.groupValues[4].toInt()
        val minute = match.groupValues[5].toInt()
        val second = match.groupValues[6].toInt()
        val zone = match.groupValues[8]
        val offsetSeconds = when {
            zone == "Z" -> 0
            else -> {
                val sign = if (match.groupValues[9] == "-") -1 else 1
                val offsetHours = match.groupValues[10].toInt()
                val offsetMinutes = match.groupValues[11].toInt()
                // The current civil UTC offset range is -12:00 through +14:00.
                // A forged -13/-14 hour feed must not move the rider's stop progress.
                val maxHours = if (sign < 0) 12 else 14
                require(offsetHours <= maxHours && offsetMinutes <= 59 &&
                    (offsetHours != maxHours || offsetMinutes == 0))
                sign * (offsetHours * 3_600 + offsetMinutes * 60)
            }
        }

        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        calendar.timeInMillis / 1_000L - offsetSeconds
    }.getOrNull()
}

private val ISO_TRANSIT_TIMESTAMP = Regex(
    "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?(Z|([+-])(\\d{2}):?(\\d{2}))$"
)

/** Only fresh matched route evidence can advance progress using realtime stop times. */
internal fun isTrustedRealtimeForJourney(
    state: RouteRealtimeState?,
    routeOffline: Boolean,
    cancelledTripIds: List<String> = emptyList(),
): Boolean = !routeOffline && state == RouteRealtimeState.FRESH_MATCHED &&
    cancelledTripIds.isEmpty()
