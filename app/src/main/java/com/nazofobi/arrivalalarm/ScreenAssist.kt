package com.nazofobi.arrivalalarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

/** Consent-first boundary for the screen-assist vertical slice. */
class ScreenAssistPermission(private val context: Context) {
    private val manager = context.getSystemService(MediaProjectionManager::class.java)

    fun createCaptureIntent(): Intent = manager.createScreenCaptureIntent()

    fun granted(resultCode: Int, data: Intent?): ScreenAssistGrant? =
        if (resultCode == Activity.RESULT_OK && data != null) ScreenAssistGrant(resultCode, data) else null
}

data class ScreenAssistGrant internal constructor(
    internal val resultCode: Int,
    internal val data: Intent,
)

enum class ScreenAssistPhase {
    IDLE,
    CONSENT_REQUIRED,
    READY,
    ACTIVE,
    PAUSED,
    DEGRADED,
    BLOCKED,
    STOPPED,
}

data class ScreenAssistState(
    val phase: ScreenAssistPhase = ScreenAssistPhase.IDLE,
    val message: String = "Ekran asistanı kapalı",
)

/**
 * Pure session state machine. The MediaProjection grant is memory-only and is
 * discarded on stop/revoke. Recovery events never silently restart capture.
 */
class ScreenAssistSession {
    var state: ScreenAssistState = ScreenAssistState()
        private set

    private var grant: ScreenAssistGrant? = null

    fun requestConsent() {
        grant = null
        state = ScreenAssistState(
            ScreenAssistPhase.CONSENT_REQUIRED,
            "Android ekran paylaşımı izni bekleniyor",
        )
    }

    fun acceptGrant(value: ScreenAssistGrant) {
        grant = value
        state = ScreenAssistState(
            ScreenAssistPhase.READY,
            "Ekran paylaşımı izni verildi • asistan başlatılabilir",
        )
    }

    fun start(): Boolean {
        if (grant == null) {
            requestConsent()
            return false
        }
        state = ScreenAssistState(
            ScreenAssistPhase.ACTIVE,
            "Ekran asistanı etkin • yalnız açık oturum süresince",
        )
        return true
    }

    fun pause(): Boolean {
        if (state.phase != ScreenAssistPhase.ACTIVE) return false
        state = ScreenAssistState(
            ScreenAssistPhase.PAUSED,
            "Ekran asistanı duraklatıldı • kare gönderimi kapalı",
        )
        return true
    }

    fun resume(): Boolean {
        if (state.phase != ScreenAssistPhase.PAUSED || grant == null) return false
        state = ScreenAssistState(
            ScreenAssistPhase.ACTIVE,
            "Ekran asistanı etkin • yalnız açık oturum süresince",
        )
        return true
    }

    fun networkUnavailable() {
        if (state.phase == ScreenAssistPhase.ACTIVE || state.phase == ScreenAssistPhase.PAUSED) {
            state = ScreenAssistState(
                ScreenAssistPhase.DEGRADED,
                "Bağlantı yok • ekran kareleri gönderilmiyor",
            )
        }
    }

    fun networkRecovered(): Boolean {
        if (state.phase != ScreenAssistPhase.DEGRADED || grant == null) return false
        state = ScreenAssistState(
            ScreenAssistPhase.READY,
            "Bağlantı geri geldi • yeniden başlatma kullanıcı onayı bekliyor",
        )
        return true
    }

    fun consentDenied() {
        grant = null
        state = ScreenAssistState(
            ScreenAssistPhase.BLOCKED,
            "Ekran paylaşımı izni verilmedi • yeniden başlatmak için tekrar deneyin",
        )
    }

    fun captureFailed() {
        grant = null
        state = ScreenAssistState(
            ScreenAssistPhase.BLOCKED,
            "Ekran yakalama başlatılamadı • yeniden izin gerekli",
        )
    }

    fun projectionRevoked() {
        grant = null
        state = ScreenAssistState(
            ScreenAssistPhase.BLOCKED,
            "Ekran paylaşımı Android tarafından sonlandırıldı • yeniden izin gerekli",
        )
    }

    fun stop() {
        grant = null
        state = ScreenAssistState(
            ScreenAssistPhase.STOPPED,
            "Ekran asistanı durduruldu • paylaşım izni unutuldu",
        )
    }
}