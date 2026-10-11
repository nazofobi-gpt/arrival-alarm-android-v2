package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A stop-role picker for one resolved service-date-specific GTFS trip instance. */
@Composable
fun ConcreteTripInstancePanel(
    instance: ConcreteTripInstance,
    onConfirmStops: (GtfsScheduleStop, GtfsScheduleStop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = instance.sequence
    var originSequence by remember(trip.tripId, instance.serviceDate) {
        mutableStateOf(trip.boardingSequence)
    }
    var destinationSequence by remember(trip.tripId, instance.serviceDate) {
        mutableStateOf<Int?>(null)
    }
    val origin = trip.stops.firstOrNull { it.sequence == originSequence && it.boardingAllowed }
    val destination = trip.stops.firstOrNull {
        it.sequence == destinationSequence && it.alightingAllowed && it.sequence > originSequence
    }
    val formatter = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Europe/Berlin")
        }
    }

    Card(modifier = modifier.fillMaxWidth().testTag("concrete-trip-instance")) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.route_selected, trip.direction),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.source_format, instance.serviceDate.toString()),
                style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(300.dp).testTag("trip-ordered-stops"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(trip.stops, key = { it.sequence }) { call ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val planned = instance.scheduledTimes[call.sequence]
                        Text(
                            "${call.sequence}. ${call.stop.name} • " +
                                (planned?.let { formatter.format(Date(it)) }
                                    ?: stringResource(R.string.unknown_time)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = call.boardingAllowed,
                                onClick = {
                                    originSequence = call.sequence
                                    if ((destinationSequence ?: -1) <= originSequence) {
                                        destinationSequence = null
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .testTag("trip-origin-${call.sequence}"),
                            ) {
                                Text(
                                    if (originSequence == call.sequence)
                                        stringResource(R.string.selecting_origin)
                                    else stringResource(R.string.select_origin)
                                )
                            }
                            OutlinedButton(
                                enabled = call.alightingAllowed && call.sequence > originSequence,
                                onClick = { destinationSequence = call.sequence },
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .testTag("trip-destination-${call.sequence}"),
                            ) {
                                Text(
                                    if (destinationSequence == call.sequence)
                                        stringResource(R.string.selecting_destination)
                                    else stringResource(R.string.select_destination)
                                )
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.inference_disabled),
                style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = origin != null && destination != null,
                onClick = {
                    if (origin != null && destination != null) {
                        onConfirmStops(origin.stop, destination.stop)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("trip-confirm-stops"),
            ) {
                Text(stringResource(R.string.route_select_action))
            }
        }
    }
}
