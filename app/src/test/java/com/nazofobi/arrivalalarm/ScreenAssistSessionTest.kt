package com.nazofobi.arrivalalarm

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistSessionTest {
    private fun grant() = ScreenAssistGrant(Activity.RESULT_OK, Intent("screen-assist-test"))

    @Test
    fun startWithoutGrantRequiresConsent() {
        val session = ScreenAssistSession()
        assertFalse(session.start())
        assertEquals(ScreenAssistPhase.CONSENT_REQUIRED, session.state.phase)
    }

    @Test
    fun grantAllowsOneExplicitSessionAndStopForgetsIt() {
        val session = ScreenAssistSession()
        session.acceptGrant(grant())
        assertTrue(session.start())
        assertEquals(ScreenAssistPhase.ACTIVE, session.state.phase)
        session.stop()
        assertEquals(ScreenAssistPhase.STOPPED, session.state.phase)
        assertFalse(session.start())
        assertEquals(ScreenAssistPhase.CONSENT_REQUIRED, session.state.phase)
    }

    @Test
    fun pauseStopsSendingUntilExplicitResume() {
        val session = ScreenAssistSession()
        session.acceptGrant(grant())
        session.start()
        assertTrue(session.pause())
        assertEquals(ScreenAssistPhase.PAUSED, session.state.phase)
        assertTrue(session.resume())
        assertEquals(ScreenAssistPhase.ACTIVE, session.state.phase)
    }

    @Test
    fun networkRecoveryDoesNotSilentlyRestartCapture() {
        val session = ScreenAssistSession()
        session.acceptGrant(grant())
        session.start()
        session.networkUnavailable()
        assertEquals(ScreenAssistPhase.DEGRADED, session.state.phase)
        assertTrue(session.networkRecovered())
        assertEquals(ScreenAssistPhase.READY, session.state.phase)
    }

    @Test
    fun projectionRevokeForgetsGrantAndRequiresFreshConsent() {
        val session = ScreenAssistSession()
        session.acceptGrant(grant())
        session.start()
        session.projectionRevoked()
        assertEquals(ScreenAssistPhase.BLOCKED, session.state.phase)
        assertFalse(session.start())
        assertEquals(ScreenAssistPhase.CONSENT_REQUIRED, session.state.phase)
    }
}