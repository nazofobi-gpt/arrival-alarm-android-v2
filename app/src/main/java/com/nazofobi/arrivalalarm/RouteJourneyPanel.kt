package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class RouteJourneyMode {
    RESULTS,
    DETAIL,
}

@Composable
fun RouteJourneyPanel(
    origin: MapPoint?,
    destination: MapPoint?,
    routeOptions: List<RouteOption>,
    routeBusy: Boolean,
    routeStatus: String?,
    routeOffline: Boolean,
    selectedRouteId: String?,
    journey: JourneyUiState,
    onRefreshRoutes: () -> Unit,
    onSelectRoute: (RouteOption) -> Unit,
    onArmAlarm: () -> Unit,
    onCancelAlarm: () -> Unit,
    mode: RouteJourneyMode = RouteJourneyMode.RESULTS,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (mode == RouteJourneyMode.RESULTS) {
            Text(
                text = stringResource(R.string.route_results_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )

            if (origin == null || destination == null) {
                Text(
                    text = stringResource(R.string.error_start_destination_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("route-results-empty"),
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        JourneyPointLine(
                            origin = origin.label,
                            destination = destination.label,
                        )
                        routeStatus?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("route-status"),
                            )
                        }
                        if (routeOffline) {
                            StatusPill(
                                label = stringResource(R.string.route_offline_badge),
                                critical = false,
                                modifier = Modifier.testTag("route-offline-badge"),
                            )
                        }
                        FilledTonalButton(
                            onClick = onRefreshRoutes,
                            enabled = !routeBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("route-refresh"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (routeBusy) {
                                    stringResource(R.string.route_calculating)
                                } else {
                                    stringResource(R.string.refresh_routes)
                                }
                            )
                        }
                    }
                }

                routeOptions.take(6).forEachIndexed { index, option ->
                    RouteOptionCard(
                        option = option,
                        selected = selectedRouteId == option.id,
                        onSelect = { onSelectRoute(option) },
                        selectTestTag = "route-option-select-" + index,
                        modifier = Modifier.testTag("route-option-" + index),
                    )
                }

                if (!routeBusy && routeOptions.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = routeStatus ?: stringResource(R.string.route_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(16.dp)
                                .testTag("route-results-empty"),
                        )
                    }
                }
            }
        } else {
            val selected = selectedRouteId
                ?.let { id -> routeOptions.firstOrNull { it.id == id } }
            if (selected == null) {
                Text(
                    text = stringResource(R.string.journey_detail_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.error_trip_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("journey-detail-empty"),
                )
            } else {
                JourneyDetailCard(selected)
            }

            JourneyAlarmControls(
                journey = journey,
                onArmAlarm = onArmAlarm,
                onCancelAlarm = onCancelAlarm,
            )
        }
    }
}

