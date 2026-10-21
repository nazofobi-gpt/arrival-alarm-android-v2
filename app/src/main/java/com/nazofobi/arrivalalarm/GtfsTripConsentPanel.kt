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
import androidx.compose.runtime.LaunchedEffect
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
    key(detail.identity, boarding.stopSequence, boarding.stopId, detail.stops) {
        val boardingActionable = boarding.hasActionableBoardingClock()
        var destinationSequence by remember { mutableStateOf<Int?>(null) }
        var alarmOverride by remember { mutableStateOf<Int?>(null) }
        var destinationExpanded by remember { mutableStateOf(false) }
        var alarmExpanded by remember { mutableStateOf(false) }
        var invalidRoles by remember { mutableStateOf(false) }
        // A rapid double tap must not issue two saves before the caller sets busy.
        // Once the parent's busy cycle ends, failed saves can be retried.
        var confirmationPending by remember { mutableStateOf(false) }
        var busySeenAfterConfirmation by remember { mutableStateOf(false) }
        LaunchedEffect(busy, confirmationPending) {
            if (busy && confirmationPending) {
                busySeenAfterConfirmation = true
            } else if (!busy && busySeenAfterConfirmation) {
                confirmationPending = false
                busySeenAfterConfirmation = false
            }
        }

        // Untimed non-timepoint stops remain visible in the trip, but cannot
        // be selected as an actionable destination or arrival-alarm target.
        val destinations = detail.stops.filter {
            it.stopSequence > boarding.stopSequence &&
                (it.dropOffType == null || it.dropOffType == 0) &&
                it.hasActionableArrivalClock()
        }
        val destination = destinations.singleOrNull { it.stopSequence == destinationSequence }
        // The boarding event is not a valid arrival-alarm target.
        val alarmStops = if (destination != null) detail.stops.filter {
            it.stopSequence > boarding.stopSequence &&
                it.stopSequence <= destination.stopSequence &&
                it.hasActionableArrivalClock()
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
                        enabled = !busy && !confirmationPending,
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
                                enabled = !busy && !confirmationPending,
                                onClick = {
                                    destinationSequence = stop.stopSequence
                                    alarmOverride = null
                                    invalidRoles = false
                                    destinationExpanded = false
                                },
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
                            enabled = !busy && !confirmationPending,
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
                                    enabled = !busy && !confirmationPending,
                                    onClick = {
                                        alarmOverride = stop.stopSequence
                                        invalidRoles = false
                                        alarmExpanded = false
                                    },
                                    modifier = Modifier.testTag("gtfs-alarm-${stop.stopSequence}"),
                                )
                            }
                        }
                    }
                }

                if (invalidRoles || destinations.isEmpty() || !boardingActionable) {
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
                            is GtfsTripConsentDraft.Ready -> {
                                if (!confirmationPending) {
                                    confirmationPending = true
                                    onConfirm(draft.selection)
                                }
                            }
                            is GtfsTripConsentDraft.Rejected -> invalidRoles = true
                        }
                    },
                    enabled = destination != null && boardingActionable && !busy && !confirmationPending,
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


/**
 * Show the exact rider-confirmed, source-verified GTFS stop roles after save or
 * process recreation. Display is read-only: it never starts tracking or arms an alarm.
 * Reject inconsistent detail rather than inventing a current trip or stop.
 */
@Composable
internal fun GtfsConfirmedTripSummary(
    verified: GtfsSelectionRestore.Verified,
    modifier: Modifier = Modifier,
) {
    val selection = verified.selection
    val detail = verified.detail
    if (detail.identity != selection.identity) return

    fun matching(ref: GtfsSelectedStopRef): GtfsTripDetailStop? =
        detail.stops.singleOrNull {
            it.stopSequence == ref.sequence && it.stopId == ref.stopId
        }
    val boarding = matching(selection.boarding) ?: return
    val destination = matching(selection.destination) ?: return
    val alarmTarget = matching(selection.alarmTarget) ?: return
    if (boarding.stopSequence >= destination.stopSequence ||
        alarmTarget.stopSequence <= boarding.stopSequence ||
        alarmTarget.stopSequence > destination.stopSequence
    ) return
    val plannedStopCount = plannedPhysicalStopsFromBoardingToAlarm(selection, detail) ?: return

    Card(modifier = modifier.fillMaxWidth().testTag("gtfs-confirmed-trip-summary")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.gtfs_consent_restored),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.gtfs_consent_boarding, boarding.name),
                modifier = Modifier.testTag("gtfs-confirmed-boarding"),
            )
            Text(
                "${stringResource(R.string.gtfs_consent_choose_destination)}: ${destination.name}",
                modifier = Modifier.testTag("gtfs-confirmed-destination"),
            )
            Text(
                "${stringResource(R.string.gtfs_consent_alarm_target)}: ${alarmTarget.name}",
                modifier = Modifier.testTag("gtfs-confirmed-alarm-target"),
            )
            Text(
                stringResource(R.string.gtfs_confirmed_planned_stops_until_alarm, plannedStopCount),
                modifier = Modifier.testTag("gtfs-confirmed-planned-stop-count"),
            )
            Text(
                stringResource(R.string.gtfs_consent_no_automatic_alarm),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
