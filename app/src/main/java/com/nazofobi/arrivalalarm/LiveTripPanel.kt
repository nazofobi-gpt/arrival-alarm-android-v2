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
    val trustedRealtime = isTrustedRealtimeForJourney(
        state = selectedRoute.realtime?.state,
        routeOffline = routeOffline,
        cancelledTripIds = selectedRoute.realtime?.cancelledTripIds.orEmpty(),
    )
    val progress = RouteStopProgressTracker().resolve(
        stops = selectedRoute.stops,
        nowEpochSeconds = nowEpochSeconds,
        trustRealtime = trustedRealtime,
    )
    val target = selectedRoute.stops.lastOrNull()
    val targetLabel = target?.name ?: selectedRoute.destination.label
    val targetTimeSource = if (trustedRealtime) {
        target?.arrival ?: target?.plannedArrival ?: selectedRoute.arrival
    } else {
        target?.plannedArrival ?: target?.plannedDeparture
            ?: selectedRoute.arrival.takeIf { target == null }
    }
    val targetTime = routeStopClock(targetTimeSource)

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

            selectedRoute.realtime?.let { realtime ->
                LiveTripFact(
                    label = stringResource(R.string.live_trip_realtime_source),
                    value = when (realtime.state) {
                        RouteRealtimeState.FRESH_MATCHED ->
                            stringResource(R.string.live_trip_realtime_fresh)
                        RouteRealtimeState.FRESH_NO_MATCH ->
                            stringResource(R.string.live_trip_realtime_no_match)
                        RouteRealtimeState.STALE ->
                            stringResource(R.string.live_trip_realtime_stale)
                        RouteRealtimeState.UNAVAILABLE ->
                            stringResource(R.string.live_trip_realtime_unavailable)
                    },
                    tag = "live-trip-realtime",
                )
            }

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
                progress.resolvedByProviderTime && progress.previous != null && progress.next != null ->
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

            // Timetable/realtime stop sequence estimate, not GPS vehicle-position evidence.
            progress.remainingStops
                ?.takeIf { progress.resolvedByProviderTime && it >= 0 }
                ?.let { remaining ->
                    LiveTripFact(
                        label = stringResource(R.string.live_trip_remaining_stops),
                        value = remaining.toString(),
                        tag = "live-trip-remaining-stops",
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

/**
 * Show a destination clock only after validating the complete provider value.
 * Prefix slicing can turn malformed payloads (e.g. 08:45garbage) into false ETAs.
 * ISO timestamps are parsed with the existing strict transit validator; GTFS
 * clock-only values may cross midnight, but cannot exceed the 47-hour UI bound.
 */
internal fun routeStopClock(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if ('T' in raw) {
        if (raw.toTransitEpochSecondsOrNull() == null) return null
        return raw.substringAfter('T').take(5)
    }

    val fullClock = when {
        GTFS_DISPLAY_HH_MM.matches(raw) -> "$raw:00"
        GTFS_DISPLAY_HH_MM_SS.matches(raw) -> raw
        else -> return null
    }
    val time = runCatching { GtfsServiceTime.parse(fullClock) }.getOrNull() ?: return null
    if (time.hour !in 0..47) return null
    return "%02d:%02d".format(java.util.Locale.ROOT, time.hour, time.minute)
}

private val GTFS_DISPLAY_HH_MM = Regex("""^\d{1,2}:[0-5]\d$""")
private val GTFS_DISPLAY_HH_MM_SS = Regex("""^\d{1,2}:[0-5]\d:[0-5]\d$""")
