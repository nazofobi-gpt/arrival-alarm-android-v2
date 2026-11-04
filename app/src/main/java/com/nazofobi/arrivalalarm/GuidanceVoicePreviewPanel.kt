package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** A voluntary speaker check, never a trip instruction or alarm. */
internal fun voicePreviewUtterance(language: GuidanceLanguage): GuidanceUtterance =
    GuidanceUtterance(
        key = "voice-preview:${language.localeTag}",
        text = when (language) {
            GuidanceLanguage.TURKISH -> "Bu bir ses denemesidir."
            GuidanceLanguage.GERMAN -> "Dies ist eine Sprachprobe."
            GuidanceLanguage.ENGLISH -> "This is a voice test."
        },
        localeTag = language.localeTag,
    )

/**
 * Do not enqueue a test utterance in durable stop guidance. A failed TTS
 * preview must not be replayed later as a real next-stop announcement.
 * Disable while tracking so QUEUE_FLUSH cannot interrupt a real arrival cue.
 */
internal fun isVoicePreviewProtectedPhase(phase: JourneyPhase): Boolean =
    phase == JourneyPhase.ARMED || phase == JourneyPhase.ARRIVED

internal fun requestGuidanceVoicePreview(
    speaker: GuidanceSpeaker,
    language: GuidanceLanguage,
    isJourneyAudioActive: Boolean,
): Boolean {
    if (isJourneyAudioActive) return false
    return try {
        speaker.speak(voicePreviewUtterance(language))
    } catch (_: RuntimeException) {
        false
    }
}

@Composable
internal fun GuidanceVoicePreviewPanel(
    speaker: GuidanceSpeaker,
    language: GuidanceLanguage,
    isJourneyAudioActive: Boolean,
    modifier: Modifier = Modifier,
    onTestArrivalAlert: () -> Boolean = { false },
    onOpenNotificationSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var alertPreviewResult by remember { mutableStateOf<Boolean?>(null) }
    var lastAttemptAccepted by remember(language) { mutableStateOf<Boolean?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = {
                lastAttemptAccepted = requestGuidanceVoicePreview(
                    speaker = speaker,
                    language = language,
                    isJourneyAudioActive = isJourneyAudioActive,
                )
            },
            enabled = !isJourneyAudioActive,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("guidance-voice-preview"),
        ) {
            Text(stringResource(R.string.guidance_voice_preview_action))
        }
        OutlinedButton(
            onClick = { alertPreviewResult = onTestArrivalAlert() },
            enabled = !isJourneyAudioActive,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("arrival-notification-preview"),
        ) { Text(stringResource(R.string.arrival_alert_preview_action)) }
        if (alertPreviewResult != null) {
            Text(
                text = stringResource(
                    if (alertPreviewResult == true) R.string.arrival_alert_preview_submitted
                    else R.string.arrival_alert_preview_unavailable,
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("arrival-notification-preview-status"),
            )
        }
        if (alertPreviewResult == false && !isJourneyAudioActive) {
            // Android can deny a preview when permission or the channel is blocked.
            // Offer user-driven recovery without opening Settings on our own.
            OutlinedButton(
                onClick = {
                    if (onOpenNotificationSettings != null) onOpenNotificationSettings()
                    else AndroidArrivalAlertPreview.openSettings(context)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("arrival-notification-settings"),
            ) {
                Text(stringResource(R.string.arrival_alert_preview_open_settings))
            }
        }
        if (isJourneyAudioActive) {
            Text(
                text = stringResource(R.string.guidance_voice_preview_tracking_disabled),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("guidance-voice-preview-disabled"),
            )
        } else if (lastAttemptAccepted != null) {
            Text(
                text = stringResource(
                    if (lastAttemptAccepted == true) R.string.guidance_voice_preview_requested
                    else R.string.guidance_voice_preview_unavailable,
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("guidance-voice-preview-status"),
            )
        }
        Text(
            text = stringResource(R.string.guidance_voice_preview_note),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
