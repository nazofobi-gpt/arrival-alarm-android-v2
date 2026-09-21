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
}
