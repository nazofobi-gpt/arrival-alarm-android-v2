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

    @Test fun alternateDepartureExcludesSelectedTrip() {
        val engine=TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures=true))
        assertEquals(listOf("a"),engine.alternateDepartures(departures,"b",now).map{it.tripId})
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
}
