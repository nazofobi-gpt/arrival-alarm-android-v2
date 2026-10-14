package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelledDepartureVisibilityTest {
    private fun cancelled(id: String, scheduled: Long, realtime: Long? = null) =
        Departure(id, "RE 1", "Bremen", scheduled, realtimeEpochSeconds = realtime, cancelled = true)

    @Test fun staleCancellationsDoNotPolluteCurrentBoard() {
        val actual = upcomingCancelledDepartures(
            listOf(
                cancelled("old", 999L),
                cancelled("later", 1_400L),
                cancelled("now", 1_000L),
                Departure("running", "RE 1", "Bremen", 1_050L),
            ),
            nowEpochSeconds = 1_000L,
        )
        assertEquals(listOf("now", "later"), actual.map { it.tripId })
    }

    @Test fun nearestScheduledCancellationsWinBeforeThreeCardLimit() {
        val actual = upcomingCancelledDepartures(
            listOf(
                cancelled("far", 9_000L),
                cancelled("third", 1_300L),
                cancelled("first", 1_100L),
                cancelled("second", 1_200L),
            ),
            nowEpochSeconds = 1_000L,
        )
        assertEquals(listOf("first", "second", "third"), actual.map { it.tripId })
    }

    @Test fun repeatedProviderCancellationDoesNotHideOtherDepartures() {
        val actual = upcomingCancelledDepartures(
            listOf(
                cancelled("a", 1_100L),
                cancelled("a", 1_100L, realtime = 1_800L),
                cancelled("b", 1_200L),
                cancelled("a", 1_300L),
            ),
            nowEpochSeconds = 1_000L,
            limit = 3,
        )
        // Same trip at a different scheduled time represents a distinct departure.
        assertEquals(
            listOf("a" to 1_100L, "b" to 1_200L, "a" to 1_300L),
            actual.map { it.tripId to it.scheduledEpochSeconds },
        )
    }

    @Test fun realtimeTimestampDoesNotResurrectAlreadyPassedCancellation() {
        val actual = upcomingCancelledDepartures(
            listOf(cancelled("past", 900L, realtime = 1_500L)),
            nowEpochSeconds = 1_000L,
        )
        assertTrue(actual.isEmpty())
    }

    @Test fun zeroAndNegativeLimitFailClosed() {
        val source = listOf(cancelled("a", 2_000L))
        assertTrue(upcomingCancelledDepartures(source, 1_000L, limit = 0).isEmpty())
        assertTrue(upcomingCancelledDepartures(source, 1_000L, limit = -10).isEmpty())
    }
}
