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
    completionRevision: Long = 0L,
) {
    val boarding = preview.boarding
    val detail = preview.detail
    key(detail.identity, boarding.stopSequence, boarding.stopId) {
        val boardingActionable = boarding.hasActionableBoardingClock()
        var selectionState by remember { mutableStateOf(TripStopSelectionReducer.initial(detail, boarding)) }
        LaunchedEffect(detail, boarding) {
            selectionState = TripStopSelectionReducer.reduce(
                selectionState,
                TripStopSelectionAction.ReplaceTrip(detail, boarding),
            )
        }
        val activeSelectionState = if (
            selectionState.detail == detail &&
            selectionState.boarding == GtfsSelectedStopRef(boarding.stopSequence, boarding.stopId)
        ) {
            selectionState
        } else {
            TripStopSelectionReducer.initial(detail, boarding)
        }
        var destinationExpanded by remember { mutableStateOf(false) }
        var alarmExpanded by remember { mutableStateOf(false) }
        // A rapid double tap must not issue two saves before the caller sets busy.
        // Once the parent's busy cycle ends, failed saves can be retried.
        var confirmationPending by remember { mutableStateOf(false) }
        var busySeenAfterConfirmation by remember { mutableStateOf(false) }
        var startedAtCompletionRevision by remember { mutableStateOf(completionRevision) }
        // Fast IO can finish before Compose observes busy=true. A parent completion
        // revision releases the latch without permitting a second in-flight save.
        LaunchedEffect(busy, confirmationPending, completionRevision) {
            if (confirmationPending) {
                if (busy) {
                    busySeenAfterConfirmation = true
                } else if (busySeenAfterConfirmation ||
                    completionRevision != startedAtCompletionRevision
                ) {
                    confirmationPending = false
                    busySeenAfterConfirmation = false
                }
            }
        }

        // Untimed non-timepoint stops remain visible in the trip, but cannot
        // be selected as an actionable destination or arrival-alarm target.
        val destinations = detail.stops.filter {
            it.stopSequence > boarding.stopSequence &&
                (it.dropOffType == null || it.dropOffType == 0) &&
                it.hasActionableArrivalClock()
        }
        val destination = TripStopSelectionReducer.destinationStop(activeSelectionState)
            ?.takeIf { selected ->
                destinations.any {
                    it.stopSequence == selected.stopSequence && it.stopId == selected.stopId
                }
            }
        // The boarding event is not a valid arrival-alarm target.
        val alarmStops = if (destination != null) detail.stops.filter {
            it.stopSequence > boarding.stopSequence &&
                it.stopSequence <= destination.stopSequence &&
                it.hasActionableArrivalClock()
        } else emptyList()
        val alarm = TripStopSelectionReducer.alarmTargetStop(activeSelectionState)
            ?.takeIf { selected -> alarmStops.any { it.stopSequence == selected.stopSequence && it.stopId == selected.stopId } }
            ?: destination

        // Preview the same source-backed stop-role contract enforced at persistence.
        // An impossible service-day clock must disable consent before the rider taps Save,
        // rather than offer a button that can only reject after the tap.
        // Keep invalid destination choices visible so the feed defect is explicit.
        val consentDraft = TripStopSelectionReducer.draft(activeSelectionState)
        val consentReady = destination != null && boardingActionable &&
            consentDraft is GtfsTripConsentDraft.Ready
        val invalidRoles = activeSelectionState.roleProblem != null ||
            (destination != null && consentDraft is GtfsTripConsentDraft.Rejected)

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
                                    selectionState = TripStopSelectionReducer.reduce(
                                        activeSelectionState,
                                        TripStopSelectionAction.SelectDestination(stop.stopSequence),
                                    )
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
                                        selectionState = TripStopSelectionReducer.reduce(
                                            activeSelectionState,
                                            TripStopSelectionAction.SelectAlarmTarget(stop.stopSequence),
                                        )
                                        alarmExpanded = false
                                    },
                                    modifier = Modifier.testTag("gtfs-alarm-${stop.stopSequence}"),
                                )
                            }
                        }
                    }
                }

                if (invalidRoles || destinations.isEmpty() || !boardingActionable ||
                    (destination != null && !consentReady)
                ) {
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
                        when (val draft = consentDraft) {
                            is GtfsTripConsentDraft.Ready -> {
                                if (!confirmationPending) {
                                    startedAtCompletionRevision = completionRevision
                                    confirmationPending = true
                                    onConfirm(draft.selection)
                                }
                            }
                            is GtfsTripConsentDraft.Rejected -> Unit
                        }
                    },
                    enabled = consentReady && !busy && !confirmationPending,
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
