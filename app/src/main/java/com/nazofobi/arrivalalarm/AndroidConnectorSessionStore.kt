package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the device OAuth access token encrypted with an Android Keystore AES key.
 *
 * The gateway URL and token expiry are non-secret metadata. The bearer token is never written
 * to SharedPreferences in plaintext and this class never logs it. [clear] removes both the
 * encrypted session and the dedicated Keystore key so disconnect/revoke cleanup is local and
 * deterministic.
 */
class AndroidConnectorSessionStore(
    context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) : ConnectorSessionProvider {
    fun saveSession(session: ConnectorDeviceSession) {
        validate(session)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(session.accessToken.toByteArray(Charsets.UTF_8))
        val editor = preferences.edit()
            .putString(KEY_BASE_URL, session.baseUrl.trim().trimEnd('/'))
            .putString(KEY_TOKEN_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))

        session.expiresAtEpochSeconds?.let { editor.putLong(KEY_EXPIRES_AT, it) }
            ?: editor.remove(KEY_EXPIRES_AT)

        check(editor.commit()) { "Failed to persist connector session" }
    }

    override fun currentSession(): ConnectorDeviceSession? {
        val baseUrl = preferences.getString(KEY_BASE_URL, null)?.trim().orEmpty()
        val ciphertext = preferences.getString(KEY_TOKEN_CIPHERTEXT, null)
        val iv = preferences.getString(KEY_TOKEN_IV, null)
        if (baseUrl.isBlank() || ciphertext.isNullOrBlank() || iv.isNullOrBlank()) return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            val token = String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
            ConnectorDeviceSession(
                baseUrl = baseUrl,
                accessToken = token,
                expiresAtEpochSeconds = if (preferences.contains(KEY_EXPIRES_AT)) {
                    preferences.getLong(KEY_EXPIRES_AT, 0L)
                } else null,
            )
        }.getOrNull()
    }

    fun hasUsableSession(): Boolean =
        currentSession()?.let { !it.isExpired(nowEpochSeconds()) } == true

    fun clear() {
        check(preferences.edit().clear().commit()) { "Failed to clear connector session" }
        val keyStore = androidKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun validate(session: ConnectorDeviceSession) {
        require(session.accessToken.isNotBlank()) { "Connector access token is required" }
        val uri = URI(session.baseUrl.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Connector gateway must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "Connector gateway host is required" }
        session.expiresAtEpochSeconds?.let {
            require(it > nowEpochSeconds()) { "Connector access token is already expired" }
        }
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
        internal const val PREFERENCES_NAME = "connector_session_v1"
        private const val KEY_ALIAS = "arrival_alarm_connector_device_token_v1"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN_CIPHERTEXT = "token_ciphertext"
        private const val KEY_TOKEN_IV = "token_iv"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
