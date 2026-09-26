package com.nazofobi.arrivalalarm

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidConnectorSessionStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AndroidConnectorSessionStore(context, nowEpochSeconds = { 1_000 })

    @After fun cleanup() {
        store.clear()
    }

    @Test fun tokenRoundTripsEncryptedAndDisconnectClearsSession() {
        store.clear()
        store.saveSession(
            ConnectorDeviceSession(
                baseUrl = "https://connector.example/",
                accessToken = "device-secret-token",
                expiresAtEpochSeconds = 2_000,
                refreshToken = "device-refresh-secret",
            )
        )

        val loaded = store.currentSession()
        assertEquals("https://connector.example", loaded?.baseUrl)
        assertEquals("device-secret-token", loaded?.accessToken)
        assertEquals(2_000L, loaded?.expiresAtEpochSeconds)
        assertEquals("device-refresh-secret", loaded?.refreshToken)
        assertTrue(store.hasUsableSession())

        val rawPreferences = context.getSharedPreferences(
            AndroidConnectorSessionStore.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        ).all.toString()
        assertFalse(rawPreferences.contains("device-secret-token"))
        assertFalse(rawPreferences.contains("device-refresh-secret"))

        store.clear()
        assertNull(store.currentSession())
    }

    @Test fun cleartextGatewayAndExpiredTokenAreRejected() {
        val cleartext = runCatching {
            store.saveSession(
                ConnectorDeviceSession(
                    baseUrl = "http://connector.example",
                    accessToken = "token",
                    expiresAtEpochSeconds = 2_000,
                )
            )
        }.exceptionOrNull()
        assertTrue(cleartext is IllegalArgumentException)

        val expired = runCatching {
            store.saveSession(
                ConnectorDeviceSession(
                    baseUrl = "https://connector.example",
                    accessToken = "token",
                    expiresAtEpochSeconds = 900,
                )
            )
        }.exceptionOrNull()
        assertTrue(expired is IllegalArgumentException)
    }
}
