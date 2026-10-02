package com.nazofobi.arrivalalarm

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

enum class GtfsCalendarExceptionType(val value: Int) {
    ADDED(1),
    REMOVED(2);

    companion object {
        fun fromValue(value: Int): GtfsCalendarExceptionType =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported GTFS calendar exception_type: $value")
    }
}

enum class GtfsWeekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

data class GtfsServiceDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<GtfsServiceDate> {
    init {
        require(year in 1900..9999) { "year out of supported range" }
        require(month in 1..12) { "month out of range" }
        require(day in 1..daysInMonth(year, month)) { "day out of range" }
    }

    val weekday: GtfsWeekday
        get() {
            val calendar = GregorianCalendar(UTC).apply {
                isLenient = false
                clear()
                set(year, month - 1, day, 12, 0, 0)
            }
            return when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> GtfsWeekday.MONDAY
                Calendar.TUESDAY -> GtfsWeekday.TUESDAY
                Calendar.WEDNESDAY -> GtfsWeekday.WEDNESDAY
                Calendar.THURSDAY -> GtfsWeekday.THURSDAY
                Calendar.FRIDAY -> GtfsWeekday.FRIDAY
                Calendar.SATURDAY -> GtfsWeekday.SATURDAY
                Calendar.SUNDAY -> GtfsWeekday.SUNDAY
                else -> error("Unexpected weekday")
            }
        }

    override fun compareTo(other: GtfsServiceDate): Int =
        compareValuesBy(this, other, GtfsServiceDate::year, GtfsServiceDate::month, GtfsServiceDate::day)

    override fun toString(): String = "%04d%02d%02d".format(year, month, day)

    companion object {
        private val UTC = TimeZone.getTimeZone("UTC")
        private val pattern = Regex("""^(\d{4})(\d{2})(\d{2})$""")

        fun parse(raw: String): GtfsServiceDate {
            val match = pattern.matchEntire(raw.trim())
                ?: throw IllegalArgumentException("Invalid GTFS service date: $raw")
            return GtfsServiceDate(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
            )
        }

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> error("month validated before daysInMonth")
        }

        private fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
}

data class GtfsCalendarService(
    val serviceId: String,
    val startDate: GtfsServiceDate,
    val endDate: GtfsServiceDate,
    val activeDays: Set<GtfsWeekday>,
) {
    init {
        require(serviceId.isNotBlank()) { "serviceId must not be blank" }
        require(endDate >= startDate) { "endDate must be on or after startDate" }
    }
}

data class GtfsCalendarException(
    val serviceId: String,
    val date: GtfsServiceDate,
    val type: GtfsCalendarExceptionType,
) {
    init {
        require(serviceId.isNotBlank()) { "serviceId must not be blank" }
    }
}

/**
 * Deterministic GTFS service-day resolver.
 *
 * Callers provide the service date explicitly; this class never reads the wall clock.
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

    fun isActive(serviceId: String, serviceDate: GtfsServiceDate): Boolean {
        when (exceptionByServiceDate[serviceId to serviceDate]?.type) {
            GtfsCalendarExceptionType.ADDED -> return true
            GtfsCalendarExceptionType.REMOVED -> return false
            null -> Unit
        }

        val calendar = calendarByService[serviceId] ?: return false
        if (serviceDate < calendar.startDate || serviceDate > calendar.endDate) return false
        return serviceDate.weekday in calendar.activeDays
    }

    fun activeServices(serviceDate: GtfsServiceDate): Set<String> {
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

data class GtfsResolvedServiceTime(
    val epochMillis: Long,
    val timeZoneId: String,
) {
    fun localFields(): IntArray {
        val calendar = GregorianCalendar(TimeZone.getTimeZone(timeZoneId)).apply {
            timeInMillis = epochMillis
        }
        return intArrayOf(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND),
        )
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

    fun resolve(serviceDate: GtfsServiceDate, agencyTimeZone: TimeZone): GtfsResolvedServiceTime {
        val noon = GregorianCalendar(agencyTimeZone).apply {
            isLenient = false
            clear()
            set(
                serviceDate.year,
                serviceDate.month - 1,
                serviceDate.day,
                12,
                0,
                0,
            )
            set(Calendar.MILLISECOND, 0)
        }
        val serviceDayAnchorMillis = noon.timeInMillis - TWELVE_HOURS_MILLIS
        return GtfsResolvedServiceTime(
            epochMillis = Math.addExact(
                serviceDayAnchorMillis,
                Math.multiplyExact(secondsFromServiceDayStart.toLong(), 1_000L),
            ),
            timeZoneId = agencyTimeZone.id,
        )
    }

    override fun toString(): String = "%02d:%02d:%02d".format(hour, minute, second)

    companion object {
        private const val TWELVE_HOURS_MILLIS = 12L * 60L * 60L * 1_000L
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
