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
        var dismisses = 0
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
                    onDismissArrivalAlert = { dismisses++; true },
                )
            }
        }
        assertEquals(0, posts)
        rule.onNodeWithTag("arrival-notification-preview").performClick()
        rule.onNodeWithTag("arrival-notification-preview-status").assertExists()
        assertEquals(1, posts)
        rule.onNodeWithTag("arrival-notification-settings").assertDoesNotExist()
        rule.onNodeWithTag("arrival-notification-dismiss").performClick()
        rule.onNodeWithTag("arrival-notification-dismiss-status").assertExists()
        rule.onNodeWithTag("arrival-notification-dismiss").assertDoesNotExist()
        assertEquals(1, dismisses)
        assertEquals(0, speech)
    }

    @Test fun rejectedNotificationPreviewOffersUserControlledRecovery() {
        var opened = 0
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(
                    speaker = object : GuidanceSpeaker {
                        override fun speak(utterance: GuidanceUtterance) = true
                    },
                    language = GuidanceLanguage.GERMAN,
                    isJourneyAudioActive = false,
                    onTestArrivalAlert = { false },
                    onOpenNotificationSettings = { opened++ },
                )
            }
        }
        rule.onNodeWithTag("arrival-notification-settings").assertDoesNotExist()
        rule.onNodeWithTag("arrival-notification-preview").performClick()
        rule.onNodeWithTag("arrival-notification-preview-status").assertExists()
        rule.onNodeWithTag("arrival-notification-settings").assertExists()
        assertEquals(0, opened)
        rule.onNodeWithTag("arrival-notification-settings").performClick()
        assertEquals(1, opened)
    }

    @Test fun activeAlarmDisablesNotificationPreview() {
        var posts = 0
        var dismisses = 0
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(
                    speaker = object : GuidanceSpeaker {
                        override fun speak(utterance: GuidanceUtterance) = true
                    },
                    language = GuidanceLanguage.TURKISH,
                    isJourneyAudioActive = true,
                    onTestArrivalAlert = { posts++; true },
                    onDismissArrivalAlert = { dismisses++; true },
                )
            }
        }
        rule.onNodeWithTag("arrival-notification-preview").assertIsNotEnabled()
        rule.onNodeWithTag("arrival-notification-dismiss").assertDoesNotExist()
        assertEquals(0, posts)
        assertEquals(0, dismisses)
    }

    @Test fun failedDismissRemainsVisibleAndNeverClaimsSuccess() {
        var dismisses = 0
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(
                    speaker = object : GuidanceSpeaker {
                        override fun speak(utterance: GuidanceUtterance) = true
                    },
                    language = GuidanceLanguage.ENGLISH,
                    isJourneyAudioActive = false,
                    onTestArrivalAlert = { true },
                    onDismissArrivalAlert = { dismisses++; false },
                )
            }
        }
        rule.onNodeWithTag("arrival-notification-preview").performClick()
        rule.onNodeWithTag("arrival-notification-dismiss").performClick()
        rule.onNodeWithTag("arrival-notification-dismiss-status").assertExists()
        rule.onNodeWithTag("arrival-notification-dismiss").assertExists()
        assertEquals(1, dismisses)
    }

    @Test fun quietChannelWarnsAndSettingsRequireAnExplicitTap() {
        var opened = 0
        rule.setContent {
            MaterialTheme {
                GuidanceVoicePreviewPanel(
                    speaker = object : GuidanceSpeaker {
                        override fun speak(utterance: GuidanceUtterance) = true
                    },
                    language = GuidanceLanguage.GERMAN,
                    isJourneyAudioActive = false,
                    onTestArrivalAlert = { true },
                    onCheckAlertChannelQuiet = { true },
                    onOpenNotificationSettings = { opened++ },
                )
            }
        }
        rule.onNodeWithTag("arrival-notification-quiet-warning").assertDoesNotExist()
        rule.onNodeWithTag("arrival-notification-preview").performClick()
        rule.onNodeWithTag("arrival-notification-quiet-warning").assertExists()
        rule.onNodeWithTag("arrival-notification-settings").assertExists()
        assertEquals(0, opened)
        rule.onNodeWithTag("arrival-notification-settings").performClick()
        assertEquals(1, opened)
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

    @Test fun arrivalChannelSettingsIntentIsScoped() {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = AndroidArrivalAlertPreview.channelSettingsIntent(context)
        assertEquals(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName,
            intent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
        assertEquals(AndroidArrivalAlertPreview.CHANNEL_ID,
            intent.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
    }
}
