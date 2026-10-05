package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class HomeSearchMode {
    HOME,
    SEARCH,
}

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
    nearbyMessage: String?,
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
    mode: HomeSearchMode = HomeSearchMode.HOME,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (mode == HomeSearchMode.HOME) {
                stringResource(R.string.journey_points_title)
            } else {
                stringResource(R.string.search_stops_label)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
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

        if (mode == HomeSearchMode.HOME) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ArrivalSelectionRow(
                        title = stringResource(R.string.select_origin),
                        value = origin?.label ?: stringResource(R.string.home_origin_missing),
                        icon = if (origin == null) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                        onClick = onSelectOriginTarget,
                        testTag = "search-target-origin",
                    )
                    ArrivalSelectionRow(
                        title = stringResource(R.string.select_destination),
                        value = destination?.label ?: stringResource(R.string.home_destination_missing),
                        icon = Icons.Rounded.Flag,
                        onClick = onSelectDestinationTarget,
                        enabled = origin != null,
                        testTag = "search-target-destination",
                    )
                    ArrivalSelectionRow(
                        title = stringResource(R.string.current_location_origin),
                        value = stringResource(R.string.current_location_label),
                        icon = Icons.Rounded.MyLocation,
                        onClick = onUseCurrentLocation,
                        testTag = "current-location-origin",
                        supporting = locationMessage,
                    )
                }
            }

            nearbyMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("nearby-message"),
                )
            }

            if (nearby.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArrivalSectionHeader(title = stringResource(R.string.nearby_stops))
                    nearbySource?.let {
                        Text(
                            text = stringResource(R.string.source_format, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            nearby.take(8).forEachIndexed { index, candidate ->
                                val action = if (selectingOrigin) {
                                    stringResource(R.string.select_origin)
                                } else {
                                    stringResource(R.string.select_destination)
                                }
                                ArrivalListRow(
                                    title = candidate.stop.name,
                                    subtitle = action,
                                    icon = Icons.Rounded.DirectionsTransit,
                                    trailing = "${candidate.distanceMeters.toInt()} m",
                                    selected = candidate.stop.id in favoriteStopIds,
                                    onClick = { onNearbyStopSelected(candidate) },
                                    testTag = "nearby-stop-$index",
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArrivalSectionHeader(title = stringResource(R.string.map_title))
                Text(
                    text = if (selectingOrigin) {
                        stringResource(R.string.map_instruction_origin)
                    } else {
                        stringResource(R.string.map_instruction_destination)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("map-instruction"),
                )
                val mapCenter = origin ?: destination ?: MapPoint(
                    latitude = 51.1657,
                    longitude = 10.4515,
                    label = "Deutschland",
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
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
                }
                mapMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("map-status"),
                    )
                }
            }
        } else {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = { Text(stringResource(R.string.search_stops_label)) },
                        supportingText = { Text(stringResource(R.string.search_supporting_text)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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
                            .heightIn(min = 48.dp)
                            .testTag("catalog-search-submit"),
                    ) {
                        Text(
                            if (searchBusy) stringResource(R.string.searching)
                            else stringResource(R.string.search_action)
                        )
                    }
                }
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

            if (searchResults.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        searchResults.take(12).forEachIndexed { index, result ->
                            val kind = when (result.kind) {
                                TransitLocationKind.STOP -> stringResource(R.string.location_kind_stop)
                                TransitLocationKind.ADDRESS -> stringResource(R.string.location_kind_address)
                                TransitLocationKind.POI -> stringResource(R.string.location_kind_poi)
                            }
                            ArrivalListRow(
                                title = result.label,
                                subtitle = kind,
                                icon = if (result.kind == TransitLocationKind.STOP) {
                                    Icons.Rounded.DirectionsTransit
                                } else {
                                    Icons.Rounded.Place
                                },
                                onClick = { onSearchResultSelected(result) },
                                testTag = "search-result-$index",
                            )
                        }
                    }
                }
            }

            if (recentSearches.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArrivalSectionHeader(title = stringResource(R.string.search_recents_title))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            recentSearches.take(5).forEachIndexed { index, recent ->
                                ArrivalListRow(
                                    title = recent,
                                    subtitle = null,
                                    icon = Icons.Rounded.History,
                                    onClick = { onRecentSearchSelected(recent) },
                                    testTag = "search-recent-$index",
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onClearRecentSearches,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("search-recents-clear"),
                    ) {
                        Text(stringResource(R.string.search_recents_clear))
                    }
                }
            }
        }
    }
}
