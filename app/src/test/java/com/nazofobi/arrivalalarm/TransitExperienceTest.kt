package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitExperienceTest {
    private val now = 1_000L
    private val departures = listOf(
        Departure("old","1","A",900,realtimeEpochSeconds=950),
        Departure("b","2","B",1_300,realtimeEpochSeconds=1_100),
        Departure("cancelled","3","C",1_050,cancelled=true),
        Departure("a","1","A",1_200)
    )

    @Test fun boardUsesRealtimeWhenSupportedAndFiltersPastAndCancelled() {
        val engine=TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures=true))
        val board=engine.departureBoard(departures,now)
        assertEquals(listOf("b","a"),board.map{it.tripId})
        assertTrue(board.first().isRealtime)
    }

    @Test fun unsupportedRealtimeFallsBackToScheduleWithoutInventingLiveState() {
        val engine=TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures=false))
        val board=engine.departureBoard(departures,now)
        assertEquals(listOf("a","b"),board.map{it.tripId})
        assertFalse(board.any{it.isRealtime})
        assertTrue(engine.alerts(listOf(ServiceAlert("x","x","x"))).isEmpty())
        assertTrue(engine.vehicles(listOf(LiveVehicle("a",GeoPoint(1.0,2.0)))).isEmpty())
    }

    @Test fun alternativesExcludeDifferentRoutesAndDirections() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val selected = Departure("trip-a", "RE 9", "Bremen", 1_100, routeId = "re9")
        val later = Departure("trip-b", "RE 9", "Bremen", 1_300, routeId = "re9")
        val opposite = Departure("trip-c", "RE 9", "Osnabrück", 1_400, routeId = "re9")
        val otherRoute = Departure("trip-d", "S 1", "Bremen", 1_500, routeId = "s1")
        assertEquals(
            listOf("trip-b"),
            engine.alternateDepartures(
                listOf(selected, later, opposite, otherRoute), selected, now
            ).map { it.tripId }
        )
    }

    @Test fun repeatedTripTemplateKeepsEachDepartureAndItsRecentSelectionDistinct() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val first = Departure("frequency-template", "S 1", "Bremen", 1_200, routeId = "s1")
        val second = first.copy(scheduledEpochSeconds = 1_800)
        val delayedFirst = first.copy(realtimeEpochSeconds = 1_280)
        assertEquals(first.departureInstanceKey, delayedFirst.departureInstanceKey)
        assertFalse(first.departureInstanceKey == second.departureInstanceKey)
        assertEquals(
            listOf(second.departureInstanceKey),
            engine.alternateDepartures(listOf(delayedFirst, second), delayedFirst, now)
                .map { it.departureInstanceKey }
        )
        val recents = TransitRecentsStore()
        recents.recordTrip(first.departureInstanceKey)
        recents.recordTrip(second.departureInstanceKey)
        assertEquals(listOf(second.departureInstanceKey, first.departureInstanceKey), recents.recents())
    }

    @Test fun accessibilityPenaltyOnlyAppliesWhenProviderSupportsIt() {
        val pref=AccessibilityPreference(stepFreeOnly=true,extraTransferMinutes=6)
        assertEquals(11,TransitExperienceEngine(TransitProviderCapabilities(accessibility=true)).walkingTransferMinutes(5,pref))
        assertEquals(5,TransitExperienceEngine(TransitProviderCapabilities(accessibility=false)).walkingTransferMinutes(5,pref))
    }

    @Test fun favoritesAndRecentsAreDeterministicAndBounded() {
        val store=TransitRecentsStore(maxItems=2)
        assertTrue(store.toggleFavorite("s1")); assertFalse(store.toggleFavorite("s1"))
        store.recordTrip("a"); store.recordTrip("b"); store.recordTrip("a"); store.recordTrip("c")
        assertEquals(listOf("c","a"),store.recents())
    }

    @Test fun favoritesAndRecentsCanSeedAndPublishPersistentState() {
        var savedFavorites = emptyList<String>()
        var savedRecents = emptyList<String>()
        val store = TransitRecentsStore(
            maxItems = 2,
            favoriteIds = listOf("stop-a"),
            recentTripIds = listOf("old-2", "old-1"),
        ) { favorites, recents ->
            savedFavorites = favorites
            savedRecents = recents
        }

        assertTrue(store.isFavorite("stop-a"))
        assertFalse(store.isFavorite("stop-b"))
        assertTrue(store.toggleFavorite("stop-b"))
        store.recordTrip("new")

        assertEquals(listOf("stop-a", "stop-b"), savedFavorites)
        assertEquals(listOf("new", "old-2"), savedRecents)
    }
}
