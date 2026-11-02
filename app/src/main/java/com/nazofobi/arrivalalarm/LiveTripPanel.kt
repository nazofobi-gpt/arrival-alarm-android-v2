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
    // Match the stop-progress evidence basis even when a fresh feed is internally contradictory.
    val targetTimeSource = if (trustedRealtime && progress.timingBasis == TransitTimingBasis.REALTIME) {
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

            // A cached snapshot cannot provide a current provider status while offline.
            // The timing label above already reports offline rather than realtime.
            selectedRoute.realtime?.takeUnless { routeOffline }?.let { realtime ->
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

            if (journey.phase == JourneyPhase.ARMED) {
                scheduledBoardingMinutesRemaining(
                    selectedRoute.stops.firstOrNull()?.plannedDeparture,
                    nowEpochSeconds,
                )?.let { minutes ->
                    LiveTripFact(
                        label = stringResource(R.string.live_trip_planned_boarding),
                        value = stringResource(R.string.live_trip_planned_boarding_minutes, minutes),
                        tag = "live-trip-planned-boarding",
                    )
                }
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

            progress.next?.let { nextStop ->
                // A next stop is timetable/provider-time derived, not a GPS fix.
                // Include only source-backed platform information and never reuse
                // an unverified live platform when the route is offline or stale.
                val formattedPlatform = if (progress.resolvedByProviderTime) {
                    val trustedPlatform = routeHasVerifiedRealtimePlatform(
                        routeOffline,
                        selectedRoute.realtime?.state,
                        nextStop.platform,
                    )
                    routeEvidencePlatform(
                        if (trustedPlatform) nextStop.platform else null,
                        nextStop.plannedPlatform,
                        !trustedPlatform,
                    )?.let { platform ->
                        stringResource(
                            if (trustedPlatform) R.string.route_realtime_platform_format
                            else R.string.route_planned_platform_format,
                            platform,
                        )
                    }
                } else null
                LiveTripFact(
                    label = stringResource(R.string.live_trip_next),
                    value = listOfNotNull(nextStop.name, formattedPlatform).joinToString(" • "),
                    tag = "live-trip-next",
                )
            }

            // Show ETA to the immediate next intermediate stop from the same coherent
            // schedule/realtime basis as the stop tracker. Never imply observed vehicle
            // position; the destination already has its own separate countdown.
            if (journey.phase == JourneyPhase.ARMED &&
                progress.resolvedByProviderTime &&
                // Before boarding, the first scheduled stop is not a next en-route stop.
                // Do not present its arrival clock as progress toward an intermediate stop.
                (progress.previous != null ||
                    (progress.current != null &&
                        progress.current !== selectedRoute.stops.firstOrNull())) &&
                progress.next != null &&
                progress.next !== target
            ) {
                destinationArrivalCountdown(
                    actualArrival = progress.next.arrival,
                    plannedArrival = progress.next.plannedArrival,
                    trustedRealtime = trustedRealtime &&
                        progress.timingBasis == TransitTimingBasis.REALTIME,
                    nowEpochSeconds = nowEpochSeconds,
                )?.let { countdown ->
                    LiveTripFact(
                        label = stringResource(
                            if (countdown.fromRealtime) R.string.live_trip_next_stop_realtime_countdown
                            else R.string.live_trip_next_stop_scheduled_countdown,
                        ),
                        value = listOf(
                            progress.next.name,
                            stringResource(R.string.live_trip_planned_boarding_minutes, countdown.minutes),
                        ).joinToString(" • "),
                        tag = "live-trip-next-stop-countdown",
                    )
                }
            }

            // This is an advisory from ordered stop-time evidence, NOT an observed vehicle location.
            // Only show it between the penultimate and destination stops, after an actual
            // departure-time boundary; never before boarding or for unarmed journeys.
            if (journey.phase == JourneyPhase.ARMED &&
                selectedRoute.stops.size >= 2 &&
                progress.resolvedByProviderTime &&
                progress.current == null &&
                progress.previous != null &&
                progress.next === target &&
                progress.remainingStops == 1
            ) {
                Text(
                    text = stringResource(R.string.live_trip_destination_next_advisory),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("live-trip-final-stop-advisory"),
                )
            }

            // The count is schedule/provider-time derived, not a GPS vehicle position.
            // Unknown or contradictory stop evidence must not produce a guessed count.
            progress.remainingStops?.takeIf {
                progress.resolvedByProviderTime && it >= 0
            }?.let { remaining ->
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

            // Provider arrival time is guidance, not a verified GPS vehicle ETA.
            // Unarmed trips, missing absolute clocks and stale realtime stay fail-closed.
            if (journey.phase == JourneyPhase.ARMED && progress.resolvedByProviderTime && target != null) {
                destinationArrivalCountdown(
                    actualArrival = target.arrival,
                    plannedArrival = target.plannedArrival,
                    // A contradictory live stop timeline makes the tracker fall
                    // back to planned times. The countdown must use that same basis.
                    trustedRealtime = trustedRealtime &&
                        progress.timingBasis == TransitTimingBasis.REALTIME,
                    nowEpochSeconds = nowEpochSeconds,
                )?.let { countdown ->
                    LiveTripFact(
                        label = stringResource(
                            if (countdown.fromRealtime) R.string.live_trip_arrival_realtime_countdown
                            else R.string.live_trip_arrival_scheduled_countdown,
                        ),
                        value = stringResource(R.string.live_trip_planned_boarding_minutes, countdown.minutes),
                        tag = "live-trip-destination-countdown",
                    )
                }
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
 * Display an advisory departure countdown only from a full, validated provider
 * instant. This is scheduled guidance, not a realtime vehicle or leave-now alert.
 */
internal fun scheduledBoardingMinutesRemaining(
    plannedDeparture: String?,
    nowEpochSeconds: Long,
): Long? {
    val departure = plannedDeparture.toTransitEpochSecondsOrNull() ?: return null
    if (nowEpochSeconds !in 946_684_800L..253_402_300_799L) return null
    val seconds = departure - nowEpochSeconds
    if (seconds <= 0L || seconds > 86_400L) return null
    return (seconds + 59L) / 60L
}

/** Strict absolute-time countdown; no GPS vehicle-position inference. */
internal data class DestinationArrivalCountdown(
    val minutes: Long,
    val fromRealtime: Boolean,
)

internal fun destinationArrivalCountdown(
    actualArrival: String?,
    plannedArrival: String?,
    trustedRealtime: Boolean,
    nowEpochSeconds: Long,
): DestinationArrivalCountdown? {
    if (nowEpochSeconds !in 946_684_800L..253_402_300_799L) return null
    val scheduled = plannedArrival.toTransitEpochSecondsOrNull()
    val realtime = if (trustedRealtime) actualArrival.toTransitEpochSecondsOrNull() else null
    val arrival = realtime ?: scheduled ?: return null
    val remainingSeconds = arrival - nowEpochSeconds
    if (remainingSeconds <= 0L || remainingSeconds > 172_800L) return null
    return DestinationArrivalCountdown(
        minutes = (remainingSeconds + 59L) / 60L,
        fromRealtime = realtime != null && (scheduled == null || realtime != scheduled),
    )
}

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
