package com.nazofobi.arrivalalarm

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.abs

data class ConcreteTripInstance(
    val sequence: ConcreteTripStopSequence,
    val serviceDate: GtfsServiceDate,
    val scheduledTimes: Map<Int, Long>,
)

/** Match the exact GTFS service day before exposing departure stop roles. */
class ConcreteTripInstancePlanner(
    private val data: StaticGtfsScheduleData,
    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Berlin"),
) {
    fun plan(departure: Departure, boardingStopId: String): ConcreteTripInstance? {
        if (boardingStopId.isBlank() || departure.tripId.isBlank() ||
            departure.realtimeTripStartDate != null || departure.realtimeTripStartTime != null
        ) return null
        val trip = data.trip(departure.tripId) ?: return null
        if (departure.routeId != null && departure.routeId != trip.routeId) return null
        val rows = data.stopTimesForTrip(trip.id, 512)
        val sequence = ConcreteTripStopSequenceResolver.resolve(
            trip, rows, boardingStopId, data::stop,
        ) ?: return null
        val board = rows.firstOrNull { it.stopSequence == sequence.boardingSequence } ?: return null
        val boardTime = parse(board.departureTime ?: board.arrivalTime) ?: return null
        val millis = departure.scheduledEpochSeconds * 1_000L
        val serviceDay = listOf(-1, 0, 1).firstNotNullOfOrNull { offset ->
            val cal = GregorianCalendar(zone).apply {
                timeInMillis = millis
                add(Calendar.DAY_OF_MONTH, offset)
            }
            val day = GtfsServiceDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH))
            val expected = epoch(boardTime, day)
            day.takeIf { data.isServiceActive(trip.serviceId, day) &&
                expected != null && abs(expected - millis) <= 90_000L }
        } ?: return null
        val times = rows.associate { row ->
            row.stopSequence to (parse(row.departureTime ?: row.arrivalTime)
                ?.let { epoch(it, serviceDay) } ?: return null)
        }
        if (rows.zipWithNext().any { (a, b) ->
            (times[b.stopSequence] ?: 0L) < (times[a.stopSequence] ?: 0L)
        }) return null
        return ConcreteTripInstance(sequence, serviceDay, times)
    }

    private fun parse(raw: String?): GtfsServiceTime? =
        raw?.let { runCatching { GtfsServiceTime.parse(it) }.getOrNull() }

    private fun epoch(t: GtfsServiceTime, day: GtfsServiceDate): Long? =
        runCatching { t.resolve(day, zone).epochMillis }.getOrNull()
}
