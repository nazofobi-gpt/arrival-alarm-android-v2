package com.nazofobi.arrivalalarm

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

const val CONNECTOR_DEVICE_SCOPE = "arrival.device"
const val CONNECTOR_OAUTH_REDIRECT_URI = "com.nazofobi.arrivalalarm://oauth/callback"

data class ConnectorOAuthMetadata(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val revocationEndpoint: String,
    val clientId: String,
    val scopes: List<String>,
    val redirectUri: String,
    val resource: String? = null,
    val deviceIdParameter: String = "device_id",
)

data class ConnectorOAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
    val tokenType: String = "Bearer",
)

data class ConnectorOAuthTransaction(
    val baseUrl: String,
    val state: String,
    val codeVerifier: String,
    val deviceId: String,
    val createdAtEpochSeconds: Long,
)

interface ConnectorOAuthTransport {
    fun fetchMetadata(baseUrl: String): ConnectorOAuthMetadata
    fun exchangeCode(
        metadata: ConnectorOAuthMetadata,
        code: String,
        codeVerifier: String,
    ): ConnectorOAuthTokens
    fun refresh(
        metadata: ConnectorOAuthMetadata,
        refreshToken: String,
    ): ConnectorOAuthTokens
    fun revoke(
        metadata: ConnectorOAuthMetadata,
        token: String,
        tokenTypeHint: String,
    )
}

interface ConnectorOAuthTransactionStore {
    fun save(transaction: ConnectorOAuthTransaction)
    fun load(): ConnectorOAuthTransaction?
    fun clear()
}

interface ConnectorDeviceIdentityProvider {
    fun deviceId(): String
}

interface ConnectorSessionStore : ConnectorSessionProvider {
    fun saveSession(session: ConnectorDeviceSession)
    fun clear()
}

