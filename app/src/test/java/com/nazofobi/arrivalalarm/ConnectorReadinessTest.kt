package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorReadinessTest {
    @Test fun removedConfigurationNeverLooksConnectedWithStaleSession() {
        assertFalse(hasVerifiedConnectorConnection(configured = false, connected = true))
        assertFalse(hasVerifiedConnectorConnection(configured = false, connected = false))
    }

    @Test fun onlyConfiguredAndConnectedSessionIsVerified() {
        assertFalse(hasVerifiedConnectorConnection(configured = true, connected = false))
        assertTrue(hasVerifiedConnectorConnection(configured = true, connected = true))
    }
}
