package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistAppContextTest {
    private class Gateway(private val valid: Boolean = true) : ScreenAssistCommandGateway {
        var applied = false
        override fun validate(command: ScreenAssistCommand) = valid
        override fun apply(command: ScreenAssistCommand): ScreenAssistCommandResult {
            applied = true
            return ScreenAssistCommandResult(true, false, "applied")
        }
    }

    @Test fun reversibleCommandUsesValidatedGateway() {
        val gateway = Gateway()
        val result = ScreenAssistCommandPolicy().execute(ScreenAssistCommand.SetDestination("stop-1"), false, gateway)
        assertTrue(result.applied)
        assertTrue(gateway.applied)
    }

    @Test fun journeyChangeRequiresConfirmation() {
        val gateway = Gateway()
        val result = ScreenAssistCommandPolicy().execute(ScreenAssistCommand.SelectJourney("journey-1"), false, gateway)
        assertFalse(result.applied)
        assertTrue(result.requiresConfirmation)
        assertFalse(gateway.applied)
    }

    @Test fun invalidCommandFailsClosed() {
        val gateway = Gateway(valid = false)
        val result = ScreenAssistCommandPolicy().execute(ScreenAssistCommand.SetOrigin("missing"), true, gateway)
        assertFalse(result.applied)
        assertFalse(gateway.applied)
    }
}
