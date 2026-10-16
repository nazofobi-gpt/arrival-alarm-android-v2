package com.nazofobi.arrivalalarm

import java.util.Locale

enum class GuidancePermissionState { DENIED, PARTIAL, GRANTED }
enum class GuidanceAudioRoute { DEVICE, BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED }
enum class VoiceChoice { DESIRED_LOCALE, DEVICE_FALLBACK, UNAVAILABLE }

enum class GuidanceLanguage(
    val localeTag: String,
    val displayName: String,
    private val destinationPrefix: String,
) {
    TURKISH("tr-TR", "Türkçe", "Hedef"),
    GERMAN("de-DE", "Deutsch", "Ziel"),
    ENGLISH("en-US", "English", "Destination");

    fun destinationText(label: String): String = "$destinationPrefix $label"

    companion object {
        fun fromLocaleTag(localeTag: String?): GuidanceLanguage {
            val normalized = localeTag?.trim()?.replace('_', '-').orEmpty()
            val language = Locale.forLanguageTag(normalized).language
            return values().firstOrNull {
                it.localeTag.substringBefore('-').equals(language, ignoreCase = true)
            } ?: TURKISH
        }
    }
}

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
    val pendingSinceEpochMillis: Long? = null,
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
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        // A next-stop instruction is time-sensitive; never replay it hours later.
        const val MAX_PENDING_AGE_MILLIS = 2 * 60_000L
    }
    var state: GuidanceReliabilityState = try {
        store.load() ?: GuidanceReliabilityState()
    } catch (_: RuntimeException) {
        // Broken persistence must not prevent in-session TTS guidance.
        GuidanceReliabilityState()
    }
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
            update(state.copy(
                pending = utterance,
                pendingSinceEpochMillis = if (state.pending == utterance) {
                    state.pendingSinceEpochMillis ?: nowEpochMillis()
                } else {
                    nowEpochMillis()
                },
                status = "Guidance beklemede • izin kurtarma gerekli",
            ))
            return false
        }
        return deliver(utterance)
    }

    fun retryPending(): Boolean {
        val pending = state.pending ?: return false
        val queuedAt = state.pendingSinceEpochMillis
        val now = nowEpochMillis()
        if (queuedAt == null || queuedAt <= 0L || now < queuedAt ||
            now - queuedAt > MAX_PENDING_AGE_MILLIS
        ) {
            update(state.copy(
                pending = null,
                pendingSinceEpochMillis = null,
                status = "Eski durak anonsu atlandı",
            ))
            return false
        }
        if (state.permissionState == GuidancePermissionState.DENIED) return false
        return deliver(pending)
    }

    private fun deliver(utterance: GuidanceUtterance): Boolean {
        // A disconnected Android TTS engine can throw during route changes.
        // Keep the original queued timestamp so exception retries cannot
        // make an expired stop instruction live forever.
        val spoken = try {
            speaker.speak(utterance)
        } catch (_: RuntimeException) {
            false
        }
        update(
            if (spoken) state.copy(
                lastSpokenKey = utterance.key,
                pending = null,
                pendingSinceEpochMillis = null,
                status = when (state.audioRoute) {
                    GuidanceAudioRoute.BLUETOOTH_CONNECTED -> "Guidance Bluetooth üzerinden gönderildi"
                    GuidanceAudioRoute.BLUETOOTH_DISCONNECTED, GuidanceAudioRoute.DEVICE ->
                        "Guidance cihaz TTS üzerinden gönderildi"
                },
            ) else state.copy(
                pending = utterance,
                pendingSinceEpochMillis = if (state.pending == utterance) {
                    state.pendingSinceEpochMillis ?: nowEpochMillis()
                } else {
                    nowEpochMillis()
                },
                status = "TTS kullanılamıyor • guidance beklemede",
            )
        )
        return spoken
    }

    private fun update(newState: GuidanceReliabilityState) {
        state = newState
        try {
            store.save(newState)
        } catch (_: RuntimeException) {
            // Keep the in-memory guidance state usable during storage failures.
        }
    }
}
