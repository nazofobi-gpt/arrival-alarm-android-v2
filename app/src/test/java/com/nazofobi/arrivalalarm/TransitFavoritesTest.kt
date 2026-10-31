package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitFavoritesTest {
    @Test
    fun addsFavoriteWithoutDroppingExistingStops() {
        assertEquals(
            setOf("existing-stop", "new-stop"),
            toggleFavoriteStopIds(setOf("existing-stop"), "new-stop"),
        )
    }

    @Test
    fun removesOnlyTheSelectedFavorite() {
        assertEquals(
            setOf("keep-stop"),
            toggleFavoriteStopIds(setOf("keep-stop", "remove-stop"), "remove-stop"),
        )
    }

    @Test
    fun rejectsBlankAndCoordinateIdentifiers() {
        val current = setOf("existing-stop")
        assertEquals(current, toggleFavoriteStopIds(current, " "))
        assertEquals(current, toggleFavoriteStopIds(current, "coord:52.665,8.237"))
        assertFalse(isFavoriteStopIdValid(" "))
        assertFalse(isFavoriteStopIdValid("coord:52.665,8.237"))
        assertTrue(isFavoriteStopIdValid("lohne-station"))
    }
}
