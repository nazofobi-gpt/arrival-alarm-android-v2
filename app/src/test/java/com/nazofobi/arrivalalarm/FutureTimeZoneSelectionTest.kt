package com.nazofobi.arrivalalarm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FutureTimeZoneSelectionTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Test
    fun autumnRepeatedHourOffersBothExplicitOffsets() {
        val candidates = berlinCivilTimeCandidates(2026, 9, 25, 2, 30)
        assertEquals(2, candidates.size)
        assertEquals(3_600_000L, candidates[1] - candidates[0])
        val clock = SimpleDateFormat("yyyy-MM-dd HH:mm XXX", Locale.ROOT).apply {
            timeZone = berlin
        }
        assertEquals("2026-10-25 02:30 +02:00", clock.format(Date(candidates[0])))
        assertEquals("2026-10-25 02:30 +01:00", clock.format(Date(candidates[1])))
    }

    @Test
    fun springNonexistentMinuteIsRejectedInsteadOfNormalized() {
        assertTrue(berlinCivilTimeCandidates(2027, 2, 28, 2, 30).isEmpty())
        assertEquals(1, berlinCivilTimeCandidates(2027, 2, 28, 3, 30).size)
    }

    @Test
    fun ordinaryMinuteHasExactlyOneInstantAndInvalidDatesFailClosed() {
        assertEquals(1, berlinCivilTimeCandidates(2026, 10, 4, 13, 15).size)
        assertTrue(berlinCivilTimeCandidates(2026, 1, 30, 12, 0).isEmpty())
        assertTrue(berlinCivilTimeCandidates(2026, 9, 25, 24, 0).isEmpty())
    }
}
