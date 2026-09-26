package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidConnectorDeviceIdentityStore(
    context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ),
) : ConnectorDeviceIdentityProvider {
    @Synchronized
    override fun deviceId(): String {
        preferences.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val created = UUID.randomUUID().toString()
        check(preferences.edit().putString(KEY_DEVICE_ID, created).commit()) {
            "Failed to persist connector device identity"
        }
        return created
    }

    companion object {
        private const val PREFERENCES_NAME = "connector_device_identity_v1"
        private const val KEY_DEVICE_ID = "device_id"
    }
}

class AndroidConnectorOAuthTransactionStore(
    context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ),
) : ConnectorOAuthTransactionStore {
    override fun save(transaction: ConnectorOAuthTransaction) {
        val encrypted = encrypt(transaction.codeVerifier)
        check(
            preferences.edit()
                .clear()
                .putString(KEY_BASE_URL, transaction.baseUrl)
                .putString(KEY_STATE, transaction.state)
                .putString(KEY_DEVICE_ID, transaction.deviceId)
                .putLong(KEY_CREATED_AT, transaction.createdAtEpochSeconds)
                .putString(KEY_VERIFIER_CIPHERTEXT, encrypted.ciphertext)
                .putString(KEY_VERIFIER_IV, encrypted.iv)
                .commit()
        ) {
            "Failed to persist connector OAuth transaction"
        }
    }

    override fun load(): ConnectorOAuthTransaction? {
        val baseUrl = preferences.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val state = preferences.getString(KEY_STATE, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val deviceId = preferences.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val ciphertext = preferences.getString(KEY_VERIFIER_CIPHERTEXT, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        val iv = preferences.getString(KEY_VERIFIER_IV, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        if (!preferences.contains(KEY_CREATED_AT)) return null

        return runCatching {
            ConnectorOAuthTransaction(
                baseUrl = baseUrl,
                state = state,
                codeVerifier = decrypt(ciphertext, iv),
                deviceId = deviceId,
                createdAtEpochSeconds = preferences.getLong(KEY_CREATED_AT, 0L),
            )
        }.getOrNull()
    }

    override fun clear() {
        check(preferences.edit().clear().commit()) {
            "Failed to clear connector OAuth transaction"
        }
        val keyStore = androidKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private data class EncryptedSecret(
        val ciphertext: String,
        val iv: String,
    )

    private fun encrypt(value: String): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return EncryptedSecret(
            ciphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    private fun decrypt(ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        return String(
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
            Charsets.UTF_8,
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = androidKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun androidKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        internal const val PREFERENCES_NAME = "connector_oauth_transaction_v1"
        private const val KEY_ALIAS = "arrival_alarm_connector_oauth_transaction_v1"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_STATE = "state"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_VERIFIER_CIPHERTEXT = "verifier_ciphertext"
        private const val KEY_VERIFIER_IV = "verifier_iv"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class HttpConnectorOAuthTransport(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000,
) : ConnectorOAuthTransport {
    override fun fetchMetadata(baseUrl: String): ConnectorOAuthMetadata {
        val normalized = baseUrl.trim().trimEnd('/')
        requireHttps(normalized)
        val connection = open(normalized + METADATA_PATH, "GET")
        return try {
            val body = readSuccessfulBody(connection)
            parseMetadata(body)
        } finally {
            connection.disconnect()
        }
    }

    override fun exchangeCode(
        metadata: ConnectorOAuthMetadata,
        code: String,
        codeVerifier: String,
    ): ConnectorOAuthTokens {
        val form = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "client_id" to metadata.clientId,
            "redirect_uri" to metadata.redirectUri,
            "code_verifier" to codeVerifier,
        )
        metadata.resource?.let { form["resource"] = it }
        return postToken(metadata.tokenEndpoint, form)
    }

    override fun refresh(
        metadata: ConnectorOAuthMetadata,
        refreshToken: String,
    ): ConnectorOAuthTokens {
        val form = linkedMapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to metadata.clientId,
            "scope" to metadata.scopes.joinToString(" "),
        )
        metadata.resource?.let { form["resource"] = it }
        return postToken(metadata.tokenEndpoint, form)
    }

    override fun revoke(
        metadata: ConnectorOAuthMetadata,
        token: String,
        tokenTypeHint: String,
    ) {
        postForm(
            metadata.revocationEndpoint,
            linkedMapOf(
                "token" to token,
                "token_type_hint" to tokenTypeHint,
                "client_id" to metadata.clientId,
            )
        )
    }

    private fun postToken(url: String, form: Map<String, String>): ConnectorOAuthTokens =
        parseTokens(postForm(url, form))

    private fun postForm(url: String, form: Map<String, String>): String {
        requireHttps(url)
        val connection = open(url, "POST").apply {
            doOutput = true
            setRequestProperty("content-type", "application/x-www-form-urlencoded")
            setRequestProperty("accept", "application/json")
        }
        return try {
            val body = form.entries.joinToString("&") { entry ->
                formEncode(entry.key) + "=" + formEncode(entry.value)
            }.toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            readSuccessfulBody(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        requireHttps(url)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            useCaches = false
        }
    }

    private fun readSuccessfulBody(connection: HttpURLConnection): String {
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.errorStream?.close()
            throw IllegalStateException("connector_oauth_http_" + status)
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun formEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val METADATA_PATH = "/.well-known/arrival-alarm-device-oauth"

        internal fun parseMetadata(body: String): ConnectorOAuthMetadata {
            val json = JSONObject(body)
            val scopes = when (val raw = json.opt("scopes")) {
                is JSONArray -> buildList {
                    for (index in 0 until raw.length()) {
                        raw.optString(index).takeIf { it.isNotBlank() }?.let { value ->
                            add(value)
                        }
                    }
                }
                is String -> raw.split(Regex("\\s+")).filter { it.isNotBlank() }
                else -> emptyList()
            }
            return ConnectorOAuthMetadata(
                authorizationEndpoint = json.getString("authorization_endpoint"),
                tokenEndpoint = json.getString("token_endpoint"),
                revocationEndpoint = json.getString("revocation_endpoint"),
                clientId = json.getString("client_id"),
                scopes = scopes,
                redirectUri = json.getString("redirect_uri"),
                resource = if (json.isNull("resource")) null else {
                    json.optString("resource").takeIf { it.isNotBlank() }
                },
                deviceIdParameter = json.optString("device_id_parameter", "device_id"),
            )
        }

        internal fun parseTokens(body: String): ConnectorOAuthTokens {
            val json = JSONObject(body)
            return ConnectorOAuthTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                expiresInSeconds = json.optLong("expires_in", 0L),
                tokenType = json.optString("token_type", "Bearer"),
            )
        }

        private fun requireHttps(value: String) {
            val uri = runCatching { URI(value) }.getOrNull()
            require(
                uri != null &&
                    uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank()
            ) {
                "connector_oauth_https_required"
            }
        }
    }
}
