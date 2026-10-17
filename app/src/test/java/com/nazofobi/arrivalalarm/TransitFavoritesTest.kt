package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitFavoritesTest {
    @Test fun selectingStopAddsItWithoutOverwritingOtherFavorites() {
        assertEquals(setOf("a", "b"), toggleFavoriteStopIds(setOf("a"), "b"))
    }

    @Test fun selectingAnExistingFavoriteRemovesOnlyThatStop() {
        assertEquals(setOf("b"), toggleFavoriteStopIds(setOf("a", "b"), "a"))
    }

    @Test fun blankStopIdCannotBePersisted() {
        val initial = setOf("a")
        assertEquals(initial, toggleFavoriteStopIds(initial, " "))
    }
}
