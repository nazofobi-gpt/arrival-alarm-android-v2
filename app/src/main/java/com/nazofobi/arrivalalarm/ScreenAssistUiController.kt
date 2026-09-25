package com.nazofobi.arrivalalarm

/**
 * Single UI-facing recovery model for capture + Realtime. It deliberately
 * exposes no token, frame, or location payload.
 */
enum class ScreenAssistUiPhase { READY, CONNECTING, ACTIVE, PAUSED, DEGRADED, BLOCKED, STOPPED }

data class ScreenAssistUiState(
    val phase: ScreenAssistUiPhase,
    val message: String,
    val canStart: Boolean,
    val canPause: Boolean,
    val canResume: Boolean,
    val canStop: Boolean,
)

class ScreenAssistUiController {
    fun reduce(capture: ScreenAssistState, realtime: ScreenAssistRealtimePhase): ScreenAssistUiState {
        if (realtime == ScreenAssistRealtimePhase.CONNECTING) {
            return state(
                ScreenAssistUiPhase.CONNECTING,
                "Güvenli AI bağlantısı kuruluyor • ekran yakalama henüz başlamadı",
                canStop = true,
            )
        }
        if (capture.phase == ScreenAssistPhase.BLOCKED) {
            return state(ScreenAssistUiPhase.BLOCKED, capture.message, canStart = true)
        }
        if (realtime == ScreenAssistRealtimePhase.BLOCKED ||
            realtime == ScreenAssistRealtimePhase.TOKEN_REQUIRED
        ) {
            return state(
                ScreenAssistUiPhase.BLOCKED,
                "Realtime oturumu hazır değil • yeniden bağlantı gerekli",
                canStart = capture.phase == ScreenAssistPhase.READY ||
                    capture.phase == ScreenAssistPhase.STOPPED ||
                    capture.phase == ScreenAssistPhase.IDLE,
            )
        }
        if (capture.phase == ScreenAssistPhase.DEGRADED ||
            realtime == ScreenAssistRealtimePhase.DEGRADED
        ) {
            return state(
                ScreenAssistUiPhase.DEGRADED,
                "Bağlantı kesildi • kare gönderimi durdu",
                canStart = capture.phase == ScreenAssistPhase.READY,
            )
        }
        return when (capture.phase) {
            ScreenAssistPhase.ACTIVE -> state(
                ScreenAssistUiPhase.ACTIVE,
                "Ekran asistanı etkin • paylaşım göstergesi açık",
                canPause = true,
                canStop = true,
            )
            ScreenAssistPhase.PAUSED -> state(
                ScreenAssistUiPhase.PAUSED,
                "Ekran asistanı duraklatıldı • kare gönderimi kapalı",
                canResume = true,
                canStop = true,
            )
            ScreenAssistPhase.READY -> state(
                ScreenAssistUiPhase.READY,
                "Ekran paylaşımı hazır • kullanıcı başlatabilir",
                canStart = true,
            )
            ScreenAssistPhase.STOPPED, ScreenAssistPhase.IDLE -> state(
                ScreenAssistUiPhase.STOPPED,
                capture.message,
                canStart = true,
            )
            ScreenAssistPhase.CONSENT_REQUIRED -> state(
                ScreenAssistUiPhase.BLOCKED,
                capture.message,
                canStart = true,
            )
            ScreenAssistPhase.BLOCKED -> error("handled above")
            ScreenAssistPhase.DEGRADED -> error("handled above")
        }
    }

    private fun state(
        phase: ScreenAssistUiPhase,
        message: String,
        canStart: Boolean = false,
        canPause: Boolean = false,
        canResume: Boolean = false,
        canStop: Boolean = false,
    ) = ScreenAssistUiState(phase, message, canStart, canPause, canResume, canStop)
}
