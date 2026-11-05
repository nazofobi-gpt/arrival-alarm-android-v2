package com.nazofobi.arrivalalarm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Read-only scheduled evidence. No route state, notification or alarm side effect. */
@Composable
internal fun PlannedTransferWindowPanel(
    windows: List<RouteTransferWindowEvidence>,
    nowEpochMillis: Long? = null,
    onOpenWalkingDirections: ((String) -> Unit)? = null,
) {
    if (windows.isEmpty()) return
    val context = LocalContext.current
    val mapUnavailable = stringResource(R.string.route_planned_transfer_walk_unavailable)
    var clockEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Active-screen clock only: no background worker, alarm, GPS or notification.
    LaunchedEffect(nowEpochMillis) {
        if (nowEpochMillis == null) {
            while (true) {
                delay(30_000L)
                clockEpochMillis = System.currentTimeMillis()
            }
        }
    }
    val currentEpochMillis = nowEpochMillis ?: clockEpochMillis
    Column(
        modifier = Modifier.fillMaxWidth().testTag("journey-static-transfers"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        windows.forEachIndexed { index, evidence ->
            Text(
                text = stringResource(
                    R.string.route_planned_transfer_window,
                    index + 1,
                    evidence.arrivalStopName,
                    evidence.departureStopName,
                    evidence.plannedGapMinutes,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("journey-static-transfer-" + index),
            )
            evidence.plannedArrivalLocalTime?.takeIf { it.isNotBlank() }?.let { localTime ->
                Text(
                    text = stringResource(R.string.route_planned_transfer_arrival_local_time, localTime),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("journey-static-transfer-arrival-time-" + index),
                )
            }
            boundedGtfsPlatformCode(evidence.plannedArrivalPlatformCode)?.let { platform ->
                Text(
                    text = stringResource(R.string.route_planned_transfer_arrival_platform, platform),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("journey-static-transfer-arrival-platform-" + index),
                )
            }
            evidence.plannedDepartureLocalTime?.takeIf { it.isNotBlank() }?.let { localTime ->
                Text(
                    text = stringResource(R.string.route_planned_transfer_local_time, localTime),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("journey-static-transfer-local-time-" + index),
                )
            }
            boundedGtfsPlatformCode(evidence.plannedDeparturePlatformCode)?.let { platform ->
                Text(
                    text = stringResource(R.string.route_planned_transfer_departure_platform, platform),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("journey-static-transfer-departure-platform-" + index),
                )
            }
            val countdownMinutes = plannedTransferDepartureCountdownMinutes(
                evidence.plannedDepartureEpochMillis, currentEpochMillis,
            )
            if (countdownMinutes != null) {
                Text(
                    text = stringResource(
                        R.string.route_planned_transfer_departure_countdown, countdownMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("journey-static-transfer-countdown-" + index),
                )
            } else if (plannedTransferDepartureRecentlyPassed(
                    evidence.plannedDepartureEpochMillis, currentEpochMillis,
                )
            ) {
                Text(
                    text = stringResource(R.string.route_planned_transfer_departure_past),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("journey-static-transfer-departure-passed-" + index),
                )
            }
            plannedTransferStraightLineDistanceMeters(evidence)?.let { distanceMeters ->
                Text(
                    text = stringResource(
                        R.string.route_planned_transfer_straight_line_distance, distanceMeters,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("journey-transfer-straight-distance-" + index),
                )
            }
            if (plannedTransferStraightLineWalkingRisk(evidence)) {
                Text(
                    text = stringResource(R.string.route_planned_transfer_walk_risk),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("journey-transfer-walk-risk-" + index),
                )
            }
            if (plannedTransferShortConnectionRisk(evidence)) {
                Text(
                    text = stringResource(R.string.route_planned_transfer_short_window_risk),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("journey-transfer-short-window-risk-" + index),
                )
            }
            evidence.walkingCoordinates?.let(::plannedTransferWalkingDirectionsUrl)?.let { walkUrl ->
                TextButton(
                    onClick = {
                        if (onOpenWalkingDirections != null) {
                            onOpenWalkingDirections(walkUrl)
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(walkUrl)))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, mapUnavailable, Toast.LENGTH_LONG).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(context, mapUnavailable, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("journey-transfer-walk-map-" + index),
                ) {
                    Text(stringResource(R.string.route_planned_transfer_walk_map))
                }
                Text(
                    text = stringResource(R.string.route_planned_transfer_walk_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("journey-transfer-walk-privacy-" + index),
                )
            }
        }
        Text(
            text = stringResource(R.string.route_planned_transfer_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("journey-static-transfer-disclaimer"),
        )
    }
}
