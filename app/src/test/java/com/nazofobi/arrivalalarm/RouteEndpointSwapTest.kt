package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteEndpointSwapTest {
    private val lohne = MapPoint(52.665, 8.237, "Lohne")
    private val bremen = MapPoint(53.083, 8.813, "Bremen Hbf")

    @Test
    fun swapsBothValidEndpointsWithoutLosingIdentity() {
        assertEquals(bremen to lohne, routeEndpointsForSwap(lohne, bremen, JourneyPhase.DESTINATION_SELECTED))
        assertEquals(lohne to bremen, routeEndpointsForSwap(bremen, lohne, JourneyPhase.DESTINATION_SELECTED))
    }

    @Test
    fun refusesToChangeArmedOrCompletedAlarm() {
        assertNull(routeEndpointsForSwap(lohne, bremen, JourneyPhase.ARMED))
        assertNull(routeEndpointsForSwap(lohne, bremen, JourneyPhase.ARRIVED))
    }

    @Test
    fun refusesMissingPointsAndIncompleteJourney() {
        assertNull(routeEndpointsForSwap(null, bremen, JourneyPhase.DESTINATION_SELECTED))
        assertNull(routeEndpointsForSwap(lohne, null, JourneyPhase.DESTINATION_SELECTED))
        assertNull(routeEndpointsForSwap(lohne, bremen, JourneyPhase.EMPTY))
        assertNull(routeEndpointsForSwap(lohne, bremen, JourneyPhase.START_SELECTED))
    }

    @Test
    fun refusesNonFiniteAndOutOfBoundsCoordinates() {
        val invalid = listOf(
            MapPoint(Double.NaN, 8.237, "NaN"),
            MapPoint(Double.POSITIVE_INFINITY, 8.237, "Infinity"),
            MapPoint(91.0, 8.237, "Invalid latitude"),
            MapPoint(52.665, -181.0, "Invalid longitude"),
        )
        for (point in invalid) {
            assertNull(routeEndpointsForSwap(point, bremen, JourneyPhase.DESTINATION_SELECTED))
            assertNull(routeEndpointsForSwap(lohne, point, JourneyPhase.DESTINATION_SELECTED))
        }
    }
}
