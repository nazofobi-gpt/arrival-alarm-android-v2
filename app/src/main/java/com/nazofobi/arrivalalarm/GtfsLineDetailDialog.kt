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
import androidx.compose.runtime.mutableIntStateOf
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
    // Keep page state outside the conditional dialog contents: a recomposition
    // after clicking Next must not reconstruct page 0 during content replacement.
    // Reset when the inspected source changes, never when paging the same route.
    var intermediatePage by rememberSaveable(lineLabel, detail?.lineName, detail?.sourceVersion) {
        mutableIntStateOf(0)
    }
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
                                onClick = {
                                    selectedDirection = index
                                    intermediatePage = 0
                                },
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
                        // Identically named platforms and repeated stops must remain selectable
                        // by source identity. Browsing departures never arms a journey or alarm.
                        // Bound rendered choices so a broad query cannot expand the dialog without limit.
                        // Small direction lists are immediately actionable. Long lists still
                        // require an explicit filter to bound the number of choices.
                        val browseLongLine = needle.isEmpty() &&
                            activeDirection.stops.size > MAX_LINE_BOARD_CHOICES
                        val intermediateStops = if (browseLongLine) {
                            (1 until activeDirection.stops.lastIndex).mapNotNull { index ->
                                activeDirection.stops[index].takeIf { it.id.isNotBlank() }
                                    ?.let { index to it }
                            }.distinctBy { (_, stop) -> stop.id }
                        } else emptyList()
                        val pageCount = if (browseLongLine) {
                            (intermediateStops.size + MAX_LINE_INTERMEDIATE_CHOICES - 1) /
                                MAX_LINE_INTERMEDIATE_CHOICES
                        } else 0
                        val safePage = intermediatePage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                        val matchingBoardStops = if (browseLongLine) {
                            // Keep both terminals directly accessible on every page, including
                            // circular routes where they share a single source stop ID.
                            val terminals = listOf(0, activeDirection.stops.lastIndex).distinct()
                                .mapNotNull { index ->
                                    activeDirection.stops[index].takeIf { it.id.isNotBlank() }
                                        ?.let { index to it }
                                }
                            (terminals + intermediateStops.drop(
                                safePage * MAX_LINE_INTERMEDIATE_CHOICES
                            ).take(MAX_LINE_INTERMEDIATE_CHOICES))
                                .sortedBy { (index, _) -> index }
                                .distinctBy { (_, stop) -> stop.id }
                        } else {
                            matchingStopIndexes.mapNotNull { index ->
                                activeDirection.stops.getOrNull(index)
                                    ?.takeIf { it.id.isNotBlank() }
                                    ?.let { index to it }
                            }.distinctBy { (_, stop) -> stop.id }
                        }
                        matchingBoardStops.take(MAX_LINE_BOARD_CHOICES).forEach { (index, stop) ->
                            val unique = matchingBoardStops.size == 1
                            TextButton(
                                onClick = { onOpenDepartures(stop.id) },
                                modifier = Modifier.testTag(
                                    if (unique) "gtfs-line-open-departures"
                                    else "gtfs-line-open-departures-$index"
                                ),
                            ) {
                                val label = if (unique) stop.name else "${index + 1}. ${stop.name}"
                                Text(stringResource(R.string.gtfs_line_open_stop_departures, label))
                            }
                        }
                        if (browseLongLine && pageCount > 1) {
                            if (safePage > 0) {
                                TextButton(
                                    onClick = { intermediatePage = safePage - 1 },
                                    modifier = Modifier.testTag("gtfs-line-previous-intermediate"),
                                ) {
                                    Text(stringResource(R.string.gtfs_line_previous_intermediate))
                                }
                            }
                            if (safePage + 1 < pageCount) {
                                TextButton(
                                    onClick = { intermediatePage = safePage + 1 },
                                    modifier = Modifier.testTag("gtfs-line-next-intermediate"),
                                ) {
                                    Text(stringResource(R.string.gtfs_line_next_intermediate))
                                }
                            }
                        }
                        if (matchingBoardStops.size > MAX_LINE_BOARD_CHOICES) {
                            Text(stringResource(R.string.gtfs_line_refine_stop_search))
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

private const val MAX_LINE_BOARD_CHOICES = 8
private const val MAX_LINE_INTERMEDIATE_CHOICES = MAX_LINE_BOARD_CHOICES - 2
