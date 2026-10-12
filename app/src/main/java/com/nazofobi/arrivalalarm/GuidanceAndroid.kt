package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

class SharedPreferencesGuidanceStateStore(
    prefs: SharedPreferences,
) : GuidanceStateStore {
    private val p = prefs

    override fun load(): GuidanceReliabilityState? {
        if (!p.contains("permission")) return null
        val pending = if (p.contains("pending_key")) GuidanceUtterance(
            key = p.getString("pending_key", "") ?: "",
            text = p.getString("pending_text", "") ?: "",
            localeTag = p.getString("pending_locale", "tr-TR") ?: "tr-TR",
        ) else null
        return GuidanceReliabilityState(
            permissionState = runCatching {
                GuidancePermissionState.valueOf(p.getString("permission", GuidancePermissionState.DENIED.name)!!)
            }.getOrDefault(GuidancePermissionState.DENIED),
            audioRoute = runCatching {
                GuidanceAudioRoute.valueOf(p.getString("route", GuidanceAudioRoute.DEVICE.name)!!)
            }.getOrDefault(GuidanceAudioRoute.DEVICE),
            lastSpokenKey = p.getString("last_key", null),
            pending = pending,
            status = p.getString("status", "İzinler bekleniyor") ?: "İzinler bekleniyor",
        )
    }

    override fun save(state: GuidanceReliabilityState) {
        p.edit().apply {
            putString("permission", state.permissionState.name)
            putString("route", state.audioRoute.name)
            putString("last_key", state.lastSpokenKey)
            putString("status", state.status)
            if (state.pending == null) {
                remove("pending_key"); remove("pending_text"); remove("pending_locale")
            } else {
                putString("pending_key", state.pending.key)
                putString("pending_text", state.pending.text)
                putString("pending_locale", state.pending.localeTag)
            }
        }.apply()
    }
}

class AndroidTextToSpeechSpeaker(context: Context) : GuidanceSpeaker, TextToSpeech.OnInitListener {
    private var initialized = false
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        initialized = status == TextToSpeech.SUCCESS
    }

    override fun speak(utterance: GuidanceUtterance): Boolean {
        if (!initialized) return false
        val desired = Locale.forLanguageTag(utterance.localeTag)
        val desiredResult = tts.setLanguage(desired)
        val desiredSupported = desiredResult != TextToSpeech.LANG_MISSING_DATA &&
            desiredResult != TextToSpeech.LANG_NOT_SUPPORTED
        val choice = TtsVoicePolicy.choose(desiredSupported, initialized)
        if (choice == VoiceChoice.UNAVAILABLE) return false
        if (choice == VoiceChoice.DEVICE_FALLBACK) {
            val fallback = tts.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) return false
        }
        return tts.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, null, utterance.key) == TextToSpeech.SUCCESS
    }

    fun shutdown() = tts.shutdown()
}

object AndroidGuidancePermissions {
    fun snapshot(context: Context): GuidancePermissionSnapshot {
        fun granted(permission: String) =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return GuidancePermissionSnapshot(
            fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
            bluetoothConnect = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT),
            foregroundServiceDeclared = true,
        )
    }

    fun runtimePermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    }.toTypedArray()
}

object AndroidGuidanceAudio {
    /**
     * Bluetooth output discovery requires BLUETOOTH_CONNECT on Android 12+.
     * Missing or concurrently revoked permission must not crash guidance setup.
     */
    fun currentRoute(context: Context): GuidanceAudioRoute {
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return GuidanceAudioRoute.DEVICE

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bluetooth = try {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                isBluetoothOutputType(it.type, Build.VERSION.SDK_INT)
            }
        } catch (_: SecurityException) {
            // Permission can be revoked between checking it and querying devices.
            false
        }
        return if (bluetooth) GuidanceAudioRoute.BLUETOOTH_CONNECTED else GuidanceAudioRoute.DEVICE
    }

    internal fun isBluetoothOutputType(type: Int, sdkInt: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (sdkInt >= 28 && type == AudioDeviceInfo.TYPE_HEARING_AID) ||
            (sdkInt >= 31 && (
                type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                ))
}
