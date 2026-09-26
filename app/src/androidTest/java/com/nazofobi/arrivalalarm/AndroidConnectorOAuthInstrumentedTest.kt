package com.nazofobi.arrivalalarm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidConnectorOAuthInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun pkceVerifierRoundTripsEncryptedAndTransactionClears() {
        val store = AndroidConnectorOAuthTransactionStore(context)
        store.clear()
        val transaction = ConnectorOAuthTransaction(
            baseUrl = "https://connector.example",
            state = "state-123",
            codeVerifier = "super-secret-pkce-verifier",
            deviceId = "device-123",
            createdAtEpochSeconds = 1_000,
        )

        store.save(transaction)

        assertEquals(transaction, store.load())
        val rawPreferences = context.getSharedPreferences(
            AndroidConnectorOAuthTransactionStore.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        ).all.toString()
        assertFalse(rawPreferences.contains("super-secret-pkce-verifier"))
        assertTrue(rawPreferences.contains("state-123"))

        store.clear()
        assertNull(store.load())
    }

    @Test fun generatedDeviceIdentityIsStableForTheInstall() {
        val first = AndroidConnectorDeviceIdentityStore(context).deviceId()
        val second = AndroidConnectorDeviceIdentityStore(context).deviceId()

        assertTrue(first.isNotBlank())
        assertEquals(first, second)
    }
}
