package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    val timingText = when {
        routeOffline -> stringResource(R.string.live_trip_timing_offline)
        !progress.resolvedByProviderTime -> stringResource(R.string.live_trip_timing_unknown)
        progress.timingBasis == TransitTimingBasis.REALTIME ->
            stringResource(R.string.live_trip_timing_realtime)
        else -> stringResource(R.string.live_trip_timing_scheduled)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live-trip-panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = stringResource(R.string.live_trip_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(
                                R.string.live_trip_route_format,
                                selectedRoute.line,
                                selectedRoute.direction,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.live_trip_timing_label, timingText),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("live-trip-timing"),
                )
                selectedRoute.realtime?.let { realtime ->
                    val value = when (realtime.state) {
                        RouteRealtimeState.FRESH_MATCHED ->
                            stringResource(R.string.live_trip_realtime_fresh)
                        RouteRealtimeState.FRESH_NO_MATCH ->
                            stringResource(R.string.live_trip_realtime_no_match)
                        RouteRealtimeState.STALE ->
                            stringResource(R.string.live_trip_realtime_stale)
                        RouteRealtimeState.UNAVAILABLE ->
                            stringResource(R.string.live_trip_realtime_unavailable)
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("live-trip-realtime"),
                    )
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                progress.previous?.let {
                    LiveTripRow(
                        icon = Icons.Rounded.DirectionsTransit,
                        label = stringResource(R.string.live_trip_previous),
                        value = it.name,
                        tag = "live-trip-previous",
                        subdued = true,
                    )
                }

                when {
                    progress.current != null -> LiveTripRow(
                        icon = Icons.Rounded.LocationOn,
                        label = stringResource(R.string.live_trip_current),
                        value = progress.current.name,
                        tag = "live-trip-current",
                        emphasized = true,
                    )
                    progress.resolvedByProviderTime && (progress.previous != null || progress.next != null) ->
                        LiveTripRow(
                            icon = Icons.Rounded.Navigation,
                            label = stringResource(R.string.live_trip_current),
                            value = stringResource(R.string.live_trip_between_stops),
                            tag = "live-trip-current",
                            emphasized = true,
                        )
                    else -> LiveTripRow(
                        icon = Icons.Rounded.Navigation,
                        label = stringResource(R.string.live_trip_current),
                        value = stringResource(R.string.live_trip_progress_unavailable),
                        tag = "live-trip-current",
                        subdued = true,
                    )
                }

                progress.next?.let {
                    LiveTripRow(
                        icon = Icons.Rounded.DirectionsTransit,
                        label = stringResource(R.string.live_trip_next),
                        value = it.name,
                        tag = "live-trip-next",
                    )
                }

                LiveTripRow(
                    icon = Icons.Rounded.Flag,
                    label = stringResource(R.string.live_trip_target),
                    value = targetLabel,
                    tag = "live-trip-target",
                    emphasized = true,
                    trailing = targetTime,
                )
                targetTime?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("live-trip-target-time"),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiveStatusTile(
                icon = Icons.Rounded.NotificationsActive,
                label = stringResource(R.string.live_trip_alarm),
                value = journeyPhaseText(journey.phase),
                tag = "live-trip-alarm",
                modifier = Modifier.weight(1f),
            )
            LiveStatusTile(
                icon = Icons.Rounded.BluetoothAudio,
                label = stringResource(R.string.live_trip_audio),
                value = guidanceUiStatus(guidanceState),
                tag = "live-trip-audio",
                modifier = Modifier.weight(1f),
            )
        }

        journey.distanceMeters?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.live_trip_gps_distance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                    Text(
                        text = stringResource(R.string.live_trip_distance_meters, it.roundToInt()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("live-trip-distance"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTripRow(
    icon: ImageVector,
    label: String,
    value: String,
    tag: String,
    emphasized: Boolean = false,
    subdued: Boolean = false,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(9.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = if (emphasized) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                color = if (subdued) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(tag),
            )
        }
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LiveStatusTile(
    icon: ImageVector,
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag(tag),
            )
        }
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
