package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestGateTest {
    @Test
    fun onlyLatestNearbyRequestCanUpdateUi() {
        val gate = LatestRequestGate()
        val oldRequest = gate.begin()
        val latestRequest = gate.begin()

        assertFalse(gate.accepts(oldRequest))
        assertTrue(gate.accepts(latestRequest))
    }

    @Test
    fun originChangeInvalidatesAnOutstandingRouteRequest() {
        val gate = LatestRequestGate()
        val routeRequest = gate.begin()

        gate.invalidate()

        assertFalse(gate.accepts(routeRequest))
        assertTrue(gate.accepts(gate.begin()))
    }

    @Test
    fun destinationChangeInvalidatesOldDeparturesBeforeNewRequestStarts() {
        val gate = LatestRequestGate()
        val previousStationRequest = gate.begin()

        gate.invalidate()
        assertFalse(gate.accepts(previousStationRequest))

        val newStationRequest = gate.begin()
        assertTrue(gate.accepts(newStationRequest))
        assertFalse(gate.accepts(previousStationRequest))
    }

    @Test
    fun repeatedCallbacksForActiveRequestRemainEligibleUntilSuperseded() {
        val gate = LatestRequestGate()
        val activeRequest = gate.begin()
        assertTrue(gate.accepts(activeRequest))
        assertTrue(gate.accepts(activeRequest))

        gate.begin()
        assertFalse(gate.accepts(activeRequest))
    }
}
