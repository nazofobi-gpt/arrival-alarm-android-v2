package com.nazofobi.arrivalalarm

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var fewerTransfersFirst by rememberSaveable {
        mutableStateOf(preferences.loadFewerTransfersFirst())
    }
    var shorterWalkingFirst by rememberSaveable {
        mutableStateOf(preferences.loadShorterWalkingFirst())
    }
    var atMostOneTransfer by rememberSaveable {
        mutableStateOf(preferences.loadAtMostOneTransfer())
    }
    var maxWalking15Minutes by rememberSaveable {
        mutableStateOf(preferences.loadMaxWalking15Minutes())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A previously queued walk reminder must remain cancellable when
        // routes disappear, filters hide every option, or the selection is lost.
        // A selected journey already has its own reminder control below.
        if (mode == RouteJourneyMode.RESULTS ||
            selectedRouteId == null || routeOptions.none { it.id == selectedRouteId }) {
            PendingWalkReminderWithoutRoute()
        }
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

                val visibleOptions = RouteResultsFilter(
                    directOnly = directOnly,
                    fewerTransfersFirst = fewerTransfersFirst,
                    shorterWalkingFirst = shorterWalkingFirst,
                    maxTransfers = if (atMostOneTransfer) 1 else null,
                    maxWalkingMinutes = if (maxWalking15Minutes) 15 else null,
                ).apply(routeOptions)
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
                FilterChip(
                    selected = fewerTransfersFirst,
                    onClick = {
                        fewerTransfersFirst = !fewerTransfersFirst
                        preferences.saveFewerTransfersFirst(fewerTransfersFirst)
                    },
                    label = { Text(stringResource(R.string.route_fewer_transfers_filter)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("route-fewer-transfers-filter"),
                )
                FilterChip(
                    selected = shorterWalkingFirst,
                    onClick = {
                        shorterWalkingFirst = !shorterWalkingFirst
                        preferences.saveShorterWalkingFirst(shorterWalkingFirst)
                    },
                    label = { Text(stringResource(R.string.route_shorter_walking_filter)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("route-shorter-walking-filter"),
                )

                FilterChip(
                    selected = atMostOneTransfer,
                    onClick = {
                        atMostOneTransfer = !atMostOneTransfer
                        preferences.saveAtMostOneTransfer(atMostOneTransfer)
                    },
                    label = { Text(stringResource(R.string.route_at_most_one_transfer_filter)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("route-at-most-one-transfer-filter"),
                )

                FilterChip(
                    selected = maxWalking15Minutes,
                    onClick = {
                        maxWalking15Minutes = !maxWalking15Minutes
                        preferences.saveMaxWalking15Minutes(maxWalking15Minutes)
                        // Fetch new candidates: the old three cards may all exceed the budget.
                        onRefreshRoutes()
                    },
                    label = { Text(stringResource(R.string.route_max_walking_15_filter)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("route-max-walking-15-filter"),
                )

                visibleOptions.take(6).forEachIndexed { index, option ->
                    RouteOptionCard(
                        option = option,
                        selected = selectedRouteId == option.id,
                        routeOffline = routeOffline,
                        onSelect = { onSelectRoute(option) },
                        selectTestTag = "route-option-select-" + index,
                        modifier = Modifier.testTag("route-option-" + index),
                    )
                }

                if (!routeBusy && visibleOptions.isEmpty()) {
                    Text(
                        text = when {
                            directOnly && routeOptions.isNotEmpty() ->
                                stringResource(R.string.route_direct_only_empty)
                            atMostOneTransfer && routeOptions.isNotEmpty() ->
                                stringResource(R.string.route_at_most_one_transfer_empty)
                            maxWalking15Minutes && routeOptions.isNotEmpty() ->
                                stringResource(R.string.route_max_walking_15_empty)
                            else -> routeStatus ?: stringResource(R.string.route_not_found)
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
                JourneyDetailCard(selected, routeOffline = routeOffline)
            }

            JourneyAlarmControls(
                journey = journey,
                onArmAlarm = onArmAlarm,
                onCancelAlarm = onCancelAlarm,
            )
        }
    }
}

/** Display the persisted scheduled stop/time, never a recomputed time from a new route. */
@Composable
private fun StoredWalkReminderDetails(scheduler: PlannedWalkReminderScheduler) {
    val details = scheduler.scheduledDetails() ?: return
    val clock = remember(details.startMillis) {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
        ).format(java.util.Date(details.startMillis))
    }
    Text(
        text = stringResource(R.string.route_walk_reminder_details, details.stopName, clock),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("route-walk-reminder-details"),
    )
}

/**
 * An opt-in reminder must not become impossible to revoke when a route card is
 * unavailable. This is status + explicit cancellation only; never arms a job.
 */
@Composable
private fun PendingWalkReminderWithoutRoute() {
    val context = LocalContext.current
    val scheduler = remember(context) {
        PlannedWalkReminderScheduler(context.applicationContext)
    }
    var queuedRoute by remember(context) { mutableStateOf(scheduler.scheduledRouteId()) }
    var cancelBusy by remember { mutableStateOf(false) }
    var cancelFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (queuedRoute == null) return

    Card(
        modifier = Modifier.fillMaxWidth().testTag("route-walk-reminder-global-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.route_walk_reminder_queued),
                style = MaterialTheme.typography.bodyMedium,
            )
            StoredWalkReminderDetails(scheduler)
            Text(
                text = stringResource(R.string.route_walk_reminder_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    if (!cancelBusy) {
                        cancelBusy = true
                        scope.launch {
                            val succeeded = withContext(Dispatchers.IO) { scheduler.cancel() }
                            queuedRoute = scheduler.scheduledRouteId()
                            cancelFailed = !succeeded
                            cancelBusy = false
                        }
                    }
                },
                enabled = !cancelBusy,
                modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("route-walk-reminder-global-cancel"),
            ) { Text(stringResource(R.string.route_walk_reminder_cancel)) }
            if (cancelFailed) {
                Text(
                    text = stringResource(R.string.route_walk_reminder_failure),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("route-walk-reminder-global-failure"),
                )
            }
        }
    }
}

@Composable
private fun RouteOptionCard(
    option: RouteOption,
    selected: Boolean,
    routeOffline: Boolean,
    onSelect: () -> Unit,
    selectTestTag: String,
    modifier: Modifier = Modifier,
) {
    val displayDeparture = routeOptionEvidenceClock(option, departure = true, routeOffline = routeOffline)
        ?: stringResource(R.string.unknown_time)
    val displayArrival = routeOptionEvidenceClock(option, departure = false, routeOffline = routeOffline)
        ?: stringResource(R.string.unknown_time)
    val duration = if (routeOffline) routeDurationMinutes(displayDeparture, displayArrival)
        else routeDurationMinutes(option)
    val hasRealtime = routeHasVerifiedRealtimeDelta(
        routeOffline = routeOffline,
        state = option.realtime?.state,
        suppliedDelta = option.stops.any(::hasRealtimeDelta),
    )
    val hasVerifiedCancellation = routeHasVerifiedRealtimeCancellation(
        routeOffline = routeOffline,
        state = option.realtime?.state,
        suppliedCancellation = option.realtime?.cancelledTripIds?.isNotEmpty() == true,
    )
    val realtimeLabel = when {
        routeOffline -> R.string.route_offline_badge
        hasVerifiedCancellation -> R.string.route_realtime_cancelled_badge
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
                } ?: (displayDeparture + " → " + displayArrival),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = displayDeparture + " → " + displayArrival,
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
                    routeOffline -> MaterialTheme.colorScheme.tertiary
                    hasVerifiedCancellation -> MaterialTheme.colorScheme.error
                    hasRealtime -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedButton(
                onClick = onSelect,
                enabled = !selected && !hasVerifiedCancellation,
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
private fun JourneyDetailCard(option: RouteOption, routeOffline: Boolean) {
    val displayDeparture = routeOptionEvidenceClock(option, departure = true, routeOffline = routeOffline)
        ?: stringResource(R.string.unknown_time)
    val displayArrival = routeOptionEvidenceClock(option, departure = false, routeOffline = routeOffline)
        ?: stringResource(R.string.unknown_time)
    val duration = if (routeOffline) routeDurationMinutes(displayDeparture, displayArrival)
        else routeDurationMinutes(option)
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
                text = displayDeparture + " → " + displayArrival,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Only local planning provides separate distance-based access/egress estimates.
            // These are stop contexts, never inferred pedestrian map polylines.
            option.accessWalking?.takeIf { it.walkSeconds > 0 }?.let { leg ->
                val roundedMinutes = ((leg.walkSeconds.toLong() + 59L) / 60L).toInt()
                Text(
                    text = stringResource(
                        R.string.route_walk_access_detail, leg.stopName, roundedMinutes,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("route-walk-access"),
                )
            }
            PlannedWalkStartNotice(option)
            option.egressWalking?.takeIf { it.walkSeconds > 0 }?.let { leg ->
                val roundedMinutes = ((leg.walkSeconds.toLong() + 59L) / 60L).toInt()
                Text(
                    text = stringResource(
                        R.string.route_walk_egress_detail, leg.stopName, roundedMinutes,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("route-walk-egress"),
                )
            }
            if (option.accessWalking != null || option.egressWalking != null) {
                Text(
                    text = stringResource(R.string.route_walk_source_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("route-walk-source"),
                )
            }

            HorizontalDivider()

            if (option.stops.isEmpty()) {
                JourneyEndpoint(
                    time = displayDeparture,
                    label = option.origin.label,
                    testTag = "journey-stop-origin",
                )
                JourneyEndpoint(
                    time = displayArrival,
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
                    val displayTime = routeClock(routeEvidenceTime(actual, planned, routeOffline))
                        ?: stringResource(R.string.unknown_time)
                    val realtime = routeHasVerifiedRealtimeDelta(
                        routeOffline = routeOffline,
                        state = option.realtime?.state,
                        suppliedDelta = hasRealtimeDelta(stop),
                    )
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
                        routeEvidencePlatform(stop.platform, stop.plannedPlatform, routeOffline)
                            ?.let { platform ->
                            val realtimePlatform = routeHasVerifiedRealtimePlatform(
                                routeOffline = routeOffline,
                                state = option.realtime?.state,
                                actualPlatform = stop.platform,
                            )
                            Text(
                                text = stringResource(
                                    if (realtimePlatform) {
                                        R.string.route_realtime_platform_format
                                    } else {
                                        R.string.route_planned_platform_format
                                    },
                                    platform,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (realtimePlatform) {
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

/**
 * Route-level clocks may already contain old realtime overlays. In offline mode,
 * derive endpoints only from scheduled stops. Online provider clocks must be validated
 * and fall back to the scheduled endpoint if malformed; missing proof stays unknown.
 */
private fun routeOptionEvidenceClock(option: RouteOption, departure: Boolean, routeOffline: Boolean): String? {
    val stop = if (departure) option.stops.firstOrNull() else option.stops.lastOrNull()
    val scheduled = if (departure) {
        stop?.plannedDeparture ?: stop?.plannedArrival
    } else {
        stop?.plannedArrival ?: stop?.plannedDeparture
    }
    val actual = if (departure) option.departure else option.arrival
    return routeSummaryEvidenceClock(actual, scheduled, routeOffline)
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
    routeHasUsableRealtimeDelta(stop.arrival, stop.plannedArrival) ||
        routeHasUsableRealtimeDelta(stop.departure, stop.plannedDeparture)

/** Informational source-backed walking start: no reminder or notification is armed. */
/** An optional user-confirmed Calendar draft; never silently arms an event or reminder. */
@Composable
internal fun PlannedWalkStartNotice(
    option: RouteOption,
    calendarLauncher: ((Intent) -> Boolean)? = null,
    nowMillisForTest: Long? = null,
    bufferMinutesForTest: Int? = null,
    directionsLauncher: ((Intent) -> Boolean)? = null,
    notificationPermissionReadyForTest: Boolean? = null,
    notificationSettingsLauncher: ((Intent) -> Boolean)? = null,
    notificationChannelMutedForTest: Boolean? = null,
    notificationPreviewSender: (() -> Boolean)? = null,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("route_walk_preferences", android.content.Context.MODE_PRIVATE)
    }
    var safetyBufferMinutes by remember(option.id, bufferMinutesForTest) {
        mutableStateOf(
            bufferMinutesForTest ?: preferences.getInt("safety_buffer_minutes", 0)
                .takeIf { it == 0 || it == 5 || it == 10 || it == 15 } ?: 0,
        )
    }
    var bufferSaveFailed by remember(option.id) { mutableStateOf(false) }
    val startEpoch = plannedWalkStartEpochMillis(option, safetyBufferMinutes)
    if (startEpoch == null) {
        // Installed GTFS data can disappear or change after an opt-in reminder was
        // queued. Keep its cancellation available without inventing a walk time.
        val scheduler = remember(context) {
            PlannedWalkReminderScheduler(context.applicationContext)
        }
        var pending by remember(option.id) {
            mutableStateOf(scheduler.scheduledRouteId() != null)
        }
        var cancelBusy by remember(option.id) { mutableStateOf(false) }
        var cancelFailed by remember(option.id) { mutableStateOf(false) }
        val cancelScope = rememberCoroutineScope()
        if (pending) {
            if (!scheduler.isScheduledForRoute(option.id)) {
                Text(
                    text = stringResource(R.string.route_walk_reminder_other_route),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("route-walk-reminder-other-route"),
                )
            }
            StoredWalkReminderDetails(scheduler)
            OutlinedButton(
                onClick = {
                    if (!cancelBusy) {
                        cancelBusy = true
                        cancelScope.launch {
                            val succeeded = withContext(Dispatchers.IO) {
                                scheduler.cancel()
                            }
                            pending = scheduler.scheduledRouteId() != null
                            cancelFailed = !succeeded
                            cancelBusy = false
                        }
                    }
                },
                enabled = !cancelBusy,
                modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("route-walk-reminder-toggle"),
            ) { Text(stringResource(R.string.route_walk_reminder_cancel)) }
            Text(
                text = stringResource(R.string.route_walk_reminder_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("route-walk-reminder-note"),
            )
            if (cancelFailed) {
                Text(
                    text = stringResource(R.string.route_walk_reminder_failure),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("route-walk-reminder-failure"),
                )
            }
        }
        return
    }
    var liveNowMillis by remember(option.id, startEpoch) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(option.id, startEpoch, nowMillisForTest) {
        if (nowMillisForTest == null) {
            while (true) {
                liveNowMillis = System.currentTimeMillis()
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }
    val displayNowMillis = nowMillisForTest ?: liveNowMillis
    var launchFailed by remember(option.id, startEpoch) { mutableStateOf(false) }
    var directionsFailed by remember(option.id, startEpoch) { mutableStateOf(false) }
    // A time alone is ambiguous for overnight and next-day trips. Format the
    // verified instant in the device's local calendar, including its date.
    val clock = remember(startEpoch) {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
        ).format(java.util.Date(startEpoch))
    }
    OutlinedButton(
        onClick = {
            val next = if (safetyBufferMinutes >= 15) 0 else safetyBufferMinutes + 5
            if (bufferMinutesForTest == null) {
                if (preferences.edit().putInt("safety_buffer_minutes", next).commit()) {
                    safetyBufferMinutes = next
                    bufferSaveFailed = false
                } else {
                    bufferSaveFailed = true
                }
            } else {
                safetyBufferMinutes = next
            }
        },
        modifier = Modifier.heightIn(min = 48.dp).testTag("route-walk-safety-buffer"),
    ) { Text(stringResource(R.string.route_planned_walk_buffer_action, safetyBufferMinutes)) }
    if (bufferSaveFailed) {
        Text(
            text = stringResource(R.string.route_planned_walk_buffer_failure),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("route-walk-buffer-save-failure"),
        )
    }
    Text(
        text = stringResource(R.string.route_planned_walk_start, clock),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("route-planned-walk-start"),
    )
    Text(
        text = stringResource(R.string.route_planned_walk_start_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("route-planned-walk-start-note"),
    )
    plannedWalkMinutesRemaining(option, displayNowMillis, safetyBufferMinutes)?.let { minutes ->
        Text(
            text = stringResource(R.string.route_planned_walk_remaining_minutes, minutes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("route-walk-minutes-remaining"),
        )
    }
    when (plannedWalkWarning(option, displayNowMillis, safetyBufferMinutes)) {
        PlannedWalkWarning.WALK_START_PASSED -> Text(
            text = stringResource(R.string.route_planned_walk_late_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("route-walk-start-passed"),
        )
        PlannedWalkWarning.PLANNED_DEPARTURE_PASSED -> Text(
            text = stringResource(R.string.route_planned_walk_departure_passed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("route-walk-departure-passed"),
        )
        null -> Unit
    }
    // Opt-in, best-effort WorkManager notification; never a guaranteed wake-up alarm.
    // An existing reminder is still cancellable if a later buffer choice changed.
    val reminderScheduler = remember(context) {
        PlannedWalkReminderScheduler(context.applicationContext)
    }
    val reminderScope = rememberCoroutineScope()
    // A denied notification permission needs explicit recovery, not a false schedule.
    var notificationsReady by remember(
        option.id, notificationPermissionReadyForTest, notificationChannelMutedForTest,
    ) {
        mutableStateOf(
            (notificationPermissionReadyForTest
                ?: PlannedWalkReminderScheduler.notificationPermissionGranted(context)) &&
                notificationChannelMutedForTest != true,
        )
    }
    val notificationSettingsResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        notificationsReady = (notificationPermissionReadyForTest
            ?: PlannedWalkReminderScheduler.notificationPermissionGranted(context)) &&
            notificationChannelMutedForTest != true
    }
    var notificationSettingsFailed by remember(option.id) { mutableStateOf(false) }
    var reminderScheduled by remember(option.id) {
        mutableStateOf(reminderScheduler.scheduledRouteId() != null)
    }
    var reminderActionFailed by remember(option.id) { mutableStateOf(false) }
    var reminderBusy by remember(option.id) { mutableStateOf(false) }
    val reminderDelay = plannedWalkReminderDelayMillis(
        option, displayNowMillis, safetyBufferMinutes,
    )
    if (reminderScheduled || reminderDelay != null) {
        var reminderPreviewResult by remember(option.id) { mutableStateOf<Boolean?>(null) }
        // A previous route's reminder must be disclosed and cancelled explicitly;
        // never silently replace it when a different journey is selected.
        if (reminderScheduled && !reminderScheduler.isScheduledForRoute(option.id)) {
            Text(
                text = stringResource(R.string.route_walk_reminder_other_route),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("route-walk-reminder-other-route"),
            )
        }
        if (!notificationsReady) {
            Text(
                text = stringResource(R.string.route_walk_reminder_notifications_disabled),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("route-walk-reminder-notifications-disabled"),
            )
            OutlinedButton(
                onClick = {
                    // Opening Settings is explicit; returning never auto-schedules.
                    val settings = if (Build.VERSION.SDK_INT >= 26 &&
                        (notificationChannelMutedForTest
                            ?: PlannedWalkReminderScheduler.notificationChannelMuted(context))
                    ) {
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, PlannedWalkReminderScheduler.CHANNEL)
                    } else if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"))
                    }
                    notificationSettingsFailed = if (notificationSettingsLauncher != null) {
                        !notificationSettingsLauncher(settings)
                    } else {
                        !runCatching { notificationSettingsResult.launch(settings); true }
                            .getOrDefault(false)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("route-walk-reminder-settings"),
            ) { Text(stringResource(R.string.route_walk_reminder_open_notification_settings)) }
            if (notificationSettingsFailed) {
                Text(
                    text = stringResource(R.string.route_walk_reminder_settings_failure),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("route-walk-reminder-settings-failure"),
                )
            }
        }
        OutlinedButton(
            onClick = {
                if (!reminderBusy) {
                    reminderBusy = true
                    val cancel = reminderScheduled
                    reminderScope.launch {
                        val worked = withContext(Dispatchers.IO) {
                            if (cancel) reminderScheduler.cancel()
                            else reminderScheduler.schedule(option, safetyBufferMinutes)
                        }
                        reminderScheduled = reminderScheduler.scheduledRouteId() != null
                        reminderActionFailed = !worked
                        reminderBusy = false
                    }
                }
            },
            enabled = !reminderBusy && (reminderScheduled || notificationsReady),
            modifier = Modifier.heightIn(min = 48.dp)
                .testTag("route-walk-reminder-toggle"),
        ) {
            Text(stringResource(
                if (reminderScheduled) R.string.route_walk_reminder_cancel
                else R.string.route_walk_reminder_set,
            ))
        }
        Text(
            text = stringResource(R.string.route_walk_reminder_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("route-walk-reminder-note"),
        )
        // Testing this channel is opt-in, has a distinct notification ID, and
        // neither schedules a walking reminder nor arms an arrival alarm.
        OutlinedButton(
            onClick = {
                reminderPreviewResult = (notificationPreviewSender
                    ?: { PlannedWalkReminderPreview.post(context) })()
            },
            enabled = notificationsReady && !reminderBusy,
            modifier = Modifier.heightIn(min = 48.dp)
                .testTag("route-walk-reminder-preview"),
        ) { Text(stringResource(R.string.route_walk_reminder_preview_action)) }
        reminderPreviewResult?.let { submitted ->
            Text(
                text = stringResource(
                    if (submitted) R.string.route_walk_reminder_preview_submitted
                    else R.string.route_walk_reminder_preview_failed,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (submitted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("route-walk-reminder-preview-result"),
            )
        }
        if (reminderScheduled) {
            Text(
                text = stringResource(R.string.route_walk_reminder_queued),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("route-walk-reminder-queued"),
            )
            StoredWalkReminderDetails(reminderScheduler)
        }
        if (reminderActionFailed) {
            Text(
                text = stringResource(R.string.route_walk_reminder_failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("route-walk-reminder-failure"),
            )
        }
    }
    val directionsUrl = plannedWalkDirectionsUrl(option)
    if (directionsUrl != null &&
        plannedWalkWarning(option, displayNowMillis, safetyBufferMinutes) != PlannedWalkWarning.PLANNED_DEPARTURE_PASSED
    ) {
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directionsUrl))
                directionsFailed = when {
                    directionsLauncher != null -> !directionsLauncher(intent)
                    else -> !runCatching { context.startActivity(intent); true }.getOrDefault(false)
                }
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("route-walk-directions"),
        ) { Text(stringResource(R.string.route_planned_walk_maps_action)) }
        Text(
            text = stringResource(R.string.route_planned_walk_maps_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (directionsFailed) {
            Text(
                text = stringResource(R.string.route_planned_walk_maps_failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("route-walk-directions-failure"),
            )
        }
    }
    if (startEpoch > displayNowMillis) {
        val calendarTitle = stringResource(
            R.string.route_planned_walk_calendar_title,
            option.origin.label,
            option.destination.label,
        )
        val calendarDescription = stringResource(R.string.route_planned_walk_start_disclaimer)
        OutlinedButton(
            onClick = {
                val draft = plannedWalkCalendarDraftIntent(
                    option = option,
                    nowMillis = System.currentTimeMillis(),
                    title = calendarTitle,
                    description = calendarDescription,
                    safetyBufferMinutes = safetyBufferMinutes,
                )
                launchFailed = when {
                    draft == null -> true
                    calendarLauncher != null -> !calendarLauncher(draft)
                    else -> !runCatching { context.startActivity(draft); true }
                        .getOrDefault(false)
                }
            },
            modifier = Modifier.heightIn(min = 48.dp)
                .testTag("route-planned-walk-calendar"),
        ) {
            Text(stringResource(R.string.route_planned_walk_calendar_action))
        }
        if (launchFailed) {
            Text(
                text = stringResource(R.string.route_planned_walk_calendar_failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("route-planned-walk-calendar-failure"),
            )
        }
    }
}
