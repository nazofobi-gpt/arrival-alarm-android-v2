package com.nazofobi.arrivalalarm

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class GtfsRealtimeServiceKeyFormatTest {
    @Test
    fun serviceIdentifiersStayAsciiUnderNonLatinDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals(
                "20261011",
                GtfsRealtimeServiceKeyFormat.date(GtfsServiceDate(2026, 10, 11)),
            )
            assertEquals("08:05:09", GtfsRealtimeServiceKeyFormat.time(8 * 3600 + 5 * 60 + 9))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun extendedGtfsHoursRemainStableForOvernightTrips() {
        assertEquals("24:20:05", GtfsRealtimeServiceKeyFormat.time(24 * 3600 + 20 * 60 + 5))
        assertEquals(
            24 * 3600 + 20 * 60 + 5,
            GtfsServiceTime.parse(GtfsRealtimeServiceKeyFormat.time(24 * 3600 + 20 * 60 + 5))
                .secondsFromServiceDayStart,
        )
    }
}
