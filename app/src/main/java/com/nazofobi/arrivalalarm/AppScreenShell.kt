package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Train
import Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ArrivalAppScreen {
    HOME,
    SEARCH,
    ROUTES,
    JOURNEY,
    LIVE,
    DEPARTURES,
    SETTINGS,
}

@Composable
fun ArrivalBottomNavigation(
    current: ArrivalAppScreen,
    onSelect: (ArrivalAppScreen) -> Unit,
) {
    data class Item(
        val screen: ArrivalAppScreen,
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val tag: String,
    )

    val items = listOf(
        Item(ArrivalAppScreen.HOME, stringResource(R.string.nav_home), Icons.Rounded.Home, "nav-home"),
        Item(ArrivalAppScreen.SEARCH, stringResource(R.string.nav_search), Icons.Rounded.Search, "nav-search"),
        Item(ArrivalAppScreen.JOURNEY, stringResource(R.string.nav_journey), Icons.Rounded.AddCircle, "nav-journey"),
        Item(ArrivalAppScreen.DEPARTURES, stringResource(R.string.nav_departures), Icons.Rounded.Schedule, "nav-departures"),
        Item(ArrivalAppScreen.SETTINGS, stringResource(R.string.nav_settings), Icons.Rounded.Settings, "nav-settings"),
    )

    NavigationBar(
        modifier = Modifier.testTag("bottom-navigation"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            val selected = if (item.screen == ArrivalAppScreen.JOURNEY) {
                current == ArrivalAppScreen.ROUTES ||
                    current == ArrivalAppScreen.JOURNEY ||
                    current == ArrivalAppScreen.LIVE
            } else {
                current == item.screen
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.screen) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(item.label, maxLines = 1) },
                modifier = Modifier.testTag(item.tag),
            )
        }
    }
}

@Composable
fun HomeOverviewPanel(
    origin: MapPoint?,
    destination: MapPoint?,
    nearby: List<NearbyStop>,
    nearbySource: String?,
    favoriteStopIds: Set<String>,
    mapMessage: String?,
    onUseCurrentLocation: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenJourney: () -> Unit,
    onMapStopSelected: (CatalogStop) -> Unit,
    onMapPointSelected: (MapPoint) -> Unit,
    onMapError: (String) -> Unit,
    onNearbyStopSelected: (NearbyStop) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-overview"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.home_overview_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.home_overview_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArrivalSelectionRow(
                    title = stringResource(R.string.select_origin),
                    value = origin?.label ?: stringResource(R.string.home_origin_missing),
                    icon = if (origin == null) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                    onClick = onOpenSearch,
                    testTag = "home-origin",
                )
                ArrivalSelectionRow(
                    title = stringResource(R.string.select_destination),
                    value = destination?.label ?: stringResource(R.string.home_destination_missing),
                    icon = Icons.Rounded.Flag,
                    onClick = onOpenSearch,
                    testTag = "home-destination",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onUseCurrentLocation,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("home-current-location"),
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null)
                        Text(
                            text = stringResource(R.string.current_location_origin),
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 1,
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("home-open-search"),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Text(
                            text = stringResource(R.string.nav_search),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                Button(
                    onClick = onOpenJourney,
                    enabled = origin != null && destination != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("home-open-journey"),
                ) {
                    Text(stringResource(R.string.home_journey_action))
                }
            }
        }

        if (nearby.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        nearby.take(4).forEachIndexed { index, candidate ->
                            ArrivalListRow(
                                title = candidate.stop.name,
                                subtitle = stringResource(R.string.select_destination),
                                icon = Icons.Rounded.Train,
                                trailing = "${candidate.distanceMeters.toInt()} m",
                                selected = candidate.stop.id in favoriteStopIds,
                                onClick = { onNearbyStopSelected(candidate) },
                                testTag = "home-nearby-$index",
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrivalSectionHeader(title = stringResource(R.string.map_title))
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
                    modifier = Modifier.testTag("home-map-status"),
                )
            }
        }
    }
}

@Composable
fun ScreenEmptyState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
