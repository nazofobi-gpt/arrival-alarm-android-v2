package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidanceVoicePreviewInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun arrivedJourneyDisablesPreviewToProtectFinalAlarmSpeech() {
        var spoken = 0
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                spoken++
                return true
            }
        }
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(
                    speaker, GuidanceLanguage.GERMAN,
                    isJourneyAudioActive = isVoicePreviewProtectedPhase(JourneyPhase.ARRIVED),
                )
            }
        }
        rule.onNodeWithTag("guidance-voice-preview").assertIsNotEnabled()
        rule.onNodeWithTag("guidance-voice-preview-disabled").assertExists()
        assertEquals(0, spoken)
    }

    @Test fun noDestinationNeededAndExplicitTapUsesSelectedLocale() {
        val spoken = mutableListOf<GuidanceUtterance>()
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                spoken += utterance
                return true
            }
        }
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(speaker, GuidanceLanguage.GERMAN, isJourneyAudioActive = false)
            }
        }
        assertEquals(0, spoken.size)
        rule.onNodeWithTag("guidance-voice-preview").performClick()
        rule.waitForIdle()
        assertEquals("de-DE", spoken.single().localeTag)
        rule.onNodeWithTag("guidance-voice-preview-status").assertExists()
    }

    @Test fun armedTripDisablesPreviewAndCannotInterruptAudio() {
        var spoken = 0
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                spoken++
                return true
            }
        }
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(speaker, GuidanceLanguage.TURKISH, isJourneyAudioActive = true)
            }
        }
        rule.onNodeWithTag("guidance-voice-preview").assertIsNotEnabled()
        rule.onNodeWithTag("guidance-voice-preview-disabled").assertExists()
        rule.onNodeWithTag("guidance-voice-preview-status").assertDoesNotExist()
        assertEquals(0, spoken)
    }

    @Test fun notificationPreviewRequiresTapWithoutSpeechOrJourneyMutation() {
        var posts = 0
        var speech = 0
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(
                    speaker = object : GuidanceSpeaker {
                        override fun speak(utterance: GuidanceUtterance): Boolean {
                            speech++
                            return true
                        }
                    },
                    language = GuidanceLanguage.ENGLISH,
                    isJourneyAudioActive = false,
                    onTestArrivalAlert = { posts++; true },
                )
            }
        }
        assertEquals(0, posts)
        rule.onNodeWithTag("arrival-notification-preview").performClick()
        rule.onNodeWithTag("arrival-notification-preview-status").assertExists()
        assertEquals(1, posts)
        assertEquals(0, speech)
    }

    @Test fun activeAlarmDisablesNotificationPreview() {
        var posts = 0
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(
                    speaker = object : GuidanceSpeaker {
                        override fun speak(utterance: GuidanceUtterance) = true
                    },
                    language = GuidanceLanguage.TURKISH,
                    isJourneyAudioActive = true,
                    onTestArrivalAlert = { posts++; true },
                )
            }
        }
        rule.onNodeWithTag("arrival-notification-preview").assertIsNotEnabled()
        assertEquals(0, posts)
    }

    @Test fun unavailableTtsShowsFailureWithoutSavingGuidance() {
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean = false
        }
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(speaker, GuidanceLanguage.ENGLISH, isJourneyAudioActive = false)
            }
        }
        rule.onNodeWithTag("guidance-voice-preview").performClick()
        rule.onNodeWithTag("guidance-voice-preview-status").assertExists()
    }
}
