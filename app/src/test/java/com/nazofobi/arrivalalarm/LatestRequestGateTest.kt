package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestGateTest {
    @Test fun newerRequestRejectsOlderCallbacks() {
        val gate = LatestRequestGate()
        val first = gate.begin()
        val second = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test fun changingSelectionInvalidatesOutstandingCallback() {
        val gate = LatestRequestGate()
        val pending = gate.begin()

        gate.invalidate()

        assertFalse(gate.isCurrent(pending))
        assertTrue(gate.isCurrent(gate.begin()))
    }

    @Test fun multipleUpdatesForTheCurrentRequestRemainAllowed() {
        val gate = LatestRequestGate()
        val active = gate.begin()

        assertTrue(gate.isCurrent(active))
        assertTrue(gate.isCurrent(active))
        gate.begin()
        assertFalse(gate.isCurrent(active))
    }
}
