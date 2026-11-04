package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Read-only stop sequence for an already source-validated GTFS trip.
 * A timetable is not realtime evidence. Never infer a platform change,
 * current vehicle position, journey consent or an armed alarm from this view.
 */
@Composable
internal fun GtfsTripStopSequencePanel(
    detail: GtfsTripDetail,
    boarding: GtfsTripDetailStop?,
    modifier: Modifier = Modifier,
) {
    if (detail.stops.isEmpty()) return
    // A replaced feed snapshot must not silently keep a previously opened list.
    var expanded by remember(detail.identity, detail.stops) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("g240-trip-stop-toggle"),
        ) {
            Text(stringResource(
                if (expanded) R.string.g240_trip_stops_hide else R.string.g240_trip_stops_show,
                detail.stops.size,
            ))
        }
        if (expanded) {
            Text(
                stringResource(R.string.g240_trip_stops_scheduled_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("g240-trip-stop-source"),
            )
            detail.stops.forEach { stop ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .testTag("g240-trip-stop-row-${stop.stopSequence}"),
                ) {
                    Text(
                        "${stop.name} (#${stop.stopSequence})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("g240-trip-stop-name-${stop.stopSequence}"),
                    )
                    val arrival = stop.scheduledArrival?.takeIf { it.isNotBlank() }?.let {
                        stringResource(R.string.g240_trip_stop_arrival, it)
                    }
                    val departure = stop.scheduledDeparture?.takeIf { it.isNotBlank() }?.let {
                        stringResource(R.string.g240_trip_stop_departure, it)
                    }
                    val platform = stop.platformCode?.takeIf { it.isNotBlank() }?.let {
                        stringResource(R.string.g240_trip_stop_platform, it)
                    }
                    val planned = listOfNotNull(arrival, departure, platform)
                    if (planned.isNotEmpty()) {
                        Text(
                            planned.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("g240-trip-stop-schedule-${stop.stopSequence}"),
                        )
                    }
                    if (boarding?.stopSequence == stop.stopSequence && boarding.stopId == stop.stopId) {
                        Text(
                            stringResource(R.string.g240_trip_stop_boarding),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.testTag("g240-trip-stop-boarding"),
                        )
                    }
                }
            }
        }
    }
}
