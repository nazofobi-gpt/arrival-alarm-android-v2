package com.nazofobi.arrivalalarm

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FirstRunOnboarding(
    onContinue: () -> Unit,
    offlineDataState: NationwideDataState,
    onDownloadOfflineData: () -> Unit,
    guidanceLanguage: GuidanceLanguage = GuidanceLanguage.TURKISH,
    onGuidanceLanguageChange: (GuidanceLanguage) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("first-run-onboarding"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrival_train),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("onboarding-brand-icon"),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.onboarding_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OnboardingFeature(
                marker = "A",
                title = stringResource(R.string.onboarding_route_title),
                body = stringResource(R.string.onboarding_route_body),
            )
            OnboardingFeature(
                marker = "C",
                title = stringResource(R.string.onboarding_live_title),
                body = stringResource(R.string.onboarding_live_body),
            )
            OnboardingFeature(
                marker = "!",
                title = stringResource(R.string.onboarding_alarm_title),
                body = stringResource(R.string.onboarding_alarm_body),
            )

            // Optional: configuring TalkBack or display scaling never gates journey setup.
            Card(
                modifier = Modifier.fillMaxWidth().testTag("onboarding-accessibility-setup"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_accessibility_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.onboarding_accessibility_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(accessibilityQuickSetupIntent()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("onboarding-accessibility-settings"),
                    ) {
                        Text(stringResource(R.string.onboarding_accessibility_action))
                    }
                }
            }


            // ONB-005: reuse the same persisted announcement language as Settings.
            // Pairing opens OS settings only when tapped; the app does not select or
            // silently change the audio output or request broad permissions.
            Card(
                modifier = Modifier.fillMaxWidth().testTag("onboarding-audio-setup"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_audio_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.onboarding_audio_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.guidance_language_status,
                            guidanceLanguage.displayName,
                        ),
                        modifier = Modifier.testTag("onboarding-audio-language-status"),
                    )
                    GuidanceLanguage.values().forEach { language ->
                        OutlinedButton(
                            onClick = { onGuidanceLanguageChange(language) },
                            enabled = language != guidanceLanguage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("onboarding-audio-language-${language.localeTag}"),
                        ) {
                            Text(
                                if (language == guidanceLanguage)
                                    stringResource(
                                        R.string.guidance_language_selected,
                                        language.displayName,
                                    )
                                else language.displayName
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { context.startActivity(bluetoothQuickSetupIntent()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("onboarding-bluetooth-settings"),
                    ) {
                        Text(stringResource(R.string.onboarding_bluetooth_action))
                    }
                    Text(
                        text = stringResource(R.string.onboarding_bluetooth_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // An offline index can be large: present a real, explicit choice, never auto-download.
            val offlineReady = offlineDataState is NationwideDataState.Ready &&
                offlineDataState.stopCount > 0
            val offlineLoading = offlineDataState is NationwideDataState.Loading
            Card(
                modifier = Modifier.fillMaxWidth().testTag("onboarding-offline-setup"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_offline_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.onboarding_offline_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val offlineStatus = when {
                        offlineReady -> R.string.onboarding_offline_ready
                        offlineLoading -> R.string.onboarding_offline_loading
                        offlineDataState is NationwideDataState.Error ->
                            R.string.onboarding_offline_error
                        else -> R.string.onboarding_offline_idle
                    }
                    Text(
                        text = stringResource(offlineStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("onboarding-offline-status"),
                    )
                    if (!offlineReady && !offlineLoading) {
                        OutlinedButton(
                            onClick = onDownloadOfflineData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("onboarding-offline-download"),
                        ) {
                            Text(
                                stringResource(
                                    if (offlineDataState is NationwideDataState.Error)
                                        R.string.onboarding_offline_retry
                                    else R.string.onboarding_offline_download,
                                )
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_privacy_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_permission_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_background_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("onboarding-privacy-note"),
                    )
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("onboarding-complete"),
            ) {
                Text(stringResource(R.string.onboarding_start_action))
            }
        }
    }
}

/** Open the user's own device accessibility controls; no setting is changed by the app. */
internal fun accessibilityQuickSetupIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

/** Pairing remains under the user\'s control in the system Bluetooth UI. */
internal fun bluetoothQuickSetupIntent(): Intent =
    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

@Composable
private fun OnboardingFeature(
    marker: String,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = marker,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
