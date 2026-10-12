package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidanceReliabilityInstrumentedTest {
    private class RecordingSpeaker : GuidanceSpeaker {
        val spoken = mutableListOf<GuidanceUtterance>()
        override fun speak(utterance: GuidanceUtterance): Boolean { spoken += utterance; return true }
    }

    @Test fun sharedPreferencesRestoresPendingAcrossProcessStyleRecreationOnApi36() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("guidance_g133_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = SharedPreferencesGuidanceStateStore(prefs)
        val speaker = RecordingSpeaker()

        val first = GuidanceReliabilityController(speaker, store)
        first.onPermissions(GuidancePermissionSnapshot(false, false, false))
        first.requestGuidance(GuidanceUtterance("stop:api36", "Sıradaki durak Am Brill", "tr-TR"))
        assertEquals("stop:api36", first.state.pending?.key)

        val recreated = GuidanceReliabilityController(speaker, SharedPreferencesGuidanceStateStore(prefs))
        assertEquals("stop:api36", recreated.state.pending?.key)
        recreated.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertEquals(1, speaker.spoken.size)
        assertEquals("stop:api36", recreated.state.lastSpokenKey)

        val second = GuidanceReliabilityController(speaker, SharedPreferencesGuidanceStateStore(prefs))
        assertFalse(second.requestGuidance(GuidanceUtterance("stop:api36", "Sıradaki durak Am Brill", "tr-TR")))
        assertEquals(1, speaker.spoken.size)
        prefs.edit().clear().commit()
    }

    @Test fun bluetoothOutputClassificationCoversBleSpeakersAndAccessibilityDevices() {
        assertTrue(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26))
        assertTrue(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26))
        assertFalse(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_HEARING_AID, 27))
        assertTrue(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_HEARING_AID, 28))
        assertFalse(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLE_HEADSET, 30))
        assertFalse(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLE_SPEAKER, 30))
        assertTrue(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLE_HEADSET, 31))
        assertTrue(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLE_SPEAKER, 31))
        assertFalse(AndroidGuidanceAudio.isBluetoothOutputType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 36))
    }

    @Test fun deniedBluetoothConnectNeverQueriesAudioOutputOnApi31Plus() {
        if (Build.VERSION.SDK_INT < 31) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deniedContext = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int =
                if (permission == Manifest.permission.BLUETOOTH_CONNECT)
                    PackageManager.PERMISSION_DENIED
                else
                    super.checkSelfPermission(permission)

            override fun getSystemService(name: String): Any? {
                if (name == Context.AUDIO_SERVICE) {
                    error("Audio output must not be queried without BLUETOOTH_CONNECT")
                }
                return super.getSystemService(name)
            }
        }
        assertEquals(GuidanceAudioRoute.DEVICE, AndroidGuidanceAudio.currentRoute(deniedContext))
    }

}
