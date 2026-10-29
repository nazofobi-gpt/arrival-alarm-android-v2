package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitRecentsNoOpPersistenceTest {
    @Test
    fun selectingNewestTripAgainDoesNotRewriteUnchangedHistory() {
        val persisted = mutableListOf<List<String>>()
        val store = TransitRecentsStore(maxItems = 2) { _, recents ->
            persisted += recents
        }

        store.recordTrip("trip-a")
        store.recordTrip("trip-a")
        assertEquals(listOf(listOf("trip-a")), persisted)

        store.recordTrip("trip-b")
        store.recordTrip("trip-b")
        assertEquals(listOf(listOf("trip-a"), listOf("trip-b", "trip-a")), persisted)

        // An older trip moving to the front is an actual change and must persist.
        store.recordTrip("trip-a")
        assertEquals(listOf("trip-a", "trip-b"), store.recents())
        assertEquals(3, persisted.size)
        assertEquals(listOf("trip-a", "trip-b"), persisted.last())
    }

    @Test
    fun selectingAlreadyRestoredNewestTripAvoidsDuplicatePreferenceWrite() {
        var writes = 0
        val store = TransitRecentsStore(
            maxItems = 2,
            recentTripIds = listOf("trip-a", "trip-b"),
        ) { _, _ -> writes++ }

        store.recordTrip("trip-a")
        assertEquals(0, writes)
        store.recordTrip("trip-b")
        assertEquals(listOf("trip-b", "trip-a"), store.recents())
        assertEquals(1, writes)
    }

    @Test
    fun disabledOrNegativeHistoryCapacityNeverPersistsEmptySnapshots() {
        for (capacity in listOf(0, -1, Int.MIN_VALUE)) {
            var writes = 0
            val store = TransitRecentsStore(
                maxItems = capacity,
                recentTripIds = listOf("existing"),
            ) { _, _ -> writes++ }

            store.recordTrip("new-trip")
            store.recordTrip("new-trip")
            assertEquals(emptyList<String>(), store.recents())
            assertEquals("capacity=$capacity", 0, writes)
        }
    }
}
