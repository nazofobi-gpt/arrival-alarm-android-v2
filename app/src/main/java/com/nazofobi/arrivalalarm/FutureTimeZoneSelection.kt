package com.nazofobi.arrivalalarm

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * All actual instants for a user-picked Berlin civil minute. Ordinary minutes have one,
 * spring-forward gaps have none, and autumn fold minutes have two. Android API 23-safe.
 * This prevents silently selecting the wrong train on the DST fallback hour.
 */
internal fun berlinCivilTimeCandidates(
    year: Int, zeroBasedMonth: Int, day: Int, hour: Int, minute: Int,
): List<Long> {
    val utc = TimeZone.getTimeZone("UTC")
    val naiveLocalEpoch = runCatching {
        GregorianCalendar(utc).apply {
            isLenient = false
            clear()
            set(year, zeroBasedMonth, day, hour, minute, 0)
        }.timeInMillis
    }.getOrNull() ?: return emptyList()
    val berlin = TimeZone.getTimeZone("Europe/Berlin")
    val offsets = listOf(-86_400_000L, -43_200_000L, 0L, 43_200_000L, 86_400_000L)
        .map { berlin.getOffset(naiveLocalEpoch + it) }
        .distinct()
    return offsets.map { offset -> naiveLocalEpoch - offset }
        .filter { candidate ->
            val local = GregorianCalendar(berlin).apply { timeInMillis = candidate }
            local.get(Calendar.YEAR) == year &&
                local.get(Calendar.MONTH) == zeroBasedMonth &&
                local.get(Calendar.DAY_OF_MONTH) == day &&
                local.get(Calendar.HOUR_OF_DAY) == hour &&
                local.get(Calendar.MINUTE) == minute
        }
        .distinct()
        .sorted()
}

/**
 * Reopen a valid chosen timetable instant, not today's clock.
 * For a new choice use the next whole minute: the current minute is
 * already in the past by the time the dialogs are confirmed.
 */
internal fun routeTimePickerInitialEpochMillis(
    selectedEpochMillis: Long?,
    nowEpochMillis: Long,
): Long {
    if (selectedEpochMillis != null &&
        supportedFutureDeparture(nowEpochMillis, selectedEpochMillis)
    ) return selectedEpochMillis
    return nowEpochMillis - nowEpochMillis % 60_000L + 60_000L
}
