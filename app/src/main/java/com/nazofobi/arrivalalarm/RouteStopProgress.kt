package com.nazofobi.arrivalalarm

import java.time.OffsetDateTime

data class RouteStopProgress(
    val previous: RouteStop? = null,
    val current: RouteStop? = null,
    val next: RouteStop? = null,
    val resolvedByRealtimeOrSchedule: Boolean = false,
)

class RouteStopProgressTracker {
    fun resolve(stops: List<RouteStop>, nowEpochSeconds: Long): RouteStopProgress {
        if (stops.isEmpty()) return RouteStopProgress()

        val windows = stops.map { stop ->
            val arrival = stop.arrival.toEpochSecondsOrNull()
                ?: stop.plannedArrival.toEpochSecondsOrNull()
            val departure = stop.departure.toEpochSecondsOrNull()
                ?: stop.plannedDeparture.toEpochSecondsOrNull()
            StopWindow(stop, arrival, departure)
        }
        if (windows.none { it.arrival != null || it.departure != null }) {
            return RouteStopProgress()
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
                resolvedByRealtimeOrSchedule = true,
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
            resolvedByRealtimeOrSchedule = true,
        )
    }

    private data class StopWindow(
        val stop: RouteStop,
        val arrival: Long?,
        val departure: Long?,
    )

    private fun String?.toEpochSecondsOrNull(): Long? {
        if (this.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(this).toEpochSecond() }.getOrNull()
    }
}
