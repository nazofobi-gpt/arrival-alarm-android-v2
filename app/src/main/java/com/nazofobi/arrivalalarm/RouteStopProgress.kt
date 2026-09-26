package com.nazofobi.arrivalalarm

import java.time.OffsetDateTime

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
)

/**
 * Resolves only what provider stop times actually support.
 *
 * Between stops, current remains null rather than pretending that the vehicle is physically
 * at a stop. The last passed stop is previous and the first future stop is next.
 */
class RouteStopProgressTracker {
    fun resolve(stops: List<RouteStop>, nowEpochSeconds: Long): RouteStopProgress {
        if (stops.isEmpty()) return RouteStopProgress()

        val windows = stops.map { stop ->
            val actualArrival = stop.arrival.toTransitEpochSecondsOrNull()
            val plannedArrival = stop.plannedArrival.toTransitEpochSecondsOrNull()
            val actualDeparture = stop.departure.toTransitEpochSecondsOrNull()
            val plannedDeparture = stop.plannedDeparture.toTransitEpochSecondsOrNull()
            StopWindow(
                stop = stop,
                arrival = actualArrival ?: plannedArrival,
                departure = actualDeparture ?: plannedDeparture,
                hasRealtimeDelta =
                    (actualArrival != null && plannedArrival != null && actualArrival != plannedArrival) ||
                        (actualDeparture != null && plannedDeparture != null && actualDeparture != plannedDeparture),
            )
        }
        if (windows.none { it.arrival != null || it.departure != null }) {
            return RouteStopProgress()
        }

        val basis = if (windows.any { it.hasRealtimeDelta }) {
            TransitTimingBasis.REALTIME
        } else {
            TransitTimingBasis.SCHEDULED
        }

        val currentIndex = windows.indexOfFirst { window ->
            val arrival = window.arrival
            val departure = window.departure
            when {
                arrival != null && departure != null -> nowEpochSeconds in arrival..departure
                arrival != null -> nowEpochSeconds == arrival
                departure != null -> nowEpochSeconds == departure
                else -> false
            }
        }
        if (currentIndex >= 0) {
            return RouteStopProgress(
                previous = stops.getOrNull(currentIndex - 1),
                current = stops[currentIndex],
                next = stops.getOrNull(currentIndex + 1),
                resolvedByProviderTime = true,
                timingBasis = basis,
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

        return RouteStopProgress(
            previous = stops.getOrNull(previousIndex),
            current = null,
            next = stops.getOrNull(nextIndex),
            resolvedByProviderTime = true,
            timingBasis = basis,
        )
    }

    private data class StopWindow(
        val stop: RouteStop,
        val arrival: Long?,
        val departure: Long?,
        val hasRealtimeDelta: Boolean,
    )
}

internal fun String?.toTransitEpochSecondsOrNull(): Long? {
    if (this.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(this).toEpochSecond() }.getOrNull()
}
