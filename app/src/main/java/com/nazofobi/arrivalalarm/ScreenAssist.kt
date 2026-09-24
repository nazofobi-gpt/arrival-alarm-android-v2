package com.nazofobi.arrivalalarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

/**
 * Consent-first boundary for the screen-assist vertical slice.
 *
 * Android owns the capture permission dialog. Arrival Alarm never starts a
 * capture session without a fresh RESULT_OK grant and never persists the
 * returned projection token. Frame transport/AI interpretation deliberately
 * sits behind [ScreenAssistSession] so the UI can expose an honest state even
 * before a remote inference provider is wired.
 */
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
    STOPPED,
}

data class ScreenAssistState(
    val phase: ScreenAssistPhase = ScreenAssistPhase.IDLE,
    val message: String = "Ekran asistanı kapalı",
)

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

    fun stop() {
        grant = null
        state = ScreenAssistState(
            ScreenAssistPhase.STOPPED,
            "Ekran asistanı durduruldu • paylaşım izni unutuldu",
        )
    }
}
