package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Read-only scheduled evidence. No route state, notification or alarm side effect. */
@Composable
internal fun PlannedTransferWindowPanel(
    windows: List<RouteTransferWindowEvidence>,
    nowEpochMillis: Long? = null,
) {
    if (windows.isEmpty()) return
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
            evidence.plannedDepartureLocalTime?.takeIf { it.isNotBlank() }?.let { localTime ->
                Text(
                    text = stringResource(R.string.route_planned_transfer_local_time, localTime),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("journey-static-transfer-local-time-" + index),
                )
            }
            plannedTransferDepartureCountdownMinutes(
                evidence.plannedDepartureEpochMillis, currentEpochMillis,
            )?.let { minutes ->
                Text(
                    text = stringResource(R.string.route_planned_transfer_departure_countdown, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("journey-static-transfer-countdown-" + index),
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
