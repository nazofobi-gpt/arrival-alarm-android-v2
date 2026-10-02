package com.nazofobi.arrivalalarm

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GtfsServiceDayTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Test
    fun baseCalendarWeekdayAndRangeAreApplied() {
        val service = GtfsCalendarService(
            serviceId = "weekday",
            startDate = GtfsServiceDate.parse("20260101"),
            endDate = GtfsServiceDate.parse("20261231"),
            activeDays = setOf(
                GtfsWeekday.MONDAY,
                GtfsWeekday.TUESDAY,
                GtfsWeekday.WEDNESDAY,
                GtfsWeekday.THURSDAY,
                GtfsWeekday.FRIDAY,
            ),
        )
        val calendar = GtfsServiceCalendar(listOf(service), emptyList())

        assertTrue(calendar.isActive("weekday", GtfsServiceDate.parse("20261002")))
        assertFalse(calendar.isActive("weekday", GtfsServiceDate.parse("20261003")))
        assertFalse(calendar.isActive("weekday", GtfsServiceDate.parse("20270104")))
        assertFalse(calendar.isActive("missing", GtfsServiceDate.parse("20261002")))
    }

    @Test
    fun calendarDatesOverrideBaseWeekdayPattern() {
        val weekday = GtfsCalendarService(
            serviceId = "weekday",
            startDate = GtfsServiceDate.parse("20260101"),
            endDate = GtfsServiceDate.parse("20261231"),
            activeDays = setOf(
                GtfsWeekday.MONDAY,
                GtfsWeekday.TUESDAY,
                GtfsWeekday.WEDNESDAY,
                GtfsWeekday.THURSDAY,
                GtfsWeekday.FRIDAY,
            ),
        )
        val calendar = GtfsServiceCalendar(
            calendars = listOf(weekday),
            exceptions = listOf(
                GtfsCalendarException(
                    "weekday",
                    GtfsServiceDate.parse("20260102"),
                    GtfsCalendarExceptionType.REMOVED,
                ),
                GtfsCalendarException(
                    "weekday",
                    GtfsServiceDate.parse("20260103"),
                    GtfsCalendarExceptionType.ADDED,
                ),
            ),
        )

        assertFalse(calendar.isActive("weekday", GtfsServiceDate.parse("20260102")))
        assertTrue(calendar.isActive("weekday", GtfsServiceDate.parse("20260103")))
        assertFalse(calendar.isActive("weekday", GtfsServiceDate.parse("20260104")))
    }

    @Test
    fun calendarDatesCanDefineServiceWithoutCalendarTxt() {
        val date = GtfsServiceDate.parse("20260501")
        val calendar = GtfsServiceCalendar(
            calendars = emptyList(),
            exceptions = listOf(
                GtfsCalendarException("special", date, GtfsCalendarExceptionType.ADDED),
            ),
        )

        assertTrue(calendar.isActive("special", date))
        assertEquals(setOf("special"), calendar.activeServices(date))
        assertFalse(calendar.isActive("special", GtfsServiceDate.parse("20260502")))
    }

    @Test
    fun emptyCalendarHasNoActiveService() {
        val calendar = GtfsServiceCalendar(emptyList(), emptyList())
        assertEquals(emptySet<String>(), calendar.activeServices(GtfsServiceDate.parse("20261002")))
    }

    @Test
    fun dateParsingRejectsInvalidCivilDates() {
        listOf("20260229", "20261301", "20260001", "20260431", "26-01-01").forEach { raw ->
            try {
                GtfsServiceDate.parse(raw)
                fail("Expected invalid GTFS service date to fail: $raw")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
        assertEquals(GtfsWeekday.THURSDAY, GtfsServiceDate.parse("20261001").weekday)
    }

    @Test
    fun timeAbove24HoursResolvesIntoFollowingCivilDate() {
        val time = GtfsServiceTime.parse("25:15:30")
        val resolved = time.resolve(GtfsServiceDate.parse("20260115"), berlin)

        assertEquals(25, time.hour)
        assertEquals(90_930, time.secondsFromServiceDayStart)
        assertEquals(listOf(2026, 1, 16, 1, 15, 30), resolved.localFields().toList())
    }

    @Test
    fun springDstUsesGtfsNoonMinusTwelveHourAnchor() {
        val serviceDate = GtfsServiceDate.parse("20260329")
        val start = GtfsServiceTime.parse("00:00:00").resolve(serviceDate, berlin)
        val end = GtfsServiceTime.parse("04:00:00").resolve(serviceDate, berlin)

        assertEquals(listOf(2026, 3, 28, 23, 0, 0), start.localFields().toList())
        assertEquals(listOf(2026, 3, 29, 4, 0, 0), end.localFields().toList())
        assertEquals(4L * 60L * 60L * 1_000L, end.epochMillis - start.epochMillis)
    }

    @Test
    fun autumnDstAlsoRemainsMonotonic() {
        val serviceDate = GtfsServiceDate.parse("20261025")
        val start = GtfsServiceTime.parse("00:00:00").resolve(serviceDate, berlin)
        val end = GtfsServiceTime.parse("04:00:00").resolve(serviceDate, berlin)

        assertEquals(listOf(2026, 10, 25, 1, 0, 0), start.localFields().toList())
        assertEquals(listOf(2026, 10, 25, 4, 0, 0), end.localFields().toList())
        assertEquals(4L * 60L * 60L * 1_000L, end.epochMillis - start.epochMillis)
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
