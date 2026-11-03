package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceVoicePreviewTest {
    @Test fun selectedTrDeEnLanguageReachesSpeakerWithoutTripState() {
        val spoken = mutableListOf<GuidanceUtterance>()
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                spoken += utterance
                return true
            }
        }
        val guidanceStore = InMemoryGuidanceStateStore()
        val controller = GuidanceReliabilityController(speaker, guidanceStore)
        GuidanceLanguage.values().forEach { language ->
            assertTrue(requestGuidanceVoicePreview(speaker, language, isJourneyArmed = false))
        }
        assertEquals(listOf("tr-TR", "de-DE", "en-US"), spoken.map { it.localeTag })
        assertEquals(
            listOf(
                "Bu bir ses denemesidir.",
                "Dies ist eine Sprachprobe.",
                "This is a voice test.",
            ),
            spoken.map { it.text },
        )
        assertTrue(spoken.all { it.key.startsWith("voice-preview:") })
        assertNull(controller.state.pending)
        assertNull(guidanceStore.load())
    }

    @Test fun repeatedExplicitTapIsNotSuppressedAsDuplicateTripGuidance() {
        var attempts = 0
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                attempts++
                return true
            }
        }
        assertTrue(requestGuidanceVoicePreview(speaker, GuidanceLanguage.ENGLISH, false))
        assertTrue(requestGuidanceVoicePreview(speaker, GuidanceLanguage.ENGLISH, false))
        assertEquals(2, attempts)
    }

    @Test fun armedJourneyCannotFlushArrivalSpeech() {
        var called = false
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                called = true
                return true
            }
        }
        assertFalse(requestGuidanceVoicePreview(speaker, GuidanceLanguage.TURKISH, true))
        assertFalse(called)
    }

    @Test fun unavailableOrThrowingTtsDoesNotEnqueueDelayedSpeech() {
        var attempted = 0
        val unavailable = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                attempted++
                return false
            }
        }
        assertFalse(requestGuidanceVoicePreview(unavailable, GuidanceLanguage.GERMAN, false))
        assertEquals(1, attempted)
        val throwing = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean =
                throw IllegalStateException("TTS unavailable")
        }
        assertFalse(requestGuidanceVoicePreview(throwing, GuidanceLanguage.ENGLISH, false))
    }
}
