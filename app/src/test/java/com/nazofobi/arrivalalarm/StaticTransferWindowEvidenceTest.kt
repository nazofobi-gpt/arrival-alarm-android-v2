package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StaticTransferWindowEvidenceTest {
    private val t = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli()
    private val first = StaticTransitLeg("t1", "r1", "a", "x", t, t + 20 * 60_000L)
    private val second = StaticTransitLeg("t2", "r2", "y", "b", t + 25 * 60_000L, t + 50 * 60_000L)
    private val names = mapOf("x" to "Bremen Hbf", "y" to "Platform Süd")

    @Test fun displaysBothSourceStopsAndFiveMinuteScheduledGap() {
        val result = plannedStaticTransferWindows(listOf(first, second)) { names[it] }
        assertEquals(1, result.size)
        assertEquals("x", result.single().arrivalStopId)
        assertEquals("y", result.single().departureStopId)
        assertEquals("Bremen Hbf", result.single().arrivalStopName)
        assertEquals("Platform Süd", result.single().departureStopName)
        assertEquals(5, result.single().plannedGapMinutes)
    }

    @Test fun roundsUpSecondsWithoutReadingAmbiguousDstLocalClocks() {
        val result = plannedStaticTransferWindows(
            listOf(first, second.copy(departureEpochMillis = first.arrivalEpochMillis + 61_000L)),
        ) { names[it] }
        assertEquals(2, result.single().plannedGapMinutes)
    }

    @Test fun missingNamedStopOrContradictoryOrUnboundedClockFailsClosed() {
        assertTrue(plannedStaticTransferWindows(listOf(first, second)) { null }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first, second.copy(
            departureEpochMillis = first.arrivalEpochMillis - 1L,
        ))) { names[it] }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first, second.copy(
            departureEpochMillis = first.arrivalEpochMillis + 4 * 60 * 60_000L,
        ))) { names[it] }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first, second.copy(
            departureEpochMillis = Long.MAX_VALUE,
        ))) { names[it] }.isEmpty())
    }

    @Test fun refusesToShowPartialTransferEvidenceOnMultipleLegs() {
        val third = StaticTransitLeg("t3", "r3", "c", "d",
            second.arrivalEpochMillis - 1L, second.arrivalEpochMillis + 60_000L)
        assertTrue(plannedStaticTransferWindows(listOf(first, second, third)) { names[it] }.isEmpty())
        assertTrue(plannedStaticTransferWindows(listOf(first)) { names[it] }.isEmpty())
    }
}
