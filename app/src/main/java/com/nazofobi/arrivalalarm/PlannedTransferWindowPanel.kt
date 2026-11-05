package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Read-only scheduled evidence. No route state, notification or alarm side effect. */
@Composable
internal fun PlannedTransferWindowPanel(windows: List<RouteTransferWindowEvidence>) {
    if (windows.isEmpty()) return
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
        }
        Text(
            text = stringResource(R.string.route_planned_transfer_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("journey-static-transfer-disclaimer"),
        )
    }
}
