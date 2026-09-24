package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistRealtimeTest {
    @Test
    fun parsesNestedEphemeralClientSecret() {
        val token = ScreenAssistBrokerResponseParser().parse(
            """{"client_secret":{"value":"ephemeral_test","expires_at":1060}}"""
        )
        assertEquals("ephemeral_test", token.value)
        assertEquals(1060L, token.expiresAtEpochSeconds)
        assertTrue(token.isUsable(1000))
    }

    @Test
    fun nearExpiryTokenFailsClosed() {
        val session = ScreenAssistRealtimeSession(minimumTokenLifetimeSeconds = 30)
        assertFalse(session.acceptToken(ScreenAssistEphemeralToken("ephemeral_test", 1029), 1000))
        assertEquals(ScreenAssistRealtimePhase.BLOCKED, session.phase)
    }

    @Test
    fun expiredTokenCannotReconnect() {
        val session = ScreenAssistRealtimeSession()
        assertTrue(session.acceptToken(ScreenAssistEphemeralToken("ephemeral_test", 1100), 1000))
        assertTrue(session.connect(1000))
        session.networkLost()
        assertEquals(ScreenAssistRealtimePhase.DEGRADED, session.phase)
        session.tokenExpired()
        assertFalse(session.connect(1100))
        assertEquals(ScreenAssistRealtimePhase.TOKEN_REQUIRED, session.phase)
    }

    @Test
    fun stopDropsTokenAndRequiresFreshBrokerToken() {
        val session = ScreenAssistRealtimeSession()
        assertTrue(session.acceptToken(ScreenAssistEphemeralToken("ephemeral_test", 1100), 1000))
        session.stop()
        assertFalse(session.connect(1000))
        assertEquals(ScreenAssistRealtimePhase.TOKEN_REQUIRED, session.phase)
    }
}
