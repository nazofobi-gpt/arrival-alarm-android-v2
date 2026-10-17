package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureBoardVisibilityTest {
    @Test
    fun selectedStopShowsDeparturesWithoutAnyRoute() {
        assertTrue(
            shouldDisplayDepartureBoard(
                hasDestination = true,
                hasSelectedStop = true,
                hasRouteOptions = false,
            ),
        )
    }

    @Test
    fun coordinateDestinationCanUseJourneyOptionsFallback() {
        assertTrue(
            shouldDisplayDepartureBoard(
                hasDestination = true,
                hasSelectedStop = false,
                hasRouteOptions = true,
            ),
        )
    }

    @Test
    fun selectedStopShowsBoardWithoutDestinationOrRoute() {
        assertTrue(
            shouldDisplayDepartureBoard(
                hasDestination = false,
                hasSelectedStop = true,
                hasRouteOptions = false,
            ),
        )
    }

    @Test
    fun absentStopAndUnresolvedCoordinateFailClosed() {
        assertFalse(
            shouldDisplayDepartureBoard(
                hasDestination = false,
                hasSelectedStop = false,
                hasRouteOptions = true,
            ),
        )
        assertFalse(
            shouldDisplayDepartureBoard(
                hasDestination = true,
                hasSelectedStop = false,
                hasRouteOptions = false,
            ),
        )
    }
}