@Composable
private fun JourneyPointLine(
    origin: String,
    destination: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.TripOrigin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = origin,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = " → ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = destination,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RouteOptionCard(
    option: RouteOption,
    selected: Boolean,
    onSelect: () -> Unit,
    selectTestTag: String,
    modifier: Modifier = Modifier,
) {
    val duration = routeDurationMinutes(option.departure, option.arrival)
    val hasRealtime = option.stops.any(::hasRealtimeDelta)
    val realtimeLabel = when {
        option.realtime?.cancelledTripIds?.isNotEmpty() == true ->
            R.string.route_realtime_cancelled_badge
        hasRealtime -> R.string.route_realtime_badge
        option.realtime?.state == RouteRealtimeState.STALE ->
            R.string.route_realtime_stale_badge
        option.realtime?.state == RouteRealtimeState.FRESH_NO_MATCH ->
            R.string.route_realtime_no_match_badge
        option.realtime?.state == RouteRealtimeState.UNAVAILABLE ->
            R.string.route_realtime_unavailable_badge
        else -> R.string.route_planned_badge
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routeClock(option.departure) ?: option.departure,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = option.origin.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsTransit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = duration?.let {
                            stringResource(R.string.route_duration_minutes, it)
                        } ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = routeClock(option.arrival) ?: option.arrival,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = option.destination.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = option.line,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Text(
                    text = option.direction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        R.string.route_walk_transfer_format,
                        option.walkingMinutes,
                        option.transfers,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    label = stringResource(realtimeLabel),
                    critical = option.realtime?.cancelledTripIds?.isNotEmpty() == true,
                    success = hasRealtime,
                )
            }

            if (selected) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(R.string.route_selected_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    enabled = option.realtime?.cancelledTripIds?.isNotEmpty() != true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(selectTestTag),
                ) {
                    Text(stringResource(R.string.route_select_action))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    critical: Boolean,
    success: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val container = when {
        critical -> MaterialTheme.colorScheme.errorContainer
        success -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        critical -> MaterialTheme.colorScheme.onErrorContainer
        success -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun JourneyDetailCard(option: RouteOption) {
    val duration = routeDurationMinutes(option.departure, option.arrival)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("journey-detail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.journey_detail_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsTransit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = option.line + " • " + option.direction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = option.departure + " → " + option.arrival,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    duration?.let {
                        Text(
                            text = " • " + stringResource(R.string.route_duration_minutes, it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (option.stops.isEmpty()) {
                    JourneyEndpoint(
                        time = option.departure,
                        label = option.origin.label,
                        testTag = "journey-stop-origin",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    JourneyEndpoint(
                        time = option.arrival,
                        label = option.destination.label,
                        testTag = "journey-stop-destination",
                    )
                } else {
                    val visibleStops = option.stops.take(16)
                    visibleStops.forEachIndexed { index, stop ->
                        val actual = stop.departure ?: stop.arrival
                        val planned = stop.plannedDeparture ?: stop.plannedArrival
                        val displayTime = routeClock(actual ?: planned)
                            ?: stringResource(R.string.unknown_time)
                        val realtime = hasRealtimeDelta(stop)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("journey-stop-" + index)
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = displayTime,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (realtime) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.width(56.dp),
                            )
                            Icon(
                                imageVector = Icons.Rounded.TripOrigin,
                                contentDescription = null,
                                tint = if (realtime) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Column(
                                modifier = Modifier.padding(start = 10.dp).weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stop.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                (stop.platform ?: stop.plannedPlatform)?.let { platform ->
                                    Text(
                                        text = stringResource(
                                            if (stop.platform != null) {
                                                R.string.route_realtime_platform_format
                                            } else {
                                                R.string.route_planned_platform_format
                                            },
                                            platform,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (realtime) {
                                    Text(
                                        text = stringResource(R.string.route_realtime_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                        if (index != visibleStops.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyEndpoint(
    time: String,
    label: String,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = routeClock(time) ?: time,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Icon(
            imageVector = Icons.Rounded.TripOrigin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
internal fun JourneyAlarmControls(
    journey: JourneyUiState,
    onArmAlarm: () -> Unit,
    onCancelAlarm: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.phase_format,
                        journeyPhaseText(journey.phase),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 8.dp).testTag("phase"),
                )
            }
            journey.error?.let {
                Text(
                    text = localizedDomainMessage(context, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("error"),
                )
            }
            Button(
                onClick = onArmAlarm,
                enabled = journey.phase == JourneyPhase.DESTINATION_SELECTED,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("arm"),
            ) {
                Text(stringResource(R.string.arm_alarm))
            }
            OutlinedButton(
                onClick = onCancelAlarm,
                enabled = journey.phase == JourneyPhase.ARMED ||
                    journey.phase == JourneyPhase.ARRIVED,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("cancel-alarm"),
            ) {
                Text(stringResource(R.string.cancel_alarm))
            }
        }
    }
}

private fun routeDurationMinutes(departure: String, arrival: String): Int? {
    fun minutes(clock: String): Int? {
        val raw = clock.substringAfter('T', clock)
        val hour = raw.take(2).toIntOrNull() ?: return null
        val minute = raw.drop(3).take(2).toIntOrNull() ?: return null
        return hour * 60 + minute
    }
    val start = minutes(departure) ?: return null
    val end = minutes(arrival) ?: return null
    return if (end >= start) end - start else end + 24 * 60 - start
}

private fun routeClock(value: String?): String? {
    val raw = value?.substringAfter('T', value)?.takeIf { it.isNotBlank() } ?: return null
    return raw.take(5).takeIf { it.length >= 4 }
}

private fun hasRealtimeDelta(stop: RouteStop): Boolean =
    (stop.arrival != null && stop.plannedArrival != null && stop.arrival != stop.plannedArrival) ||
        (stop.departure != null && stop.plannedDeparture != null && stop.departure != stop.plannedDeparture)
