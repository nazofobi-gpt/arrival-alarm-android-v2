package com.nazofobi.arrivalalarm

import org.json.JSONObject

/**
 * Mobile-side boundary for the Screen Assist Realtime broker.
 *
 * The app receives only a short-lived client secret from the application's
 * backend. A standard OpenAI API key must never cross this boundary.
 */
data class ScreenAssistEphemeralToken(
    val value: String,
    val expiresAtEpochSeconds: Long,
) {
    fun isUsable(nowEpochSeconds: Long, minimumRemainingSeconds: Long = 30): Boolean =
        value.isNotBlank() && expiresAtEpochSeconds - nowEpochSeconds >= minimumRemainingSeconds
}

class ScreenAssistBrokerResponseParser {
    fun parse(json: String): ScreenAssistEphemeralToken {
        val root = JSONObject(json)
        check(!root.has("api_key")) { "Broker response must not expose a standard API key" }
        check(!root.has("apiKey")) { "Broker response must not expose a standard API key" }

        val secret = when {
            root.has("value") -> root
            root.has("client_secret") -> root.get("client_secret")
            root.has("clientSecret") -> root.get("clientSecret")
            else -> error("Missing client secret")
        }

        val value: String
        val expiresAt: Long
        if (secret is JSONObject) {
            value = secret.optString("value")
            expiresAt = secret.optLong(
                "expires_at",
                secret.optLong(
                    "expiresAt",
                    root.optLong("expires_at", root.optLong("expiresAt", 0L)),
                ),
            )
        } else {
            value = secret.toString()
            expiresAt = root.optLong("expires_at", root.optLong("expiresAt", 0L))
        }
        check(value.isNotBlank()) { "Empty client secret" }
        check(expiresAt > 0L) { "Missing client-secret expiry" }
        return ScreenAssistEphemeralToken(value, expiresAt)
    }
}

enum class ScreenAssistRealtimePhase {
    IDLE,
    TOKEN_REQUIRED,
    READY,
    CONNECTED,
    DEGRADED,
    BLOCKED,
    STOPPED,
}

/**
 * Fail-closed Realtime lifecycle. Token values are intentionally never exposed
 * through state/status strings so logging UI state cannot leak credentials.
 */
class ScreenAssistRealtimeSession(
    private val minimumTokenLifetimeSeconds: Long = 30,
) {
    var phase: ScreenAssistRealtimePhase = ScreenAssistRealtimePhase.IDLE
        private set
    var status: String = "Realtime kapalı"
        private set
    private var token: ScreenAssistEphemeralToken? = null

    fun requireToken() {
        token = null
        phase = ScreenAssistRealtimePhase.TOKEN_REQUIRED
        status = "Kısa ömürlü oturum anahtarı gerekli"
    }

    fun acceptToken(candidate: ScreenAssistEphemeralToken, nowEpochSeconds: Long): Boolean {
        if (!candidate.isUsable(nowEpochSeconds, minimumTokenLifetimeSeconds)) {
            token = null
            phase = ScreenAssistRealtimePhase.BLOCKED
            status = "Oturum anahtarı geçersiz veya süresi yetersiz"
            return false
        }
        token = candidate
        phase = ScreenAssistRealtimePhase.READY
        status = "Realtime bağlantısı hazır"
        return true
    }

    fun connect(nowEpochSeconds: Long): Boolean {
        val current = token
        if (current == null || !current.isUsable(nowEpochSeconds, minimumTokenLifetimeSeconds)) {
            requireToken()
            return false
        }
        phase = ScreenAssistRealtimePhase.CONNECTED
        status = "Realtime bağlı"
        return true
    }

    fun networkLost() {
        if (phase == ScreenAssistRealtimePhase.CONNECTED) {
            phase = ScreenAssistRealtimePhase.DEGRADED
            status = "Bağlantı kesildi; kare gönderimi durdu"
        }
    }

    fun tokenExpired() {
        token = null
        phase = ScreenAssistRealtimePhase.TOKEN_REQUIRED
        status = "Oturum anahtarı yenilenmeli"
    }

    fun stop() {
        token = null
        phase = ScreenAssistRealtimePhase.STOPPED
        status = "Realtime durduruldu"
    }
}
