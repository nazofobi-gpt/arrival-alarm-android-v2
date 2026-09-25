package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistUiControllerTest {
    private val controller = ScreenAssistUiController()

    @Test fun activeCaptureExposesPauseAndStop() {
        val state = controller.reduce(
            ScreenAssistState(ScreenAssistPhase.ACTIVE, "active"),
            ScreenAssistRealtimePhase.CONNECTED,
        )
        assertEquals(ScreenAssistUiPhase.ACTIVE, state.phase)
        assertTrue(state.canPause)
        assertTrue(state.canStop)
        assertFalse(state.canStart)
    }

    @Test fun pausedCaptureExposesResumeAndStop() {
        val state = controller.reduce(
            ScreenAssistState(ScreenAssistPhase.PAUSED, "paused"),
            ScreenAssistRealtimePhase.CONNECTED,
        )
        assertEquals(ScreenAssistUiPhase.PAUSED, state.phase)
        assertTrue(state.canResume)
        assertTrue(state.canStop)
    }

    @Test fun realtimeFailureDegradesAndStopsFrameGuidance() {
        val state = controller.reduce(
            ScreenAssistState(ScreenAssistPhase.ACTIVE, "active"),
            ScreenAssistRealtimePhase.DEGRADED,
        )
        assertEquals(ScreenAssistUiPhase.DEGRADED, state.phase)
        assertFalse(state.canPause)
        assertFalse(state.canResume)
    }

    @Test fun tokenRequiredIsBlocked() {
        val state = controller.reduce(
            ScreenAssistState(ScreenAssistPhase.READY, "ready"),
            ScreenAssistRealtimePhase.TOKEN_REQUIRED,
        )
        assertEquals(ScreenAssistUiPhase.BLOCKED, state.phase)
        assertTrue(state.canStart)
    }
}
