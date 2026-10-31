package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceUtteranceBoundsTest {
    private class RecordingSpeaker : GuidanceSpeaker {
        val spoken = mutableListOf<GuidanceUtterance>()
        override fun speak(utterance: GuidanceUtterance): Boolean {
            spoken += utterance
            return true
        }
    }

    @Test fun oversizedProviderGuidanceIsRejectedBeforeSpeechAndPersistence() {
        val speaker = RecordingSpeaker()
        val store = InMemoryGuidanceStateStore()
        val controller = GuidanceReliabilityController(speaker, store)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))

        val valid = GuidanceUtterance("stop:lo", "Lohne", "de-DE")
        val invalid = listOf(
            valid.copy(key = "k".repeat(GuidanceReliabilityController.MAX_UTTERANCE_KEY_CHARACTERS + 1)),
            valid.copy(text = "x".repeat(GuidanceReliabilityController.MAX_UTTERANCE_TEXT_CHARACTERS + 1)),
            valid.copy(localeTag = "l".repeat(GuidanceReliabilityController.MAX_LOCALE_TAG_CHARACTERS + 1)),
            valid.copy(localeTag = "  "),
        )
        invalid.forEach { assertFalse(controller.requestGuidance(it)) }
        assertTrue(speaker.spoken.isEmpty())
        assertNull(controller.state.pending)
        assertNull(controller.state.lastSpokenKey)

        controller.onPermissions(GuidancePermissionSnapshot(false, false, false))
        invalid.forEach { assertFalse(controller.requestGuidance(it)) }
        assertNull(controller.state.pending)
        assertNull(controller.state.pendingSinceEpochMillis)

        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertTrue(controller.requestGuidance(valid))
        assertEquals(listOf(valid), speaker.spoken)
    }

    @Test fun oversizedPersistedPendingCannotReplayOnPermissionRecovery() {
        val speaker = RecordingSpeaker()
        val poisoned = GuidanceUtterance(
            key = "stop:old",
            text = "x".repeat(GuidanceReliabilityController.MAX_UTTERANCE_TEXT_CHARACTERS + 1),
            localeTag = "de-DE",
        )
        val store = object : GuidanceStateStore {
            var saved = GuidanceReliabilityState(
                pending = poisoned,
                pendingSinceEpochMillis = 10_000L,
                permissionState = GuidancePermissionState.GRANTED,
            )
            override fun load(): GuidanceReliabilityState = saved
            override fun save(state: GuidanceReliabilityState) { saved = state }
        }
        val controller = GuidanceReliabilityController(speaker, store, nowEpochMillis = { 11_000L })
        assertFalse(controller.retryPending())
        assertTrue(speaker.spoken.isEmpty())
        assertNull(controller.state.pending)
        assertNull(controller.state.pendingSinceEpochMillis)
        assertNull(store.saved.pending)
    }

    @Test fun boundaryLengthGuidanceStillReachesSpeaker() {
        val speaker = RecordingSpeaker()
        val controller = GuidanceReliabilityController(speaker)
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        val edge = GuidanceUtterance(
            "k".repeat(GuidanceReliabilityController.MAX_UTTERANCE_KEY_CHARACTERS),
            "x".repeat(GuidanceReliabilityController.MAX_UTTERANCE_TEXT_CHARACTERS),
            "de-DE",
        )
        assertTrue(controller.requestGuidance(edge))
        assertEquals(edge, speaker.spoken.single())
    }
}
