package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeSearchPanel(
    selectingOrigin: Boolean,
    query: String,
    searchBusy: Boolean,
    searchResults: List<TransitLocationResult>,
    searchSource: String?,
    searchMessage: String?,
    locationMessage: String?,
    origin: MapPoint?,
    destination: MapPoint?,
    mapMessage: String?,
    nearby: List<NearbyStop>,
    nearbySource: String?,
    recentSearches: List<String>,
    favoriteStopIds: Set<String>,
    onSelectOriginTarget: () -> Unit,
    onSelectDestinationTarget: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchResultSelected: (TransitLocationResult) -> Unit,
    onRecentSearchSelected: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onMapStopSelected: (CatalogStop) -> Unit,
    onMapPointSelected: (MapPoint) -> Unit,
    onMapError: (String) -> Unit,
    onNearbyStopSelected: (NearbyStop) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.search_stops_label),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = if (selectingOrigin) {
                stringResource(R.string.search_target_origin_status)
            } else {
                stringResource(R.string.search_target_destination_status)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("search-target-status"),
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.journey_points_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                FilledTonalButton(
                    onClick = onSelectOriginTarget,
                    enabled = !selectingOrigin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search-target-origin"),
                ) {
                    Text(
                        if (selectingOrigin) {
                            stringResource(R.string.selecting_origin)
                        } else {
                            stringResource(R.string.select_origin)
                        }
                    )
                }

                FilledTonalButton(
                    onClick = onSelectDestinationTarget,
                    enabled = origin != null && selectingOrigin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search-target-destination"),
                ) {
                    Text(
                        if (!selectingOrigin) {
                            stringResource(R.string.selecting_destination)
                        } else {
                            stringResource(R.string.select_destination)
                        }
                    )
                }

                HorizontalDivider()

                if (origin == null && destination == null) {
                    Text(
                        text = stringResource(R.string.journey_points_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    origin?.let {
                        Text(
                            text = stringResource(R.string.origin_format, it.label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("origin-label"),
                        )
                    }
                    destination?.let {
                        Text(
                            text = stringResource(R.string.destination_format, it.label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("destination-label"),
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_stops_label)) },
                    supportingText = {
                        Text(stringResource(R.string.search_supporting_text))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog-search"),
                    singleLine = true,
                )

                Button(
                    onClick = onSearch,
                    enabled = !searchBusy && query.trim().length >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog-search-submit"),
                ) {
                    Text(
                        if (searchBusy) {
                            stringResource(R.string.searching)
                        } else {
                            stringResource(R.string.search_action)
                        }
                    )
                }

                searchSource?.let {
                    Text(
                        text = stringResource(R.string.source_format, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("search-source"),
                    )
                }
                searchMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("search-message"),
                    )
                }

                searchResults.take(12).forEachIndexed { index, result ->
                    OutlinedButton(
                        onClick = { onSearchResultSelected(result) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search-result-$index"),
                    ) {
                        val action = if (selectingOrigin) {
                            stringResource(R.string.select_origin)
                        } else {
                            stringResource(R.string.select_destination)
                        }
                        val kind = when (result.kind) {
                            TransitLocationKind.STOP -> stringResource(R.string.location_kind_stop)
                            TransitLocationKind.ADDRESS -> stringResource(R.string.location_kind_address)
                            TransitLocationKind.POI -> stringResource(R.string.location_kind_poi)
                        }
                        Text(
                            stringResource(
                                R.string.search_location_result_format,
                                action,
                                kind,
                                result.label,
                            )
                        )
                    }
                }

                if (recentSearches.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.search_recents_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    recentSearches.take(5).forEachIndexed { index, recent ->
                        OutlinedButton(
                            onClick = { onRecentSearchSelected(recent) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search-recent-$index"),
                        ) {
                            Text(recent)
                        }
                    }
                    OutlinedButton(
                        onClick = onClearRecentSearches,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search-recents-clear"),
                    ) {
                        Text(stringResource(R.string.search_recents_clear))
                    }
                }

                FilledTonalButton(
                    onClick = onUseCurrentLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("current-location-origin"),
                ) {
                    Text(stringResource(R.string.current_location_origin))
                }
                locationMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("location-status"),
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (selectingOrigin) {
                        stringResource(R.string.map_instruction_origin)
                    } else {
                        stringResource(R.string.map_instruction_destination)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("map-instruction"),
                )

                val mapCenter = origin ?: destination ?: MapPoint(
                    latitude = 51.1657,
                    longitude = 10.4515,
                    label = "Deutschland",
                )
                TransitSelectionMap(
                    center = mapCenter,
                    nearbyStops = nearby,
                    origin = origin,
                    destination = destination,
                    mapPointLabel = stringResource(R.string.map_point_label),
                    onStopSelected = onMapStopSelected,
                    onMapPointSelected = onMapPointSelected,
                    onMapError = onMapError,
                )

                mapMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("map-status"),
                    )
                }
                Text(
                    text = stringResource(R.string.map_provider_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("map-provider"),
                )
            }
        }

        if (nearby.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.nearby_stops),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    nearbySource?.let {
                        Text(
                            text = stringResource(R.string.source_format, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    nearby.take(8).forEachIndexed { index, candidate ->
                        OutlinedButton(
                            onClick = { onNearbyStopSelected(candidate) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nearby-stop-$index"),
                        ) {
                            val action = if (selectingOrigin) {
                                stringResource(R.string.select_origin)
                            } else {
                                stringResource(R.string.select_destination)
                            }
                            Text(
                                stringResource(
                                    R.string.nearby_stop_format,
                                    action,
                                    if (candidate.stop.id in favoriteStopIds) {
                                        "★ " + candidate.stop.name
                                    } else {
                                        candidate.stop.name
                                    },
                                    candidate.distanceMeters,
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
