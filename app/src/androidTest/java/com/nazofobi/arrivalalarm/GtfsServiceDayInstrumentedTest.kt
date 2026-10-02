package com.nazofobi.arrivalalarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsServiceDayInstrumentedTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Test
    fun calendarDateExceptionPrecedenceIsDeterministicOnAndroid() {
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
        val calendar = GtfsServiceCalendar(
            calendars = listOf(service),
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
    }

    @Test
    fun overnightGtfsTimeCrossesCivilMidnightWithoutLosingServiceDay() {
        val resolved = GtfsServiceTime.parse("25:15:30")
            .resolve(GtfsServiceDate.parse("20260115"), berlin)

        assertEquals(listOf(2026, 1, 16, 1, 15, 30), resolved.localFields().toList())
    }

    @Test
    fun berlinSpringDstUsesNoonMinusTwelveHourAnchor() {
        val date = GtfsServiceDate.parse("20260329")
        val start = GtfsServiceTime.parse("00:00:00").resolve(date, berlin)
        val end = GtfsServiceTime.parse("04:00:00").resolve(date, berlin)

        assertEquals(listOf(2026, 3, 28, 23, 0, 0), start.localFields().toList())
        assertEquals(listOf(2026, 3, 29, 4, 0, 0), end.localFields().toList())
        assertEquals(14_400_000L, end.epochMillis - start.epochMillis)
    }

    @Test
    fun berlinAutumnDstRemainsMonotonic() {
        val date = GtfsServiceDate.parse("20261025")
        val start = GtfsServiceTime.parse("00:00:00").resolve(date, berlin)
        val end = GtfsServiceTime.parse("04:00:00").resolve(date, berlin)

        assertEquals(listOf(2026, 10, 25, 1, 0, 0), start.localFields().toList())
        assertEquals(listOf(2026, 10, 25, 4, 0, 0), end.localFields().toList())
        assertEquals(14_400_000L, end.epochMillis - start.epochMillis)
    }
}
