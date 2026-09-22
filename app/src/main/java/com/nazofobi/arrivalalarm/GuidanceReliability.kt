package com.nazofobi.arrivalalarm

enum class GuidancePermissionState { DENIED, PARTIAL, GRANTED }
enum class GuidanceAudioRoute { DEVICE, BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED }
enum class VoiceChoice { DESIRED_LOCALE, DEVICE_FALLBACK, UNAVAILABLE }

data class GuidancePermissionSnapshot(
    val fineLocation: Boolean,
    val notifications: Boolean,
    val bluetoothConnect: Boolean,
    val foregroundServiceDeclared: Boolean = true,
)

data class GuidanceUtterance(
    val key: String,
    val text: String,
    val localeTag: String,
)

data class GuidanceReliabilityState(
    val permissionState: GuidancePermissionState = GuidancePermissionState.DENIED,
    val audioRoute: GuidanceAudioRoute = GuidanceAudioRoute.DEVICE,
    val lastSpokenKey: String? = null,
    val pending: GuidanceUtterance? = null,
    val status: String = "İzinler bekleniyor",
)

interface GuidanceStateStore {
    fun load(): GuidanceReliabilityState?
    fun save(state: GuidanceReliabilityState)
}

class InMemoryGuidanceStateStore : GuidanceStateStore {
    private var saved: GuidanceReliabilityState? = null
    override fun load(): GuidanceReliabilityState? = saved
    override fun save(state: GuidanceReliabilityState) { saved = state }
}

interface GuidanceSpeaker {
    fun speak(utterance: GuidanceUtterance): Boolean
}

object TtsVoicePolicy {
    fun choose(desiredLocaleSupported: Boolean, deviceTtsAvailable: Boolean): VoiceChoice = when {
        desiredLocaleSupported -> VoiceChoice.DESIRED_LOCALE
        deviceTtsAvailable -> VoiceChoice.DEVICE_FALLBACK
        else -> VoiceChoice.UNAVAILABLE
    }
}

class GuidanceReliabilityController(
    private val speaker: GuidanceSpeaker,
    private val store: GuidanceStateStore = InMemoryGuidanceStateStore(),
) {
    var state: GuidanceReliabilityState = store.load() ?: GuidanceReliabilityState()
        private set

    fun onPermissions(snapshot: GuidancePermissionSnapshot) {
        val level = when {
            !snapshot.foregroundServiceDeclared || !snapshot.fineLocation || !snapshot.notifications ->
                GuidancePermissionState.DENIED
            snapshot.bluetoothConnect -> GuidancePermissionState.GRANTED
            else -> GuidancePermissionState.PARTIAL
        }
        update(
            state.copy(
                permissionState = level,
                status = when (level) {
                    GuidancePermissionState.DENIED -> "Konum/bildirim izni gerekli • FGS başlatılmaz"
                    GuidancePermissionState.PARTIAL -> "Temel izinler hazır • Bluetooth izni yok, cihaz sesi fallback"
                    GuidancePermissionState.GRANTED -> "Guidance izinleri hazır"
                },
            )
        )
        retryPending()
    }

    fun onAudioRoute(route: GuidanceAudioRoute) {
        val status = when (route) {
            GuidanceAudioRoute.BLUETOOTH_CONNECTED -> "Bluetooth ses rotası bağlı"
            GuidanceAudioRoute.BLUETOOTH_DISCONNECTED -> "Bluetooth ayrıldı • cihaz sesi fallback"
            GuidanceAudioRoute.DEVICE -> "Cihaz ses rotası"
        }
        update(state.copy(audioRoute = route, status = status))
        retryPending()
    }

    fun requestGuidance(utterance: GuidanceUtterance): Boolean {
        if (utterance.key == state.lastSpokenKey) return false
        if (state.permissionState == GuidancePermissionState.DENIED) {
            update(state.copy(pending = utterance, status = "Guidance beklemede • izin kurtarma gerekli"))
            return false
        }
        return deliver(utterance)
    }

    fun retryPending(): Boolean {
        val pending = state.pending ?: return false
        if (state.permissionState == GuidancePermissionState.DENIED) return false
        return deliver(pending)
    }

    private fun deliver(utterance: GuidanceUtterance): Boolean {
        val spoken = speaker.speak(utterance)
        update(
            if (spoken) state.copy(
                lastSpokenKey = utterance.key,
                pending = null,
                status = when (state.audioRoute) {
                    GuidanceAudioRoute.BLUETOOTH_CONNECTED -> "Guidance Bluetooth üzerinden gönderildi"
                    GuidanceAudioRoute.BLUETOOTH_DISCONNECTED, GuidanceAudioRoute.DEVICE ->
                        "Guidance cihaz TTS üzerinden gönderildi"
                },
            ) else state.copy(pending = utterance, status = "TTS kullanılamıyor • guidance beklemede")
        )
        return spoken
    }

    private fun update(newState: GuidanceReliabilityState) {
        state = newState
        store.save(newState)
    }
}
