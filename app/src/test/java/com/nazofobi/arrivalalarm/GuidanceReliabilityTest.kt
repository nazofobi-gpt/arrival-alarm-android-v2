package com.nazofobi.arrivalalarm

import org.junit.Assert.*
import org.junit.Test

class GuidanceReliabilityTest {
    private class RecordingSpeaker(var available: Boolean = true) : GuidanceSpeaker {
        val spoken = mutableListOf<GuidanceUtterance>()
        override fun speak(utterance: GuidanceUtterance): Boolean {
            if (!available) return false
            spoken += utterance
            return true
        }
    }

    @Test fun exactStopAndDestinationTextUsesLocaleAndSuppressesDuplicates() {
        val speaker = RecordingSpeaker()
        val controller = GuidanceReliabilityController(speaker)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        val stop = GuidanceUtterance("stop:am-brill", "Sıradaki durak Am Brill", "tr-TR")
        assertTrue(controller.requestGuidance(stop))
        assertEquals("Sıradaki durak Am Brill", speaker.spoken.single().text)
        assertEquals("tr-TR", speaker.spoken.single().localeTag)
        assertFalse(controller.requestGuidance(stop))
        assertEquals(1, speaker.spoken.size)

        val destination = GuidanceUtterance("dest:airport", "Hedef Flughafen Bremen", "tr-TR")
        assertTrue(controller.requestGuidance(destination))
        assertEquals("Hedef Flughafen Bremen", speaker.spoken.last().text)
    }

    @Test fun deterministicVoiceFallbackPolicy() {
        assertEquals(VoiceChoice.DESIRED_LOCALE, TtsVoicePolicy.choose(true, true))
        assertEquals(VoiceChoice.DEVICE_FALLBACK, TtsVoicePolicy.choose(false, true))
        assertEquals(VoiceChoice.UNAVAILABLE, TtsVoicePolicy.choose(false, false))
    }

    @Test fun guidanceLanguageProvidesLocalizedDestinationTextAndStableLocaleFallback() {
        assertEquals("Hedef Bremen Hbf", GuidanceLanguage.TURKISH.destinationText("Bremen Hbf"))
        assertEquals("Ziel Bremen Hbf", GuidanceLanguage.GERMAN.destinationText("Bremen Hbf"))
        assertEquals("Destination Bremen Hbf", GuidanceLanguage.ENGLISH.destinationText("Bremen Hbf"))
        assertEquals("de-DE", GuidanceLanguage.fromLocaleTag("de-DE").localeTag)
        assertEquals(GuidanceLanguage.TURKISH, GuidanceLanguage.fromLocaleTag("fr-FR"))
        assertEquals(GuidanceLanguage.TURKISH, GuidanceLanguage.fromLocaleTag(null))
    }

    @Test fun deniedPartialGrantedRecoveryPreservesPendingUtterance() {
        val speaker = RecordingSpeaker()
        val store = InMemoryGuidanceStateStore()
        val controller = GuidanceReliabilityController(speaker, store)
        val u = GuidanceUtterance("stop:1", "Sıradaki durak Bremen Hbf", "tr-TR")

        controller.onPermissions(GuidancePermissionSnapshot(false, false, false))
        assertEquals(GuidancePermissionState.DENIED, controller.state.permissionState)
        assertFalse(controller.requestGuidance(u))
        assertNotNull(controller.state.pending)

        controller.onPermissions(GuidancePermissionSnapshot(true, true, false))
        assertEquals(GuidancePermissionState.PARTIAL, controller.state.permissionState)
        assertEquals(1, speaker.spoken.size)
        assertNull(controller.state.pending)

        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertEquals(GuidancePermissionState.GRANTED, controller.state.permissionState)
        assertFalse(controller.requestGuidance(u))
        assertEquals(1, speaker.spoken.size)
    }

    @Test fun bluetoothDisconnectReconnectKeepsOwnershipWithoutDuplicate() {
        val speaker = RecordingSpeaker()
        val controller = GuidanceReliabilityController(speaker)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        controller.onAudioRoute(GuidanceAudioRoute.BLUETOOTH_CONNECTED)
        val u = GuidanceUtterance("stop:2", "Sıradaki durak Am Brill", "tr-TR")
        assertTrue(controller.requestGuidance(u))
        controller.onAudioRoute(GuidanceAudioRoute.BLUETOOTH_DISCONNECTED)
        assertEquals("stop:2", controller.state.lastSpokenKey)
        controller.onAudioRoute(GuidanceAudioRoute.BLUETOOTH_CONNECTED)
        assertFalse(controller.requestGuidance(u))
        assertEquals(1, speaker.spoken.size)
    }

    @Test fun processRecreationRestoresPendingAndLastSpokenState() {
        val speaker = RecordingSpeaker()
        val store = InMemoryGuidanceStateStore()
        val first = GuidanceReliabilityController(speaker, store)
        first.onPermissions(GuidancePermissionSnapshot(false, false, false))
        val pending = GuidanceUtterance("dest:x", "Hedef Flughafen Bremen", "tr-TR")
        first.requestGuidance(pending)

        val recreated = GuidanceReliabilityController(speaker, store)
        assertEquals("dest:x", recreated.state.pending?.key)
        recreated.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertEquals("dest:x", recreated.state.lastSpokenKey)
        assertNull(recreated.state.pending)

        val again = GuidanceReliabilityController(speaker, store)
        assertEquals("dest:x", again.state.lastSpokenKey)
        assertFalse(again.requestGuidance(pending))
    }

    @Test fun ttsFailureKeepsPendingForRecovery() {
        val speaker = RecordingSpeaker(available = false)
        val controller = GuidanceReliabilityController(speaker)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        val u = GuidanceUtterance("stop:3", "Sıradaki durak Flughafen Bremen", "tr-TR")
        assertFalse(controller.requestGuidance(u))
        assertEquals("stop:3", controller.state.pending?.key)
        speaker.available = true
        assertTrue(controller.retryPending())
        assertEquals("stop:3", controller.state.lastSpokenKey)
    }
}
