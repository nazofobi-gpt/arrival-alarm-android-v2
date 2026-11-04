package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun GtfsLineDetailDialog(
    lineLabel: String,
    detail: GtfsLineDetail?,
    loading: Boolean,
    onOpenDepartures: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var selectedDirection by rememberSaveable(lineLabel, detail?.lineName, detail?.directions?.size) {
        mutableStateOf(0)
    }
    var stopFilter by rememberSaveable(lineLabel, detail?.lineName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("gtfs-line-detail-dialog"),
        title = {
            Text(stringResource(R.string.gtfs_line_detail_title, detail?.lineName ?: lineLabel))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.gtfs_line_static_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    loading -> Text(stringResource(R.string.gtfs_line_loading))
                    detail == null || detail.directions.isEmpty() ->
                        Text(stringResource(R.string.gtfs_line_unavailable))
                    else -> {
                        // Keep the direction controls visible without rendering up to
                        // eight long stop lists at once. Switching never changes a journey.
                        val activeIndex = selectedDirection.takeIf {
                            it in detail.directions.indices
                        } ?: 0
                        detail.directions.forEachIndexed { index, direction ->
                            FilterChip(
                                selected = activeIndex == index,
                                onClick = { selectedDirection = index },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.gtfs_line_direction,
                                            index + 1,
                                            direction.headsign.ifBlank {
                                                stringResource(R.string.gtfs_line_unknown_direction)
                                            },
                                        ),
                                    )
                                },
                                modifier = Modifier.testTag("gtfs-line-select-direction-$index"),
                            )
                        }
                        val activeDirection = detail.directions[activeIndex]
                        OutlinedTextField(
                            value = stopFilter,
                            onValueChange = { stopFilter = it.take(80) },
                            label = { Text(stringResource(R.string.gtfs_line_search_stops)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("gtfs-line-stop-filter"),
                        )
                        val needle = stopFilter.trim()
                        val matchingStopIndexes = activeDirection.stopNames.indices.filter { index ->
                            needle.isEmpty() || activeDirection.stopNames[index].contains(needle, ignoreCase = true)
                        }
                        val matchingStops = matchingStopIndexes.map { index ->
                            "${index + 1}. ${activeDirection.stopNames[index]}"
                        }
                        if (matchingStops.isEmpty()) {
                            Text(
                                stringResource(R.string.gtfs_line_no_matching_stops),
                                modifier = Modifier.testTag("gtfs-line-no-matches"),
                            )
                        } else {
                            Text(
                                matchingStops.joinToString("\n"),
                                modifier = Modifier.testTag("gtfs-line-stops-$activeIndex"),
                            )
                        }
                        // Opening a station board is an explicit browse action, never an alarm.
                        // Require an unambiguous, source-backed stop ID from the selected direction.
                        val singleMatch = matchingStopIndexes.singleOrNull()
                            ?.takeIf { needle.isNotEmpty() }
                            ?.let { activeDirection.stops.getOrNull(it) }
                            ?.takeIf { it.id.isNotBlank() }
                        if (singleMatch != null) {
                            TextButton(
                                onClick = { onOpenDepartures(singleMatch.id) },
                                modifier = Modifier.testTag("gtfs-line-open-departures"),
                            ) {
                                Text(stringResource(R.string.gtfs_line_open_stop_departures, singleMatch.name))
                            }
                        }
                        if (activeDirection.stopsTruncated) {
                            Text(stringResource(R.string.gtfs_line_stops_limited))
                        }
                        if (detail.additionalDirectionsOmitted) {
                            Text(stringResource(R.string.gtfs_line_directions_limited))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("gtfs-line-close")) {
                Text(stringResource(R.string.gtfs_line_close))
            }
        },
    )
}
