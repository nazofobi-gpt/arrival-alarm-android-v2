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

    @Test fun sharedTripIdAndScheduleAcrossDifferentRoutesDoesNotHideCancellation() {
        val routeA = Departure(
            "shared", "RE 1", "Bremen", 1_100L, cancelled = true, routeId = "route-a",
        )
        val routeB = Departure(
            "shared", "S 1", "Oldenburg", 1_100L, cancelled = true, routeId = "route-b",
        )
        val duplicateUpdate = routeA.copy(realtimeEpochSeconds = 1_400L)

        val actual = upcomingCancelledDepartures(
            listOf(routeA, duplicateUpdate, routeB),
            nowEpochSeconds = 1_000L,
        )
        assertEquals(listOf(routeA, routeB), actual)
    }

    @Test fun routeIdUnavailableStillKeepsDistinctDisplayedServices() {
        val bremen = cancelled("shared", 1_100L)
        val oldenburg = bremen.copy(line = "S 1", direction = "Oldenburg")

        assertEquals(
            listOf(bremen, oldenburg),
            upcomingCancelledDepartures(listOf(bremen, oldenburg), nowEpochSeconds = 1_000L),
        )
    }

    @Test fun frequencyInstancesWithSameTripAndScheduleRemainDistinct() {
        val first = Departure(
            "frequency", "RE 1", "Bremen", 1_100L,
            cancelled = true, routeId = "route-1",
            realtimeTripStartDate = "20261019", realtimeTripStartTime = "08:00:00",
        )
        val second = first.copy(realtimeTripStartTime = "08:10:00")
        val duplicateUpdate = first.copy(realtimeEpochSeconds = 1_400L)

        assertEquals(
            listOf(first, second),
            upcomingCancelledDepartures(
                listOf(first, duplicateUpdate, second),
                nowEpochSeconds = 1_000L,
            ),
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
