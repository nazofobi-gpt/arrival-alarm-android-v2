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

    @Test fun blankGuidanceIdentityOrTextCannotReachTtsOrPendingStore() {
        val speaker = RecordingSpeaker()
        val store = InMemoryGuidanceStateStore()
        val controller = GuidanceReliabilityController(speaker, store)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))

        assertFalse(controller.requestGuidance(GuidanceUtterance("  ", "Next stop", "en-US")))
        assertFalse(controller.requestGuidance(GuidanceUtterance("stop:missing", "  ", "en-US")))
        assertTrue(speaker.spoken.isEmpty())
        assertNull(controller.state.pending)
        assertNull(controller.state.lastSpokenKey)

        controller.onPermissions(GuidancePermissionSnapshot(false, false, false))
        assertFalse(controller.requestGuidance(GuidanceUtterance("", "Destination", "en-US")))
        assertFalse(controller.requestGuidance(GuidanceUtterance("stop:blank", "", "en-US")))
        assertNull(controller.state.pending)
        assertNull(controller.state.pendingSinceEpochMillis)
    }

    @Test fun malformedRestoredPendingGuidanceIsDiscardedWithoutSpeech() {
        val speaker = RecordingSpeaker()
        val malformed = GuidanceUtterance("stop:unknown", "  ", "tr-TR")
        val store = object : GuidanceStateStore {
            var saved = GuidanceReliabilityState(
                pending = malformed,
                pendingSinceEpochMillis = 10_000L,
                permissionState = GuidancePermissionState.GRANTED,
            )
            override fun load(): GuidanceReliabilityState = saved
            override fun save(state: GuidanceReliabilityState) { saved = state }
        }
        val controller = GuidanceReliabilityController(
            speaker,
            store,
            nowEpochMillis = { 10_001L },
        )

        assertFalse(controller.retryPending())
        assertTrue(speaker.spoken.isEmpty())
        assertNull(controller.state.pending)
        assertNull(controller.state.pendingSinceEpochMillis)
        assertNull(store.saved.pending)
    }

    @Test fun deterministicVoiceFallbackPolicy() {
        assertEquals(VoiceChoice.DESIRED_LOCALE, TtsVoicePolicy.choose(true, true))
        assertEquals(VoiceChoice.DEVICE_FALLBACK, TtsVoicePolicy.choose(false, true))
        assertEquals(VoiceChoice.UNAVAILABLE, TtsVoicePolicy.choose(false, false))
        // An unavailable speech engine must fail closed even if a locale was previously supported.
        assertEquals(VoiceChoice.UNAVAILABLE, TtsVoicePolicy.choose(true, false))
    }

    @Test fun guidanceLanguageProvidesLocalizedDestinationTextAndStableLocaleFallback() {
        assertEquals("Hedef Bremen Hbf", GuidanceLanguage.TURKISH.destinationText("Bremen Hbf"))
        assertEquals("Ziel Bremen Hbf", GuidanceLanguage.GERMAN.destinationText("Bremen Hbf"))
        assertEquals("Destination Bremen Hbf", GuidanceLanguage.ENGLISH.destinationText("Bremen Hbf"))
        assertEquals("de-DE", GuidanceLanguage.fromLocaleTag("de-DE").localeTag)
        assertEquals(GuidanceLanguage.GERMAN, GuidanceLanguage.fromLocaleTag("de"))
        assertEquals(GuidanceLanguage.GERMAN, GuidanceLanguage.fromLocaleTag("de-AT"))
        assertEquals(GuidanceLanguage.GERMAN, GuidanceLanguage.fromLocaleTag("de_CH"))
        assertEquals(GuidanceLanguage.ENGLISH, GuidanceLanguage.fromLocaleTag("en-GB"))
        assertEquals(GuidanceLanguage.ENGLISH, GuidanceLanguage.fromLocaleTag("EN_us"))
        assertEquals(GuidanceLanguage.TURKISH, GuidanceLanguage.fromLocaleTag("tr-CY"))
        assertEquals(GuidanceLanguage.TURKISH, GuidanceLanguage.fromLocaleTag("fr-FR"))
        assertEquals(GuidanceLanguage.TURKISH, GuidanceLanguage.fromLocaleTag("not_a_locale"))
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

    @Test fun throwingTtsAdapterKeepsGuidancePendingAcrossControllerRecreation() {
        var engineAvailable = false
        val delivered = mutableListOf<GuidanceUtterance>()
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                if (!engineAvailable) throw IllegalStateException("TTS engine disconnected")
                delivered += utterance
                return true
            }
        }
        val store = InMemoryGuidanceStateStore()
        val controller = GuidanceReliabilityController(speaker, store)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        val stop = GuidanceUtterance("stop:retry", "Sıradaki durak Bremen Hbf", "tr-TR")

        assertFalse(controller.requestGuidance(stop))
        assertEquals(stop, controller.state.pending)
        assertNull(controller.state.lastSpokenKey)
        // A route-change retry must also tolerate an unavailable speech engine.
        controller.onAudioRoute(GuidanceAudioRoute.BLUETOOTH_DISCONNECTED)
        assertEquals(stop, controller.state.pending)
        assertTrue(delivered.isEmpty())

        engineAvailable = true
        val recreated = GuidanceReliabilityController(speaker, store)
        assertTrue(recreated.retryPending())
        assertEquals(listOf(stop), delivered)
        assertEquals(stop.key, recreated.state.lastSpokenKey)
        assertNull(recreated.state.pending)
        assertFalse(recreated.requestGuidance(stop))
        assertEquals(1, delivered.size)
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

    @Test fun expiredPendingAnnouncementIsNotSpokenAfterPermissionRecovery() {
        val speaker = RecordingSpeaker()
        val store = InMemoryGuidanceStateStore()
        var now = 10_000L
        val controller = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        val stop = GuidanceUtterance("stop:old", "Sıradaki durak Lohne", "tr-TR")

        controller.onPermissions(GuidancePermissionSnapshot(false, false, false))
        assertFalse(controller.requestGuidance(stop))
        assertEquals(10_000L, controller.state.pendingSinceEpochMillis)

        now += GuidanceReliabilityController.MAX_PENDING_AGE_MILLIS + 1L
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertTrue(speaker.spoken.isEmpty())
        assertNull(controller.state.pending)
        assertNull(controller.state.pendingSinceEpochMillis)
    }

    @Test fun repeatedTtsFailureDoesNotRefreshPendingExpiry() {
        val speaker = RecordingSpeaker(available = false)
        var now = 100_000L
        val controller = GuidanceReliabilityController(
            speaker, nowEpochMillis = { now },
        )
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        val stop = GuidanceUtterance("stop:failed", "Sıradaki durak Bremen Hbf", "tr-TR")

        assertFalse(controller.requestGuidance(stop))
        assertEquals(100_000L, controller.state.pendingSinceEpochMillis)
        now += 90_000L
        assertFalse(controller.retryPending())
        assertEquals(100_000L, controller.state.pendingSinceEpochMillis)

        now += 30_001L
        speaker.available = true
        assertFalse(controller.retryPending())
        assertTrue(speaker.spoken.isEmpty())
        assertNull(controller.state.pending)
    }

    @Test fun validPendingAnnouncementSurvivesShortProcessRecreation() {
        val speaker = RecordingSpeaker()
        val store = InMemoryGuidanceStateStore()
        var now = 1_000_000L
        val first = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        first.onPermissions(GuidancePermissionSnapshot(false, false, false))
        val stop = GuidanceUtterance("stop:new", "Sıradaki durak Achim", "tr-TR")
        assertFalse(first.requestGuidance(stop))

        now += 45_000L
        val restored = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        restored.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertEquals(listOf(stop), speaker.spoken)
        assertNull(restored.state.pendingSinceEpochMillis)
        assertFalse(restored.retryPending())
    }

    @Test fun repeatedDeniedRequestCannotExtendPendingStopLifetime() {
        val speaker = RecordingSpeaker()
        var now = 1_000_000L
        val controller = GuidanceReliabilityController(speaker, nowEpochMillis = { now })
        val stop = GuidanceUtterance("stop:lo", "Sıradaki durak Lohne", "tr-TR")
        controller.onPermissions(GuidancePermissionSnapshot(false, false, false))
        assertFalse(controller.requestGuidance(stop))
        assertEquals(1_000_000L, controller.state.pendingSinceEpochMillis)

        now += GuidanceReliabilityController.MAX_PENDING_AGE_MILLIS - 1L
        assertFalse(controller.requestGuidance(stop))
        assertEquals(1_000_000L, controller.state.pendingSinceEpochMillis)

        now += 2L
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertTrue(speaker.spoken.isEmpty())
        assertNull(controller.state.pending)
    }

    @Test fun throwingTtsSpeakerRetainsOriginalPendingExpiry() {
        val spoken = mutableListOf<GuidanceUtterance>()
        var engineConnected = false
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                if (!engineConnected) throw IllegalStateException("TTS not ready")
                spoken += utterance
                return true
            }
        }
        var now = 1_000_000L
        val store = InMemoryGuidanceStateStore()
        val first = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        first.onPermissions(GuidancePermissionSnapshot(true, true, true))
        val stop = GuidanceUtterance("stop:exception", "Sıradaki durak Achim", "tr-TR")
        assertFalse(first.requestGuidance(stop))
        assertEquals(1_000_000L, first.state.pendingSinceEpochMillis)

        now += 60_000L
        val recreated = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        assertFalse(recreated.retryPending())
        assertEquals(1_000_000L, recreated.state.pendingSinceEpochMillis)

        engineConnected = true
        now += GuidanceReliabilityController.MAX_PENDING_AGE_MILLIS - 60_000L + 1L
        assertFalse(recreated.retryPending())
        assertTrue(spoken.isEmpty())
        assertNull(recreated.state.pending)
    }

    @Test fun brokenGuidanceStateLoadDoesNotPreventInSessionTts() {
        val brokenStore = object : GuidanceStateStore {
            override fun load(): GuidanceReliabilityState? =
                throw IllegalStateException("corrupt stored guidance state")
            override fun save(state: GuidanceReliabilityState) {}
        }
        val speaker = RecordingSpeaker()
        val controller = GuidanceReliabilityController(speaker, brokenStore)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, false))
        val instruction = GuidanceUtterance(
            "stop:lo", "Sıradaki durak Lohne", "tr-TR",
        )

        assertTrue(controller.requestGuidance(instruction))
        assertEquals(listOf(instruction), speaker.spoken)
        assertEquals(instruction.key, controller.state.lastSpokenKey)
    }

    @Test fun brokenGuidanceStateSaveDoesNotSuppressTtsOrDuplicateInstruction() {
        val brokenStore = object : GuidanceStateStore {
            override fun load(): GuidanceReliabilityState? = null
            override fun save(state: GuidanceReliabilityState) {
                throw IllegalStateException("guidance storage unavailable")
            }
        }
        val speaker = RecordingSpeaker()
        val controller = GuidanceReliabilityController(speaker, brokenStore)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        controller.onAudioRoute(GuidanceAudioRoute.BLUETOOTH_CONNECTED)
        val instruction = GuidanceUtterance(
            "stop:bre", "Sıradaki durak Bremen Hbf", "tr-TR",
        )

        assertTrue(controller.requestGuidance(instruction))
        controller.onAudioRoute(GuidanceAudioRoute.BLUETOOTH_DISCONNECTED)
        assertFalse(controller.requestGuidance(instruction))
        assertEquals(listOf(instruction), speaker.spoken)
        assertEquals(instruction.key, controller.state.lastSpokenKey)
    }


    @Test fun restoredAlreadySpokenPendingIsClearedWithoutReplay() {
        val instruction = GuidanceUtterance("stop:lo", "Sıradaki durak Lohne", "tr-TR")
        var saved = GuidanceReliabilityState(
            lastSpokenKey = instruction.key,
            pending = instruction,
            pendingSinceEpochMillis = 100_000L,
        )
        val store = object : GuidanceStateStore {
            override fun load(): GuidanceReliabilityState = saved
            override fun save(state: GuidanceReliabilityState) { saved = state }
        }
        val speaker = RecordingSpeaker()
        val controller = GuidanceReliabilityController(speaker, store, nowEpochMillis = { 100_100L })

        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertTrue(speaker.spoken.isEmpty())
        assertEquals(instruction.key, controller.state.lastSpokenKey)
        assertNull(controller.state.pending)
        assertNull(controller.state.pendingSinceEpochMillis)

        val recreated = GuidanceReliabilityController(speaker, store, nowEpochMillis = { 100_200L })
        assertFalse(recreated.retryPending())
        assertTrue(speaker.spoken.isEmpty())
    }
}
