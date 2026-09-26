package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (origin != null && destination != null) {
            Text(
                text = stringResource(R.string.route_results_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )

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

            routeOptions.take(3).forEachIndexed { index, option ->
                RouteOptionCard(
                    option = option,
                    selected = selectedRouteId == option.id,
                    onSelect = { onSelectRoute(option) },
                    modifier = Modifier.testTag("route-option-" + index),
                )
            }

            selectedRouteId
                ?.let { id -> routeOptions.firstOrNull { it.id == id } }
                ?.let { selected ->
                    JourneyDetailCard(selected)
                }
        }

        JourneyAlarmControls(
            journey = journey,
            onArmAlarm = onArmAlarm,
            onCancelAlarm = onCancelAlarm,
        )
    }
}

@Composable
private fun RouteOptionCard(
    option: RouteOption,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = routeDurationMinutes(option.departure, option.arrival)
    val hasRealtime = option.stops.any(::hasRealtimeDelta)

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
                text = if (hasRealtime) {
                    stringResource(R.string.route_realtime_badge)
                } else {
                    stringResource(R.string.route_planned_badge)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (hasRealtime) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedButton(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
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
    val duration = routeDurationMinutes(option.departure, option.arrival)
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
                val visibleStops = option.stops.take(16)
                visibleStops.forEachIndexed { index, stop ->
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
                        if (realtime) {
                            Text(
                                text = stringResource(R.string.route_realtime_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    if (index != visibleStops.lastIndex) {
                        HorizontalDivider()
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
private fun JourneyAlarmControls(
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
