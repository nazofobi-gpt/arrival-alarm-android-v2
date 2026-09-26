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
                require(offsetHours <= 23 && offsetMinutes <= 59)
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
