package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRequestGuardTest {
    @Test
    fun newerDestinationSuppressesOlderRouteCallback() {
        val gate = RouteRequestGuard()
        val oldRequest = gate.begin()
        val latestRequest = gate.begin()

        assertFalse(gate.accepts(oldRequest))
        assertTrue(gate.accepts(latestRequest))
    }

    @Test
    fun clearingOriginSuppressesInFlightCallbackWithoutReplacement() {
        val gate = RouteRequestGuard()
        val inFlight = gate.begin()

        gate.invalidate()

        assertFalse(gate.accepts(inFlight))
    }

    @Test
    fun newRouteAfterInvalidationRemainsUsable() {
        val gate = RouteRequestGuard()
        val oldRequest = gate.begin()
        gate.invalidate()
        val nextRequest = gate.begin()

        assertFalse(gate.accepts(oldRequest))
        assertTrue(gate.accepts(nextRequest))
        assertFalse(gate.accepts(0L))
    }
}
