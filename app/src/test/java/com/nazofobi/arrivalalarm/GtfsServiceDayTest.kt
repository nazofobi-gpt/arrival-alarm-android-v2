package com.nazofobi.arrivalalarm

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GtfsServiceDayTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun calendarDatesOverrideBaseWeekdayPattern() {
        val weekday = GtfsCalendarService(
            serviceId = "weekday",
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            activeDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
        val calendar = GtfsServiceCalendar(
            calendars = listOf(weekday),
            exceptions = listOf(
                GtfsCalendarException(
                    "weekday",
                    LocalDate.of(2026, 1, 2),
                    GtfsCalendarExceptionType.REMOVED,
                ),
                GtfsCalendarException(
                    "weekday",
                    LocalDate.of(2026, 1, 3),
                    GtfsCalendarExceptionType.ADDED,
                ),
            ),
        )

        assertFalse(calendar.isActive("weekday", LocalDate.of(2026, 1, 2)))
        assertTrue(calendar.isActive("weekday", LocalDate.of(2026, 1, 3)))
        assertFalse(calendar.isActive("weekday", LocalDate.of(2026, 1, 4)))
    }

    @Test
    fun calendarDatesCanDefineServiceWithoutCalendarTxt() {
        val date = LocalDate.of(2026, 5, 1)
        val calendar = GtfsServiceCalendar(
            calendars = emptyList(),
            exceptions = listOf(
                GtfsCalendarException("special", date, GtfsCalendarExceptionType.ADDED),
            ),
        )

        assertTrue(calendar.isActive("special", date))
        assertEquals(setOf("special"), calendar.activeServices(date))
        assertFalse(calendar.isActive("special", date.plusDays(1)))
    }

    @Test
    fun timeAbove24HoursResolvesIntoFollowingCivilDate() {
        val time = GtfsServiceTime.parse("25:15:30")
        val resolved = time.resolve(LocalDate.of(2026, 1, 15), berlin)

        assertEquals(25, time.hour)
        assertEquals(90_930, time.secondsFromServiceDayStart)
        assertEquals(LocalDateTime.of(2026, 1, 16, 1, 15, 30), resolved.toLocalDateTime())
    }

    @Test
    fun springDstUsesGtfsNoonMinusTwelveHourAnchor() {
        val serviceDate = LocalDate.of(2026, 3, 29)
        val start = GtfsServiceTime.parse("00:00:00").resolve(serviceDate, berlin)
        val end = GtfsServiceTime.parse("04:00:00").resolve(serviceDate, berlin)

        assertEquals(LocalDateTime.of(2026, 3, 28, 23, 0), start.toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 3, 29, 4, 0), end.toLocalDateTime())
        assertEquals(Duration.ofHours(4), Duration.between(start.toInstant(), end.toInstant()))
    }

    @Test
    fun autumnDstAlsoRemainsMonotonic() {
        val serviceDate = LocalDate.of(2026, 10, 25)
        val start = GtfsServiceTime.parse("00:00:00").resolve(serviceDate, berlin)
        val end = GtfsServiceTime.parse("04:00:00").resolve(serviceDate, berlin)

        assertEquals(LocalDateTime.of(2026, 10, 25, 1, 0), start.toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 10, 25, 4, 0), end.toLocalDateTime())
        assertEquals(Duration.ofHours(4), Duration.between(start.toInstant(), end.toInstant()))
    }

    @Test
    fun malformedTimesFailClosed() {
        listOf("24:60:00", "-1:00:00", "12:00", "not-a-time").forEach { raw ->
            try {
                GtfsServiceTime.parse(raw)
                fail("Expected invalid GTFS time to fail: $raw")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }
}
