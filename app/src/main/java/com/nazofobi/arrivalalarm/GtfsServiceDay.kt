package com.nazofobi.arrivalalarm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class GtfsCalendarExceptionType(val value: Int) {
    ADDED(1),
    REMOVED(2);

    companion object {
        fun fromValue(value: Int): GtfsCalendarExceptionType =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported GTFS calendar exception_type: $value")
    }
}

data class GtfsCalendarService(
    val serviceId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val activeDays: Set<DayOfWeek>,
) {
    init {
        require(serviceId.isNotBlank()) { "serviceId must not be blank" }
        require(!endDate.isBefore(startDate)) { "endDate must be on or after startDate" }
    }
}

data class GtfsCalendarException(
    val serviceId: String,
    val date: LocalDate,
    val type: GtfsCalendarExceptionType,
) {
    init {
        require(serviceId.isNotBlank()) { "serviceId must not be blank" }
    }
}

/**
 * Deterministic GTFS service-day resolver.
 *
 * Callers must provide the service date explicitly; this class never reads the wall clock.
 * calendar_dates overrides the base calendar for an exact service/date pair.
 */
class GtfsServiceCalendar(
    calendars: Collection<GtfsCalendarService>,
    exceptions: Collection<GtfsCalendarException>,
) {
    private val calendarByService = calendars.associateBy { it.serviceId }
    private val exceptionByServiceDate = exceptions.associateBy { it.serviceId to it.date }

    init {
        require(calendarByService.size == calendars.size) { "Duplicate calendar service_id" }
        require(exceptionByServiceDate.size == exceptions.size) {
            "Duplicate calendar_dates service_id/date"
        }
    }

    fun isActive(serviceId: String, serviceDate: LocalDate): Boolean {
        when (exceptionByServiceDate[serviceId to serviceDate]?.type) {
            GtfsCalendarExceptionType.ADDED -> return true
            GtfsCalendarExceptionType.REMOVED -> return false
            null -> Unit
        }

        val calendar = calendarByService[serviceId] ?: return false
        if (serviceDate.isBefore(calendar.startDate) || serviceDate.isAfter(calendar.endDate)) {
            return false
        }
        return serviceDate.dayOfWeek in calendar.activeDays
    }

    fun activeServices(serviceDate: LocalDate): Set<String> {
        val candidateIds = buildSet {
            addAll(calendarByService.keys)
            exceptionByServiceDate.keys
                .asSequence()
                .filter { (_, date) -> date == serviceDate }
                .forEach { (serviceId, _) -> add(serviceId) }
        }
        return candidateIds.filterTo(sortedSetOf()) { isActive(it, serviceDate) }
    }
}

/**
 * GTFS Schedule "Time" value measured from noon-minus-12h of the service day.
 *
 * Hours may exceed 24 for trips continuing after midnight.
 */
@JvmInline
value class GtfsServiceTime private constructor(val secondsFromServiceDayStart: Int) {
    init {
        require(secondsFromServiceDayStart >= 0)
    }

    val hour: Int get() = secondsFromServiceDayStart / 3_600
    val minute: Int get() = (secondsFromServiceDayStart % 3_600) / 60
    val second: Int get() = secondsFromServiceDayStart % 60

    fun resolve(serviceDate: LocalDate, agencyZone: ZoneId): ZonedDateTime {
        val serviceDayAnchor = serviceDate
            .atTime(12, 0)
            .atZone(agencyZone)
            .minusHours(12)
        return serviceDayAnchor.plusSeconds(secondsFromServiceDayStart.toLong())
    }

    override fun toString(): String = "%02d:%02d:%02d".format(hour, minute, second)

    companion object {
        private val pattern = Regex("""^(\d{1,3}):([0-5]\d):([0-5]\d)$""")

        fun parse(raw: String): GtfsServiceTime {
            val match = pattern.matchEntire(raw.trim())
                ?: throw IllegalArgumentException("Invalid GTFS time: $raw")
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val second = match.groupValues[3].toInt()
            val total = Math.addExact(
                Math.multiplyExact(hour, 3_600),
                Math.addExact(Math.multiplyExact(minute, 60), second),
            )
            return GtfsServiceTime(total)
        }
    }
}
