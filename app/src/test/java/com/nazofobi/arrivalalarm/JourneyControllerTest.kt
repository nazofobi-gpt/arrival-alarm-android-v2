package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JourneyControllerTest {
    @Test fun happyPathReachesArrived() {
        val c = JourneyController(arrivalRadiusMeters = 250.0)
        c.selectStart(GeoPoint(1.0, 1.0))
        c.selectDestination(GeoPoint(2.0, 2.0))
        c.arm()
        c.onDistanceChanged(500.0)
        assertEquals(JourneyPhase.ARMED, c.state.phase)
        c.onDistanceChanged(200.0)
        assertEquals(JourneyPhase.ARRIVED, c.state.phase)
    }

    @Test fun destinationWithoutStartIsExplicitError() {
        val c = JourneyController()
        c.selectDestination(GeoPoint(2.0, 2.0))
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
        assertNotNull(c.state.error)
    }

    @Test fun distanceUpdatesDoNotArriveBeforeArmed() {
        val c = JourneyController()
        c.selectStart(GeoPoint(1.0, 1.0))
        c.selectDestination(GeoPoint(2.0, 2.0))
        c.onDistanceChanged(0.0)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
    }
    @Test fun invalidCoordinatesCannotReplaceValidSelections() {
        val c = JourneyController()
        c.selectStart(GeoPoint(53.5, 8.1))
        c.selectDestination(GeoPoint(53.6, 8.2))
        c.selectDestination(GeoPoint(Double.NaN, 9.0))
        assertEquals(GeoPoint(53.6, 8.2), c.state.destination)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
        assertNotNull(c.state.error)

        c.selectStart(GeoPoint(91.0, 8.0))
        assertEquals(GeoPoint(53.5, 8.1), c.state.start)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
        c.arm()
        assertEquals(JourneyPhase.ARMED, c.state.phase)
    }

    @Test fun invalidFreshSelectionsDoNotArmAndRespectGlobalBounds() {
        val c = JourneyController()
        c.selectStart(GeoPoint(Double.POSITIVE_INFINITY, 0.0))
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
        c.selectStart(GeoPoint(-90.0, 180.0))
        c.selectDestination(GeoPoint(20.0, -180.1))
        assertEquals(JourneyPhase.START_SELECTED, c.state.phase)
        c.arm()
        assertEquals(JourneyPhase.START_SELECTED, c.state.phase)
        c.selectDestination(GeoPoint(90.0, -180.0))
        c.arm()
        assertEquals(JourneyPhase.ARMED, c.state.phase)
    }

    @Test fun corruptedPersistedArmedJourneyFailsClosedOnRestart() {
        val store = object : JourneyStateStore {
            override fun load() = JourneyUiState(
                phase = JourneyPhase.ARMED,
                start = GeoPoint(53.0, 8.0),
                destination = GeoPoint(Double.NaN, 8.1),
            )
            override fun save(state: JourneyUiState) = Unit
        }
        val c = JourneyController(stateStore = store)
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
        c.arm()
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRadiusCannotCreateAlwaysArrivingController() {
        JourneyController(arrivalRadiusMeters = Double.POSITIVE_INFINITY)
    }

}
