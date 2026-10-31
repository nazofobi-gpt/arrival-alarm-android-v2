package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun GtfsTripSearchConsentPanel(
    template: GtfsTripSearchIdentityTemplate,
    selection: GtfsTripSearchSelection?,
    inspection: GtfsTripDetailResult?,
    confirmed: GtfsSelectionRestore.Verified?,
    busy: Boolean,
    statusMessage: String?,
    completionRevision: Long,
    onChooseServiceDate: () -> Unit,
    onChangeServiceDate: () -> Unit,
    onConfirm: (GtfsSelectedTrip) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardingSequence by remember(selection?.identity) { mutableStateOf<Int?>(null) }
    var destinationSequence by remember(selection?.identity) { mutableStateOf<Int?>(null) }
    var alarmSequence by remember(selection?.identity) { mutableStateOf<Int?>(null) }
    var confirmPending by remember(selection?.identity) { mutableStateOf(false) }

    LaunchedEffect(completionRevision, busy) {
        if (!busy) confirmPending = false
    }

    val detail = (inspection as? GtfsTripDetailResult.Available)?.detail
    val identityMatches = selection != null && detail?.identity == selection.identity
    val validCoordinate: (GtfsTripDetailStop) -> Boolean = { stop ->
        stop.latitude.isFinite() && stop.longitude.isFinite() &&
            stop.latitude in -90.0..90.0 && stop.longitude in -180.0..180.0
    }
    val boardingOptions = detail?.stops.orEmpty().filter { stop ->
        (stop.pickupType == null || stop.pickupType == 0) &&
            !stop.scheduledDeparture.isNullOrBlank() && validCoordinate(stop)
    }
    val selectedBoarding = detail?.stops?.firstOrNull { it.stopSequence == boardingSequence }
    val destinationOptions = detail?.stops.orEmpty().filter { stop ->
        selectedBoarding != null &&
            stop.stopSequence > selectedBoarding.stopSequence &&
            (stop.dropOffType == null || stop.dropOffType == 0) &&
            !stop.scheduledArrival.isNullOrBlank() && validCoordinate(stop)
    }
    val selectedDestination = detail?.stops?.firstOrNull {
        it.stopSequence == destinationSequence
    }
    val alarmOptions = detail?.stops.orEmpty().filter { stop ->
        selectedBoarding != null && selectedDestination != null &&
            stop.stopSequence > selectedBoarding.stopSequence &&
            stop.stopSequence <= selectedDestination.stopSequence &&
            (stop.dropOffType == null || stop.dropOffType == 0) &&
            !stop.scheduledArrival.isNullOrBlank() && validCoordinate(stop)
    }
    val effectiveAlarmSequence = alarmSequence ?: destinationSequence
    val draft = if (detail != null && identityMatches &&
        boardingSequence != null && destinationSequence != null
    ) {
        buildGtfsTripConsentDraft(
            detail = detail,
            boardingSequence = boardingSequence!!,
            destinationSequence = destinationSequence!!,
            alarmTargetSequence = effectiveAlarmSequence,
        )
    } else {
        null
    }
    val readyDraft = draft as? GtfsTripConsentDraft.Ready

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("gtfs-trip-search-panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.gtfs_trip_search_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.gtfs_trip_search_back))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(template.displayLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.gtfs_trip_search_source_version,
                        template.sourceVersion,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selection == null) {
                    Text(stringResource(R.string.gtfs_trip_search_date_required))
                    Button(
                        onClick = onChooseServiceDate,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("gtfs-trip-search-choose-date"),
                    ) {
                        Text(stringResource(R.string.gtfs_trip_search_choose_date))
                    }
                } else {
                    Text(
                        stringResource(
                            R.string.gtfs_trip_search_service_date,
                            formatServiceDate(selection.identity.serviceDate),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onChangeServiceDate,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("gtfs-trip-search-change-date"),
                    ) {
                        Text(stringResource(R.string.gtfs_trip_search_change_date))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.gtfs_trip_search_no_alarm_start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.gtfs_trip_search_loading))
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                color = if (inspection is GtfsTripDetailResult.Unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.testTag("gtfs-trip-search-status"),
            )
        }

        if (confirmed != null) {
            GtfsConfirmedTripSummary(confirmed)
            Button(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("gtfs-trip-search-finished"),
            ) {
                Text(stringResource(R.string.gtfs_trip_search_back))
            }
        } else if (selection != null && inspection is GtfsTripDetailResult.Unavailable) {
            Text(
                stringResource(R.string.gtfs_trip_search_unavailable),
                color = MaterialTheme.colorScheme.error,
            )
        } else if (selection != null && detail != null && !identityMatches) {
            Text(
                stringResource(R.string.gtfs_trip_search_identity_mismatch),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("gtfs-trip-search-identity-mismatch"),
            )
        } else if (selection != null && detail != null && identityMatches) {
            Text(
                detail.headsign?.takeIf { it.isNotBlank() } ?: template.displayLabel,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.gtfs_trip_search_ordered_stops),
                style = MaterialTheme.typography.titleSmall,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("gtfs-trip-search-ordered-stops"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                detail.stops.forEach { stop ->
                    val roles = buildList {
                        if (stop.stopSequence == boardingSequence) add(
                            stringResource(R.string.gtfs_trip_search_role_boarding),
                        )
                        if (stop.stopSequence == destinationSequence) add(
                            stringResource(R.string.gtfs_trip_search_role_destination),
                        )
                        if (stop.stopSequence == effectiveAlarmSequence) add(
                            stringResource(R.string.gtfs_trip_search_role_alarm),
                        )
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gtfs-trip-search-stop-${stop.stopSequence}"),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.gtfs_trip_search_stop_sequence,
                                    stop.stopSequence,
                                ) + " • " + stop.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val time = listOfNotNull(
                                stop.scheduledDeparture?.takeIf(String::isNotBlank)?.let {
                                    stringResource(R.string.gtfs_trip_search_departure_time, it)
                                },
                                stop.scheduledArrival?.takeIf(String::isNotBlank)?.let {
                                    stringResource(R.string.gtfs_trip_search_arrival_time, it)
                                },
                            ).joinToString(" • ")
                            if (time.isNotBlank()) {
                                Text(time, style = MaterialTheme.typography.bodySmall)
                            }
                            if (roles.isNotEmpty()) {
                                Text(
                                    roles.joinToString(" • "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (boardingOptions.isEmpty()) {
                Text(
                    stringResource(R.string.gtfs_trip_search_no_boarding_stops),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TripSearchStopPicker(
                label = stringResource(R.string.gtfs_trip_search_boarding),
                chooseLabel = stringResource(R.string.gtfs_trip_search_choose_boarding),
                selected = selectedBoarding,
                options = boardingOptions,
                enabled = !busy && confirmed == null,
                tag = "gtfs-trip-search-boarding-picker",
                onSelect = { sequence ->
                    boardingSequence = sequence
                    destinationSequence = null
                    alarmSequence = null
                },
            )
            TripSearchStopPicker(
                label = stringResource(R.string.gtfs_trip_search_destination),
                chooseLabel = stringResource(R.string.gtfs_trip_search_choose_destination),
                selected = selectedDestination,
                options = destinationOptions,
                enabled = !busy && selectedBoarding != null,
                tag = "gtfs-trip-search-destination-picker",
                onSelect = { sequence ->
                    destinationSequence = sequence
                    alarmSequence = null
                },
            )
            val selectedAlarm = detail.stops.firstOrNull {
                it.stopSequence == effectiveAlarmSequence
            }
            TripSearchStopPicker(
                label = stringResource(R.string.gtfs_trip_search_alarm_target),
                chooseLabel = stringResource(R.string.gtfs_trip_search_choose_alarm_target),
                selected = selectedAlarm,
                options = alarmOptions,
                enabled = !busy && selectedDestination != null,
                tag = "gtfs-trip-search-alarm-picker",
                onSelect = { alarmSequence = it },
            )

            if (boardingSequence != null && destinationSequence != null &&
                readyDraft == null
            ) {
                Text(
                    stringResource(R.string.gtfs_trip_search_invalid_roles),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("gtfs-trip-search-invalid-roles"),
                )
            }
            Button(
                onClick = {
                    val selected = readyDraft?.selection ?: return@Button
                    confirmPending = true
                    onConfirm(selected)
                },
                enabled = readyDraft != null && !busy && !confirmPending && identityMatches,
                modifier = Modifier.fillMaxWidth().testTag("gtfs-trip-search-confirm"),
            ) {
                Text(stringResource(R.string.gtfs_trip_search_confirm))
            }
        }
    }
}

@Composable
private fun TripSearchStopPicker(
    label: String,
    chooseLabel: String,
    selected: GtfsTripDetailStop?,
    options: List<GtfsTripDetailStop>,
    enabled: Boolean,
    tag: String,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember(tag, selected?.stopSequence) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled && options.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag(tag),
            ) {
                Text(selected?.let(::tripSearchStopLabel) ?: chooseLabel)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { stop ->
                    DropdownMenuItem(
                        text = { Text(tripSearchStopLabel(stop)) },
                        onClick = {
                            onSelect(stop.stopSequence)
                            expanded = false
                        },
                        modifier = Modifier.testTag("$tag-option-${stop.stopSequence}"),
                    )
                }
            }
        }
    }
}

private fun tripSearchStopLabel(stop: GtfsTripDetailStop): String {
    val clock = stop.scheduledDeparture ?: stop.scheduledArrival
    return if (clock.isNullOrBlank()) {
        "${stop.name} (#${stop.stopSequence})"
    } else {
        "${stop.name} (#${stop.stopSequence}) • $clock"
    }
}

private fun formatServiceDate(serviceDate: GtfsServiceDate): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault()).apply {
        clear()
        set(serviceDate.year, serviceDate.month - 1, serviceDate.day)
    }
    return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(calendar.time)
}
