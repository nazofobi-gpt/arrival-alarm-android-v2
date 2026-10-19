package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val context = LocalContext.current
    val preferences = remember(context) { ArrivalUiPreferences(context) }
    var directOnly by rememberSaveable { mutableStateOf(preferences.loadDirectOnlyRoutes()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mode == RouteJourneyMode.RESULTS) {
            Text(
                text = stringResource(R.string.route_results_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.route_points_format,
                                origin.label,
                                destination.label,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        routeStatus?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("route-status"),
                            )
                        }
                        if (routeOffline) {
                            Text(
                                text = stringResource(R.string.route_offline_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("route-offline-badge"),
                            )
                        }
                        Button(
                            onClick = onRefreshRoutes,
                            enabled = !routeBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("route-refresh"),
                        ) {
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

                val visibleOptions = RouteResultsFilter(directOnly).apply(routeOptions)
                FilterChip(
                    selected = directOnly,
                    onClick = {
                        directOnly = !directOnly
                        preferences.saveDirectOnlyRoutes(directOnly)
                    },
                    label = { Text(stringResource(R.string.route_direct_only_filter)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("route-direct-only-filter"),
                )

                visibleOptions.take(6).forEachIndexed { index, option ->
                    RouteOptionCard(
                        option = option,
                        selected = selectedRouteId == option.id,
                        onSelect = { onSelectRoute(option) },
                        selectTestTag = "route-option-select-" + index,
                        modifier = Modifier.testTag("route-option-" + index),
                    )
                }

                if (!routeBusy && visibleOptions.isEmpty()) {
                    Text(
                        text = if (directOnly && routeOptions.isNotEmpty()) {
                            stringResource(R.string.route_direct_only_empty)
                        } else {
                            routeStatus ?: stringResource(R.string.route_not_found)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("route-results-empty"),
                    )
                }
            }
        } else {
            val selected = selectedRouteId
                ?.let { id -> routeOptions.firstOrNull { it.id == id } }
            if (selected == null) {
                Text(
                    text = stringResource(R.string.journey_detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
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
private fun RouteOptionCard(
    option: RouteOption,
    selected: Boolean,
    onSelect: () -> Unit,
    selectTestTag: String,
    modifier: Modifier = Modifier,
) {
    val duration = routeDurationMinutes(option)
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

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = duration?.let {
                    stringResource(R.string.route_duration_minutes, it)
                } ?: (option.departure + " → " + option.arrival),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = option.departure + " → " + option.arrival,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = option.line,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = option.direction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.route_walk_transfer_format,
                    option.walkingMinutes,
                    option.transfers,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(realtimeLabel),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    option.realtime?.cancelledTripIds?.isNotEmpty() == true ->
                        MaterialTheme.colorScheme.error
                    hasRealtime -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedButton(
                onClick = onSelect,
                enabled = !selected &&
                    option.realtime?.cancelledTripIds?.isNotEmpty() != true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(selectTestTag),
            ) {
                Text(
                    if (selected) {
                        stringResource(R.string.route_selected_action)
                    } else {
                        stringResource(R.string.route_select_action)
                    }
                )
            }
        }
    }
}

@Composable
private fun JourneyDetailCard(option: RouteOption) {
    val duration = routeDurationMinutes(option)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("journey-detail"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.journey_detail_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            duration?.let {
                Text(
                    text = stringResource(R.string.route_duration_minutes, it),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = option.line + " • " + option.direction,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = option.departure + " → " + option.arrival,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            if (option.stops.isEmpty()) {
                JourneyEndpoint(
                    time = option.departure,
                    label = option.origin.label,
                    testTag = "journey-stop-origin",
                )
                JourneyEndpoint(
                    time = option.arrival,
                    label = option.destination.label,
                    testTag = "journey-stop-destination",
                )
            } else {
                val expandedStops = remember(option.id) { mutableStateOf(false) }
                val visibleStopIndices = journeyStopPreviewIndices(option.stops.size, expandedStops.value)
                visibleStopIndices.forEachIndexed { visibleIndex, stopIndex ->
                    if (visibleIndex > 0) {
                        val hiddenCount = stopIndex - visibleStopIndices[visibleIndex - 1] - 1
                        if (hiddenCount > 0) {
                            Text(
                                text = stringResource(R.string.journey_stops_hidden_count, hiddenCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("journey-stops-hidden"),
                            )
                            HorizontalDivider()
                        }
                    }
                    val index = stopIndex
                    val stop = option.stops[stopIndex]
                    val actual = stop.departure ?: stop.arrival
                    val planned = stop.plannedDeparture ?: stop.plannedArrival
                    val displayTime = routeClock(actual ?: planned)
                        ?: stringResource(R.string.unknown_time)
                    val realtime = hasRealtimeDelta(stop)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("journey-stop-" + index),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = displayTime,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (realtime) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = stop.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
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
                                style = MaterialTheme.typography.labelSmall,
                                color = if (stop.platform != null) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
                    if (visibleIndex != visibleStopIndices.lastIndex) {
                        HorizontalDivider()
                    }
                }
                if (option.stops.size > COLLAPSED_JOURNEY_STOP_LIMIT) {
                    OutlinedButton(
                        onClick = { expandedStops.value = !expandedStops.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("journey-stops-toggle"),
                    ) {
                        Text(
                            if (expandedStops.value) {
                                stringResource(R.string.journey_show_fewer_stops)
                            } else {
                                stringResource(R.string.journey_show_all_stops_count, option.stops.size)
                            },
                        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.phase_format,
                    journeyPhaseText(journey.phase),
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.testTag("phase"),
            )
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
                    .heightIn(min = 48.dp)
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

/** Calendar-day-safe duration for scheduled trips; unknown dates fail closed. */
internal fun routeDurationMinutes(option: RouteOption): Int? {
    val departure = option.departureEpochMillis
    val arrival = option.arrivalEpochMillis
    if (departure != null || arrival != null) {
        if (departure == null || arrival == null || arrival < departure) return null
        val elapsedMillis = try {
            Math.subtractExact(arrival, departure)
        } catch (_: ArithmeticException) {
            return null
        }
        val minutes = elapsedMillis / 60_000L
        return minutes.takeIf { it <= Int.MAX_VALUE.toLong() }?.toInt()
    }
    return routeDurationMinutes(option.departure, option.arrival)
}

private fun routeDurationMinutes(departure: String, arrival: String): Int? {
    fun minutes(clock: String): Int? {
        val raw = clock.substringAfter('T', clock)
        // Undated labels must never masquerade as a multi-day schedule.
        if (raw.length != 5 && raw.length != 8) return null
        if (raw[2] != ':' || (raw.length == 8 && raw[5] != ':')) return null
        val hour = raw.substring(0, 2).toIntOrNull() ?: return null
        val minute = raw.substring(3, 5).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        if (raw.length == 8) {
            val seconds = raw.substring(6, 8).toIntOrNull() ?: return null
            if (seconds !in 0..59) return null
        }
        return hour * 60 + minute
    }
    val start = minutes(departure) ?: return null
    val end = minutes(arrival) ?: return null
    return if (end >= start) end - start else end + 24 * 60 - start
}

/** Invalid provider labels must not be displayed as a scheduled stop clock. */
internal fun routeClock(value: String?): String? {
    val raw = value?.substringAfter('T', value)?.takeIf { it.isNotBlank() } ?: return null
    if (raw.length != 5 && raw.length != 8) return null
    if (raw[2] != ':' || (raw.length == 8 && raw[5] != ':')) return null
    if (!raw.substring(0, 2).all { it in '0'..'9' } ||
        !raw.substring(3, 5).all { it in '0'..'9' }) return null
    val minute = raw.substring(3, 5).toInt()
    if (minute !in 0..59) return null
    if (raw.length == 8) {
        if (!raw.substring(6, 8).all { it in '0'..'9' }) return null
        if (raw.substring(6, 8).toInt() !in 0..59) return null
    }
    // GTFS extended service-day hours (24+) are valid; do not silently wrap them.
    return raw.substring(0, 5)
}

private fun hasRealtimeDelta(stop: RouteStop): Boolean =
    (stop.arrival != null && stop.plannedArrival != null && stop.arrival != stop.plannedArrival) ||
        (stop.departure != null && stop.plannedDeparture != null && stop.departure != stop.plannedDeparture)