class ConnectorOAuthCoordinator(
    private val sessionStore: ConnectorSessionStore,
    private val transactionStore: ConnectorOAuthTransactionStore,
    private val deviceIdentityProvider: ConnectorDeviceIdentityProvider,
    private val transport: ConnectorOAuthTransport,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val randomBytes: (Int) -> ByteArray = { count ->
        ByteArray(count).also(SecureRandom()::nextBytes)
    },
) {
    @Synchronized
    fun beginAuthorization(baseUrl: String): String {
        val normalizedBaseUrl = requireHttpsBaseUrl(baseUrl)
        val metadata = validateMetadata(transport.fetchMetadata(normalizedBaseUrl))
        val state = base64UrlNoPadding(randomBytes(32))
        val verifier = base64UrlNoPadding(randomBytes(48))
        val challenge = base64UrlNoPadding(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        val deviceId = deviceIdentityProvider.deviceId().trim()
        require(deviceId.isNotBlank()) { "connector_device_id_missing" }

        transactionStore.save(
            ConnectorOAuthTransaction(
                baseUrl = normalizedBaseUrl,
                state = state,
                codeVerifier = verifier,
                deviceId = deviceId,
                createdAtEpochSeconds = nowEpochSeconds(),
            )
        )

        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to metadata.clientId,
            "redirect_uri" to metadata.redirectUri,
            "scope" to metadata.scopes.joinToString(" "),
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            metadata.deviceIdParameter to deviceId,
        )
        metadata.resource?.let { params["resource"] = it }
        return appendQuery(metadata.authorizationEndpoint, params)
    }

    @Synchronized
    fun completeAuthorization(callbackUri: String): ConnectorDeviceSession {
        val transaction = transactionStore.load()
            ?: throw IllegalStateException("connector_oauth_transaction_missing")
        if (nowEpochSeconds() - transaction.createdAtEpochSeconds > TRANSACTION_TTL_SECONDS) {
            transactionStore.clear()
            throw IllegalStateException("connector_oauth_transaction_expired")
        }

        val callback = URI(callbackUri)
        requireSameRedirect(callback, URI(CONNECTOR_OAUTH_REDIRECT_URI))
        val query = parseQuery(callback.rawQuery)
        query["error"]?.let { error ->
            transactionStore.clear()
            throw IllegalStateException("connector_oauth_" + error)
        }
        val returnedState = query["state"].orEmpty()
        if (!constantTimeEquals(transaction.state, returnedState)) {
            transactionStore.clear()
            throw IllegalStateException("connector_oauth_state_mismatch")
        }
        val code = query["code"].orEmpty()
        if (code.isBlank()) {
            transactionStore.clear()
            throw IllegalStateException("connector_oauth_code_missing")
        }

        val metadata = validateMetadata(transport.fetchMetadata(transaction.baseUrl))
        requireSameRedirect(URI(metadata.redirectUri), URI(CONNECTOR_OAUTH_REDIRECT_URI))
        val tokens = validateTokens(
            transport.exchangeCode(metadata, code, transaction.codeVerifier)
        )
        val session = ConnectorDeviceSession(
            baseUrl = transaction.baseUrl,
            accessToken = tokens.accessToken,
            expiresAtEpochSeconds = nowEpochSeconds() + tokens.expiresInSeconds,
            refreshToken = tokens.refreshToken,
        )
        sessionStore.saveSession(session)
        transactionStore.clear()
        return session
    }

    @Synchronized
    fun refreshSession(session: ConnectorDeviceSession): ConnectorDeviceSession? {
        val refreshToken = session.refreshToken?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val metadata = validateMetadata(transport.fetchMetadata(session.baseUrl))
            val tokens = validateTokens(transport.refresh(metadata, refreshToken))
            ConnectorDeviceSession(
                baseUrl = session.baseUrl,
                accessToken = tokens.accessToken,
                expiresAtEpochSeconds = nowEpochSeconds() + tokens.expiresInSeconds,
                refreshToken = tokens.refreshToken ?: refreshToken,
            ).also(sessionStore::saveSession)
        }.getOrNull()
    }

    @Synchronized
    fun disconnect(): Boolean {
        val session = sessionStore.currentSession()
        var remoteRevoked = session == null
        if (session != null) {
            remoteRevoked = runCatching {
                val metadata = validateMetadata(transport.fetchMetadata(session.baseUrl))
                session.refreshToken?.takeIf { it.isNotBlank() }?.let {
                    transport.revoke(metadata, it, "refresh_token")
                }
                transport.revoke(metadata, session.accessToken, "access_token")
                true
            }.getOrDefault(false)
        }
        transactionStore.clear()
        sessionStore.clear()
        return remoteRevoked
    }

    private fun validateMetadata(metadata: ConnectorOAuthMetadata): ConnectorOAuthMetadata {
        requireHttpsUrl(metadata.authorizationEndpoint, "authorization_endpoint")
        requireHttpsUrl(metadata.tokenEndpoint, "token_endpoint")
        requireHttpsUrl(metadata.revocationEndpoint, "revocation_endpoint")
        require(metadata.clientId.isNotBlank()) { "connector_oauth_client_id_missing" }
        require(metadata.redirectUri == CONNECTOR_OAUTH_REDIRECT_URI) {
            "connector_oauth_redirect_mismatch"
        }
        require(CONNECTOR_DEVICE_SCOPE in metadata.scopes) {
            "connector_oauth_device_scope_missing"
        }
        require(metadata.deviceIdParameter.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) {
            "connector_oauth_device_parameter_invalid"
        }
        require(metadata.deviceIdParameter !in RESERVED_AUTHORIZATION_PARAMETERS) {
            "connector_oauth_device_parameter_reserved"
        }
        metadata.resource?.let { requireHttpsUrl(it, "resource") }
        return metadata
    }

    private fun validateTokens(tokens: ConnectorOAuthTokens): ConnectorOAuthTokens {
        require(tokens.accessToken.isNotBlank()) { "connector_oauth_access_token_missing" }
        require(tokens.expiresInSeconds > 0) { "connector_oauth_expiry_missing" }
        require(tokens.tokenType.equals("Bearer", ignoreCase = true)) {
            "connector_oauth_token_type_unsupported"
        }
        return tokens
    }

    companion object {
        private const val TRANSACTION_TTL_SECONDS = 10 * 60L
        private val RESERVED_AUTHORIZATION_PARAMETERS = setOf(
            "response_type",
            "client_id",
            "redirect_uri",
            "scope",
            "state",
            "code_challenge",
            "code_challenge_method",
            "resource",
        )

        internal fun base64UrlNoPadding(bytes: ByteArray): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            val out = StringBuilder((bytes.size * 4 + 2) / 3)
            var index = 0
            while (index + 2 < bytes.size) {
                val value = ((bytes[index].toInt() and 0xff) shl 16) or
                    ((bytes[index + 1].toInt() and 0xff) shl 8) or
                    (bytes[index + 2].toInt() and 0xff)
                out.append(alphabet[(value ushr 18) and 0x3f])
                out.append(alphabet[(value ushr 12) and 0x3f])
                out.append(alphabet[(value ushr 6) and 0x3f])
                out.append(alphabet[value and 0x3f])
                index += 3
            }
            val remaining = bytes.size - index
            if (remaining == 1) {
                val value = (bytes[index].toInt() and 0xff) shl 16
                out.append(alphabet[(value ushr 18) and 0x3f])
                out.append(alphabet[(value ushr 12) and 0x3f])
            } else if (remaining == 2) {
                val value = ((bytes[index].toInt() and 0xff) shl 16) or
                    ((bytes[index + 1].toInt() and 0xff) shl 8)
                out.append(alphabet[(value ushr 18) and 0x3f])
                out.append(alphabet[(value ushr 12) and 0x3f])
                out.append(alphabet[(value ushr 6) and 0x3f])
            }
            return out.toString()
        }

        internal fun appendQuery(base: String, params: Map<String, String>): String {
            requireHttpsUrl(base, "authorization_endpoint")
            val separator = if (base.contains("?")) "&" else "?"
            return base + separator + params.entries.joinToString("&") { entry ->
                percentEncode(entry.key) + "=" + percentEncode(entry.value)
            }
        }

        internal fun parseQuery(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrBlank()) return emptyMap()
            return rawQuery.split("&").mapNotNull { pair ->
                val index = pair.indexOf('=')
                val rawKey = if (index >= 0) pair.substring(0, index) else pair
                val rawValue = if (index >= 0) pair.substring(index + 1) else ""
                val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
                if (key.isBlank()) null else {
                    key to URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
                }
            }.toMap()
        }

        private fun percentEncode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("%7E", "~")

        private fun constantTimeEquals(expected: String, actual: String): Boolean =
            MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.UTF_8),
                actual.toByteArray(StandardCharsets.UTF_8),
            )

        private fun requireSameRedirect(actual: URI, expected: URI) {
            require(actual.scheme.equals(expected.scheme, ignoreCase = true)) {
                "connector_oauth_redirect_mismatch"
            }
            require(actual.authority == expected.authority && actual.path == expected.path) {
                "connector_oauth_redirect_mismatch"
            }
        }

        private fun requireHttpsBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            requireHttpsUrl(trimmed, "base_url")
            return trimmed
        }

        private fun requireHttpsUrl(value: String, name: String) {
            val uri = runCatching { URI(value) }.getOrNull()
            require(
                uri != null &&
                    uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank()
            ) {
                "connector_oauth_" + name + "_https_required"
            }
        }
    }
}

class RefreshingConnectorSessionProvider(
    private val store: ConnectorSessionStore,
    private val oauth: ConnectorOAuthCoordinator,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val retryCooldownSeconds: Long = 60,
) : ConnectorSessionProvider {
    private var lastRefreshAttemptEpochSeconds: Long? = null

    @Synchronized
    override fun currentSession(): ConnectorDeviceSession? {
        val session = store.currentSession() ?: return null
        val now = nowEpochSeconds()
        if (!session.isExpired(now, clockSkewSeconds = 90)) return session

        val lastAttempt = lastRefreshAttemptEpochSeconds
        if (lastAttempt != null && now - lastAttempt < retryCooldownSeconds) return session
        lastRefreshAttemptEpochSeconds = now
        return oauth.refreshSession(session) ?: session
    }
}
