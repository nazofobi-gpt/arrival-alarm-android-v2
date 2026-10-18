package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ConnectorOAuthTest {
    private class MemorySessionStore : ConnectorSessionStore {
        var session: ConnectorDeviceSession? = null
        override fun currentSession() = session
        override fun saveSession(session: ConnectorDeviceSession) { this.session = session }
        override fun clear() { session = null }
    }

    private class MemoryTransactionStore : ConnectorOAuthTransactionStore {
        var transaction: ConnectorOAuthTransaction? = null
        override fun save(transaction: ConnectorOAuthTransaction) { this.transaction = transaction }
        override fun load() = transaction
        override fun clear() { transaction = null }
    }

    private class FakeTransport : ConnectorOAuthTransport {
        var metadata = ConnectorOAuthMetadata(
            authorizationEndpoint = "https://auth.example/authorize",
            tokenEndpoint = "https://auth.example/token",
            revocationEndpoint = "https://auth.example/revoke",
            clientId = "arrival-android",
            scopes = listOf("arrival.device", "offline_access"),
            redirectUri = CONNECTOR_OAUTH_REDIRECT_URI,
            resource = "https://connector.example/device",
            audience = "https://arrival-alarm-api",
            deviceIdParameter = "device_id",
        )
        var exchangedVerifier: String? = null
        var refreshedToken: String? = null
        val revoked = mutableListOf<Pair<String, String>>()

        override fun fetchMetadata(baseUrl: String) = metadata

        override fun exchangeCode(
            metadata: ConnectorOAuthMetadata,
            code: String,
            codeVerifier: String,
        ): ConnectorOAuthTokens {
            assertEquals("auth-code", code)
            exchangedVerifier = codeVerifier
            return ConnectorOAuthTokens(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresInSeconds = 3600,
            )
        }

        override fun refresh(
            metadata: ConnectorOAuthMetadata,
            refreshToken: String,
        ): ConnectorOAuthTokens {
            refreshedToken = refreshToken
            return ConnectorOAuthTokens(
                accessToken = "access-2",
                refreshToken = "refresh-2",
                expiresInSeconds = 7200,
            )
        }

        override fun revoke(
            metadata: ConnectorOAuthMetadata,
            token: String,
            tokenTypeHint: String,
        ) {
            revoked += token to tokenTypeHint
        }
    }

    private fun coordinator(
        sessions: MemorySessionStore,
        transactions: MemoryTransactionStore,
        transport: FakeTransport,
        now: () -> Long = { 1_000L },
    ) = ConnectorOAuthCoordinator(
        sessionStore = sessions,
        transactionStore = transactions,
        deviceIdentityProvider = object : ConnectorDeviceIdentityProvider {
            override fun deviceId() = "device-123"
        },
        transport = transport,
        nowEpochSeconds = now,
        randomBytes = { count -> ByteArray(count) { index -> (index + 1).toByte() } },
    )

    @Test fun authorizationUsesPkceStateDeviceBindingAndNoVerifierLeak() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)

        val url = oauth.beginAuthorization("https://connector.example/")
        val query = ConnectorOAuthCoordinator.parseQuery(URI(url).rawQuery)
        val transaction = transactions.transaction

        assertEquals("code", query["response_type"])
        assertEquals("arrival-android", query["client_id"])
        assertEquals(CONNECTOR_OAUTH_REDIRECT_URI, query["redirect_uri"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("device-123", query["device_id"])
        assertEquals("https://connector.example/device", query["resource"])
        assertEquals("https://arrival-alarm-api", query["audience"])
        assertEquals(transaction?.state, query["state"])
        assertNotNull(query["code_challenge"])
        assertFalse(url.contains(transaction?.codeVerifier.orEmpty()))
    }

    @Test fun callbackStateMismatchFailsClosedAndClearsTransaction() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)
        oauth.beginAuthorization("https://connector.example")

        val error = runCatching {
            oauth.completeAuthorization(
                CONNECTOR_OAUTH_REDIRECT_URI + "?code=auth-code&state=wrong"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertNotNull(transactions.transaction)
        assertNull(sessions.session)
    }

    @Test fun validCallbackPersistsRefreshableEncryptedSessionContract() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)
        val authUrl = oauth.beginAuthorization("https://connector.example")
        val state = ConnectorOAuthCoordinator.parseQuery(URI(authUrl).rawQuery).getValue("state")

        val session = oauth.completeAuthorization(
            CONNECTOR_OAUTH_REDIRECT_URI + "?code=auth-code&state=" + state
        )

        assertEquals("access-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
        assertEquals(4_600L, session.expiresAtEpochSeconds)
        assertEquals(session, sessions.session)
        assertNull(transactions.transaction)
        assertEquals(64, transport.exchangedVerifier?.length)
    }

    @Test fun expiredSessionRefreshesAndRotatesRefreshToken() {
        val sessions = MemorySessionStore().apply {
            session = ConnectorDeviceSession(
                baseUrl = "https://connector.example",
                accessToken = "expired",
                expiresAtEpochSeconds = 900,
                refreshToken = "refresh-1",
            )
        }
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)
        val provider = RefreshingConnectorSessionProvider(
            store = sessions,
            oauth = oauth,
            nowEpochSeconds = { 1_000L },
        )

        val session = provider.currentSession()

        assertEquals("refresh-1", transport.refreshedToken)
        assertEquals("access-2", session?.accessToken)
        assertEquals("refresh-2", session?.refreshToken)
        assertEquals(8_200L, session?.expiresAtEpochSeconds)
    }

    @Test fun disconnectRevokesRemoteTokensBeforeClearingLocalSession() {
        val sessions = MemorySessionStore().apply {
            session = ConnectorDeviceSession(
                baseUrl = "https://connector.example",
                accessToken = "access-1",
                expiresAtEpochSeconds = 4_000,
                refreshToken = "refresh-1",
            )
        }
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)

        assertTrue(oauth.disconnect())

        assertEquals(
            listOf("refresh-1" to "refresh_token", "access-1" to "access_token"),
            transport.revoked,
        )
        assertNull(sessions.session)
    }

    @Test fun reservedDeviceBindingParameterIsRejectedBeforeBrowserLaunch() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport().apply {
            metadata = metadata.copy(deviceIdParameter = "state")
        }
        val oauth = coordinator(sessions, transactions, transport)

        val error = runCatching {
            oauth.beginAuthorization("https://connector.example")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertNull(transactions.transaction)
    }


    @Test fun forgedErrorCallbackCannotConsumePendingAuthorization() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)
        oauth.beginAuthorization("https://connector.example")

        val error = runCatching {
            oauth.completeAuthorization(
                CONNECTOR_OAUTH_REDIRECT_URI + "?error=access_denied&state=forged"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertNotNull(transactions.transaction)
        assertNull(sessions.session)
    }


    @Test fun oauthProviderErrorIsRedactedAfterValidState() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)
        val authorizationUrl = oauth.beginAuthorization("https://connector.example")
        val state = ConnectorOAuthCoordinator.parseQuery(URI(authorizationUrl).rawQuery).getValue("state")
        val secret = "refresh_secret_and_precise_location_52.66"

        val failure = runCatching {
            oauth.completeAuthorization(
                CONNECTOR_OAUTH_REDIRECT_URI + "?state=" + state + "&error=" + secret
            )
        }.exceptionOrNull()

        assertEquals("connector_oauth_provider_error", failure?.message)
        assertFalse(failure.toString().contains(secret))
        assertNull(transactions.transaction)
        assertNull(sessions.session)
        assertNull(transport.exchangedVerifier)
    }

    @Test fun invalidCallbacksDoNotLeakOAuthCodeOrConsumePendingAuthorization() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)
        val authorizationUrl = oauth.beginAuthorization("https://connector.example")
        val state = ConnectorOAuthCoordinator.parseQuery(URI(authorizationUrl).rawQuery).getValue("state")
        val secret = "private_authorization_code"
        val callbacks = listOf(
            CONNECTOR_OAUTH_REDIRECT_URI + "?state=" + state + "&code=" + secret + "&invalid=%secret",
            CONNECTOR_OAUTH_REDIRECT_URI + "?state=" + state + "&code=" + secret + "#" + secret,
        )
        for (callback in callbacks) {
            val failure = runCatching { oauth.completeAuthorization(callback) }.exceptionOrNull()
            assertNotNull(failure)
            assertFalse(failure.toString().contains(secret))
            assertNotNull(transactions.transaction)
            assertNull(sessions.session)
            assertNull(transport.exchangedVerifier)
        }
    }

    @Test fun connectorOAuthRejectsSecretBearingBaseUrlsBeforeSavingTransaction() {
        val unsafe = listOf(
            "https://user:credential@connector.example",
            "https://connector.example?token=credential",
            "https://connector.example/#credential",
        )
        for (baseUrl in unsafe) {
            val transactions = MemoryTransactionStore()
            val oauth = coordinator(MemorySessionStore(), transactions, FakeTransport())
            val failure = runCatching { oauth.beginAuthorization(baseUrl) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertFalse(failure.toString().contains("credential"))
            assertNull(transactions.transaction)
        }
    }

    @Test fun connectorOAuthRejectsCredentialBearingOrFragmentedMetadataEndpoints() {
        val unsafe = listOf(
            "https://user:credential@auth.example/authorize",
            "https://auth.example/authorize#credential",
        )
        for (endpoint in unsafe) {
            val transactions = MemoryTransactionStore()
            val transport = FakeTransport().apply {
                metadata = metadata.copy(authorizationEndpoint = endpoint)
            }
            val oauth = coordinator(MemorySessionStore(), transactions, transport)
            val failure = runCatching {
                oauth.beginAuthorization("https://connector.example")
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertFalse(failure.toString().contains("credential"))
            assertNull(transactions.transaction)
        }
    }
    @Test fun duplicateOAuthCallbackParametersFailClosedWithoutLosingPendingTransaction() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport()
        val oauth = coordinator(sessions, transactions, transport)
        val authorizationUrl = oauth.beginAuthorization("https://connector.example")
        val state = ConnectorOAuthCoordinator.parseQuery(URI(authorizationUrl).rawQuery).getValue("state")

        val callbackQueries = listOf(
            "state=" + state + "&state=forged&code=auth-code",
            "state=forged&%73tate=" + state + "&code=auth-code",
            "state=" + state + "&code=auth-code&code=alternate",
            "state=" + state + "&error=access_denied&%65rror=another_error",
        )
        for (query in callbackQueries) {
            val failure = runCatching {
                oauth.completeAuthorization(CONNECTOR_OAUTH_REDIRECT_URI + "?" + query)
            }.exceptionOrNull()
            assertEquals("connector_oauth_callback_invalid", failure?.message)
            assertNotNull(transactions.transaction)
            assertNull(sessions.session)
            assertNull(transport.exchangedVerifier)
        }
    }

    @Test fun discoveredAuthorizationEndpointCannotOverrideOAuthParameters() {
        val unsafeAuthorizationUrls = listOf(
            "https://auth.example/authorize?state=server-supplied",
            "https://auth.example/authorize?%73tate=server-supplied",
            "https://auth.example/authorize?device_id=server-supplied",
            "https://auth.example/authorize?ui_locales=de&ui_locales=tr",
        )
        for (endpoint in unsafeAuthorizationUrls) {
            val transactions = MemoryTransactionStore()
            val transport = FakeTransport().apply {
                metadata = metadata.copy(authorizationEndpoint = endpoint)
            }
            val oauth = coordinator(MemorySessionStore(), transactions, transport)
            val failure = runCatching {
                oauth.beginAuthorization("https://connector.example")
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertFalse(failure.toString().contains("server-supplied"))
            assertNull(transactions.transaction)
        }
    }

    @Test fun discoveredAuthorizationEndpointAllowsHarmlessFixedQueryParameters() {
        val sessions = MemorySessionStore()
        val transactions = MemoryTransactionStore()
        val transport = FakeTransport().apply {
            metadata = metadata.copy(
                authorizationEndpoint = "https://auth.example/authorize?ui_locales=de"
            )
        }
        val oauth = coordinator(sessions, transactions, transport)
        val query = ConnectorOAuthCoordinator.parseQuery(
            URI(oauth.beginAuthorization("https://connector.example")).rawQuery
        )
        assertEquals("de", query["ui_locales"])
        assertEquals(transactions.transaction?.state, query["state"])
        assertNotNull(query["code_challenge"])
    }

}
