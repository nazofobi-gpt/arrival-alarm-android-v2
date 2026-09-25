package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistJourneyGatewayTest {
    private class State : ScreenAssistJourneyState {
        val stops = setOf("origin", "boarding", "target")
        var destination: String? = null
        override fun snapshot() = ScreenAssistJourneySnapshot(
            currentLocation = ScreenAssistLocation(53.08, 8.80, 1234L, "device_location"),
            journeyId = "j1",
            line = "6",
            tripId = "t1",
            boardingStop = ScreenAssistStop("boarding", "Boarding", 53.09, 8.81),
            targetStop = ScreenAssistStop("target", "Target", 53.10, 8.82),
            etaEpochMs = 5678L,
            realtime = true,
            serviceAlert = null,
            freshnessEpochMs = 1234L,
            source = "canonical_journey_state",
        )
        override fun hasStop(stopId: String) = stopId in stops
        override fun setOrigin(stopId: String) = hasStop(stopId)
        override fun setDestination(stopId: String): Boolean { destination = stopId; return hasStop(stopId) }
        override fun setBoardingStop(stopId: String) = hasStop(stopId)
        override fun selectJourney(journeyId: String) = journeyId == "j1"
        override fun fillSearch(query: String) = query.length >= 2
        override fun requestRoute(originStopId: String?, destinationStopId: String) =
            hasStop(destinationStopId) && (originStopId == null || hasStop(originStopId))
        override fun setAlarmTarget(stopId: String) = hasStop(stopId)
    }

    @Test fun readUsesCanonicalLocationAndJourneySnapshot() {
        val context = CanonicalScreenAssistJourneyGateway(State()).read()
        assertEquals("device_location", context.location?.source)
        assertEquals("boarding", context.journey?.boardingStop?.id)
        assertEquals("canonical_journey_state", context.journey?.source)
        assertTrue(context.journey?.realtime == true)
    }

    @Test fun commandWritesThroughCanonicalStateBoundary() {
        val state = State()
        val gateway = CanonicalScreenAssistJourneyGateway(state)
        val result = ScreenAssistCommandPolicy().execute(
            ScreenAssistCommand.SetDestination("target"),
            confirmed = false,
            gateway = gateway,
        )
        assertTrue(result.applied)
        assertEquals("target", state.destination)
    }

    @Test fun unknownStopFailsClosedBeforeMutation() {
        val state = State()
        val gateway = CanonicalScreenAssistJourneyGateway(state)
        val result = ScreenAssistCommandPolicy().execute(
            ScreenAssistCommand.SetDestination("invented"),
            confirmed = false,
            gateway = gateway,
        )
        assertFalse(result.applied)
        assertEquals(null, state.destination)
    }
}
