package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistFramePolicyTest {
    @Test
    fun onlyOneFrameCanBeInFlight() {
        val policy = ScreenAssistFramePolicy()
        assertTrue(policy.decide(1_000, false).send)
        val blocked = policy.decide(2_000, false)
        assertFalse(blocked.send)
        assertEquals("backpressure", blocked.reason)
        policy.onFrameFinished()
        assertTrue(policy.decide(2_000, false).send)
    }

    @Test
    fun normalModeRateLimitsFrames() {
        val policy = ScreenAssistFramePolicy()
        assertTrue(policy.decide(1_000, false).send)
        policy.onFrameFinished()
        assertFalse(policy.decide(1_500, false).send)
        assertTrue(policy.decide(2_000, false).send)
    }

    @Test
    fun degradedModeReducesRateResolutionAndQuality() {
        val policy = ScreenAssistFramePolicy()
        val first = policy.decide(1_000, true)
        assertTrue(first.send)
        assertEquals(768, first.maxEdgePx)
        assertEquals(60, first.jpegQuality)
        policy.onFrameFinished()
        assertFalse(policy.decide(3_000, true).send)
        assertTrue(policy.decide(3_500, true).send)
    }

    @Test
    fun resetDropsBackpressureAndTimingState() {
        val policy = ScreenAssistFramePolicy()
        assertTrue(policy.decide(10_000, false).send)
        policy.reset()
        assertTrue(policy.decide(10_001, false).send)
    }
}
