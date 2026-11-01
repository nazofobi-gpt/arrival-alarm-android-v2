package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitRecentsPersistenceRetryTest {
    @Test
    fun selectingSameNewestTripRetriesFailedPreferenceWriteExactlyOnce() {
        var attempts = 0
        val persisted = mutableListOf<List<String>>()
        val store = TransitRecentsStore(maxItems = 2) { _, recents ->
            attempts++
            if (attempts == 1) throw IllegalStateException("Transient disk failure")
            persisted += recents
        }

        store.recordTrip("trip-a") // The in-memory state updates, but persistence fails.
        assertEquals(listOf("trip-a"), store.recents())
        assertEquals(1, attempts)
        assertEquals(emptyList<List<String>>(), persisted)

        store.recordTrip("trip-a") // No ordering change, but the failed write is retried.
        assertEquals(2, attempts)
        assertEquals(listOf(listOf("trip-a")), persisted)

        store.recordTrip("trip-a") // A successful retry restores no-op semantics.
        assertEquals(2, attempts)
    }

    @Test
    fun clearingAlreadyEmptyHistoryRetriesPreviouslyFailedClear() {
        var attempts = 0
        var durableHistory = listOf("old-trip")
        val store = TransitRecentsStore(
            recentTripIds = listOf("old-trip"),
        ) { _, recents ->
            attempts++
            if (attempts == 1) throw IllegalStateException("Transient disk failure")
            durableHistory = recents
        }

        store.clearRecents()
        assertEquals(emptyList<String>(), store.recents())
        assertEquals(listOf("old-trip"), durableHistory)
        assertEquals(1, attempts)

        store.clearRecents() // Must clear persisted old trip on retry.
        assertEquals(emptyList<String>(), durableHistory)
        assertEquals(2, attempts)
        store.clearRecents()
        assertEquals(2, attempts)
    }

    @Test
    fun rejectedBlankTripDoesNotTriggerPendingPersistenceRetry() {
        var attempts = 0
        val store = TransitRecentsStore { _, _ ->
            attempts++
            throw IllegalStateException("Disk unavailable")
        }
        store.recordTrip("trip-a")
        assertEquals(1, attempts)
        store.recordTrip(" ")
        assertEquals(1, attempts)
        assertEquals(listOf("trip-a"), store.recents())
    }
}
