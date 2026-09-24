package com.nazofobi.arrivalalarm

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistSessionTest {
    @Test
    fun startWithoutGrantRequiresConsent() {
        val session = ScreenAssistSession()

        assertFalse(session.start())
        assertEquals(ScreenAssistPhase.CONSENT_REQUIRED, session.state.phase)
    }

    @Test
    fun grantAllowsOneExplicitSessionAndStopForgetsIt() {
        val session = ScreenAssistSession()
        session.acceptGrant(ScreenAssistGrant(Activity.RESULT_OK, Intent("screen-assist-test")))

        assertTrue(session.start())
        assertEquals(ScreenAssistPhase.ACTIVE, session.state.phase)

        session.stop()
        assertEquals(ScreenAssistPhase.STOPPED, session.state.phase)
        assertFalse(session.start())
        assertEquals(ScreenAssistPhase.CONSENT_REQUIRED, session.state.phase)
    }
}
