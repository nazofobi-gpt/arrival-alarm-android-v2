package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * A departure-board preview is not permission to save a trip or start tracking.
 * Only the explicit confirmation button emits a typed, source-validated selection.
 * The caller still has to persist that selection; this component never arms an alarm.
 */
@Composable
internal fun GtfsTripConsentPanel(
    preview: GtfsDepartureInspection.Ready,
    busy: Boolean,
    onConfirm: (GtfsSelectedTrip) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boarding = preview.boarding
    val detail = preview.detail
    key(detail.identity, boarding.stopSequence, boarding.stopId) {
        var destinationSequence by remember { mutableStateOf<Int?>(null) }
        var alarmOverride by remember { mutableStateOf<Int?>(null) }
        var destinationExpanded by remember { mutableStateOf(false) }
        var alarmExpanded by remember { mutableStateOf(false) }
        var invalidRoles by remember { mutableStateOf(false) }

        val destinations = detail.stops.filter {
            it.stopSequence > boarding.stopSequence &&
                (it.dropOffType == null || it.dropOffType == 0)
        }
        val destination = destinations.singleOrNull { it.stopSequence == destinationSequence }
        // The boarding event is not a valid arrival-alarm target.
        val alarmStops = if (destination != null) detail.stops.filter {
            it.stopSequence > boarding.stopSequence &&
                it.stopSequence <= destination.stopSequence
        } else emptyList()
        val alarm = alarmStops.singleOrNull { it.stopSequence == alarmOverride }
            ?: destination

        Card(modifier = modifier.fillMaxWidth().testTag("gtfs-consent-panel")) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.gtfs_consent_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.gtfs_consent_boarding, boarding.name),
                    modifier = Modifier.testTag("gtfs-consent-boarding"),
                )
                Text(stringResource(R.string.gtfs_consent_choose_destination))

                Column {
                    OutlinedButton(
                        onClick = { destinationExpanded = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("gtfs-destination-picker"),
                    ) {
                        Text(destination?.name ?: stringResource(R.string.gtfs_consent_choose_destination))
                    }
                    DropdownMenu(
                        expanded = destinationExpanded,
                        onDismissRequest = { destinationExpanded = false },
                    ) {
                        destinations.forEach { stop ->
                            DropdownMenuItem(
                                text = { Text("${stop.name} (#${stop.stopSequence})") },
                                onClick = {
                                    destinationSequence = stop.stopSequence
                                    alarmOverride = null
                                    invalidRoles = false
                                    destinationExpanded = false
                                },
                                enabled = !busy,
                                modifier = Modifier.testTag("gtfs-destination-${stop.stopSequence}"),
                            )
                        }
                    }
                }

                if (destination != null) {
                    Text(stringResource(R.string.gtfs_consent_alarm_target))
                    Column {
                        OutlinedButton(
                            onClick = { alarmExpanded = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("gtfs-alarm-target-picker"),
                        ) {
                            Text(alarm?.name ?: destination.name)
                        }
                        DropdownMenu(
                            expanded = alarmExpanded,
                            onDismissRequest = { alarmExpanded = false },
                        ) {
                            alarmStops.forEach { stop ->
                                DropdownMenuItem(
                                    text = { Text("${stop.name} (#${stop.stopSequence})") },
                                    onClick = {
                                        alarmOverride = stop.stopSequence
                                        invalidRoles = false
                                        alarmExpanded = false
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.testTag("gtfs-alarm-${stop.stopSequence}"),
                                )
                            }
                        }
                    }
                }

                if (invalidRoles || destinations.isEmpty()) {
                    Text(
                        stringResource(R.string.gtfs_consent_invalid),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("gtfs-consent-invalid"),
                    )
                }
                Text(
                    stringResource(R.string.gtfs_consent_no_automatic_alarm),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        val selected = destination ?: return@Button
                        when (val draft = buildGtfsTripConsentDraft(
                            detail = detail,
                            boardingSequence = boarding.stopSequence,
                            destinationSequence = selected.stopSequence,
                            alarmTargetSequence = alarm?.stopSequence,
                        )) {
                            is GtfsTripConsentDraft.Ready -> onConfirm(draft.selection)
                            is GtfsTripConsentDraft.Rejected -> invalidRoles = true
                        }
                    },
                    enabled = destination != null && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("gtfs-consent-confirm"),
                ) {
                    Text(stringResource(R.string.gtfs_consent_save))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("gtfs-consent-dismiss"),
                ) {
                    Text(stringResource(R.string.gtfs_consent_cancel))
                }
            }
        }
    }
}
