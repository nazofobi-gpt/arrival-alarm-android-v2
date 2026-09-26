package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Production projection of active journey state.
 *
 * GPS distance comes from ActiveJourneyService/JourneyUiState. Stop progression is intentionally
 * provider-time based and never presented as a physical vehicle-position observation.
 */
@Composable
fun LiveTripPanel(
    selectedRoute: RouteOption,
    journey: JourneyUiState,
    guidanceState: GuidanceReliabilityState,
    routeOffline: Boolean,
    nowEpochSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val progress = RouteStopProgressTracker().resolve(selectedRoute.stops, nowEpochSeconds)
    val target = selectedRoute.stops.lastOrNull()
    val targetLabel = target?.name ?: selectedRoute.destination.label
    val targetTime = routeStopClock(
        target?.arrival ?: target?.plannedArrival ?: selectedRoute.arrival
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live-trip-panel"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.live_trip_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.live_trip_route_format,
                    selectedRoute.line,
                    selectedRoute.direction,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = stringResource(
                    R.string.live_trip_timing_label,
                    when {
                        routeOffline -> stringResource(R.string.live_trip_timing_offline)
                        !progress.resolvedByProviderTime -> stringResource(R.string.live_trip_timing_unknown)
                        progress.timingBasis == TransitTimingBasis.REALTIME ->
                            stringResource(R.string.live_trip_timing_realtime)
                        else -> stringResource(R.string.live_trip_timing_scheduled)
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("live-trip-timing"),
            )

            HorizontalDivider()

            progress.previous?.let {
                LiveTripFact(
                    label = stringResource(R.string.live_trip_previous),
                    value = it.name,
                    tag = "live-trip-previous",
                )
            }

            when {
                progress.current != null -> LiveTripFact(
                    label = stringResource(R.string.live_trip_current),
                    value = progress.current.name,
                    tag = "live-trip-current",
                )
                progress.resolvedByProviderTime && (progress.previous != null || progress.next != null) ->
                    Text(
                        text = stringResource(R.string.live_trip_between_stops),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("live-trip-current"),
                    )
                else -> Text(
                    text = stringResource(R.string.live_trip_progress_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("live-trip-current"),
                )
            }

            progress.next?.let {
                LiveTripFact(
                    label = stringResource(R.string.live_trip_next),
                    value = it.name,
                    tag = "live-trip-next",
                )
            }

            LiveTripFact(
                label = stringResource(R.string.live_trip_target),
                value = targetLabel,
                tag = "live-trip-target",
            )

            targetTime?.let {
                LiveTripFact(
                    label = stringResource(R.string.live_trip_target_time),
                    value = it,
                    tag = "live-trip-target-time",
                )
            }

            journey.distanceMeters?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                LiveTripFact(
                    label = stringResource(R.string.live_trip_gps_distance),
                    value = stringResource(R.string.live_trip_distance_meters, it.roundToInt()),
                    tag = "live-trip-distance",
                )
            }

            HorizontalDivider()

            LiveTripFact(
                label = stringResource(R.string.live_trip_alarm),
                value = journeyPhaseText(journey.phase),
                tag = "live-trip-alarm",
            )

            LiveTripFact(
                label = stringResource(R.string.live_trip_audio),
                value = guidanceUiStatus(guidanceState),
                tag = "live-trip-audio",
            )
        }
    }
}

@Composable
private fun LiveTripFact(
    label: String,
    value: String,
    tag: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun guidanceUiStatus(state: GuidanceReliabilityState): String = stringResource(
    when {
        state.pending != null && state.permissionState == GuidancePermissionState.DENIED ->
            R.string.guidance_pending
        state.pending != null -> R.string.guidance_tts_unavailable
        state.permissionState == GuidancePermissionState.DENIED -> R.string.guidance_permission_needed
        state.permissionState == GuidancePermissionState.PARTIAL -> R.string.guidance_partial
        state.audioRoute == GuidanceAudioRoute.BLUETOOTH_CONNECTED -> R.string.guidance_bluetooth
        state.audioRoute == GuidanceAudioRoute.BLUETOOTH_DISCONNECTED -> R.string.live_trip_audio_device_fallback
        else -> R.string.guidance_device
    }
)

private fun routeStopClock(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val time = raw.substringAfter('T', raw)
    val candidate = time.take(5)
    return candidate.takeIf {
        candidate.length == 5 && candidate[2] == ':' &&
            candidate.take(2).toIntOrNull() != null &&
            candidate.drop(3).toIntOrNull() != null
    }
}
