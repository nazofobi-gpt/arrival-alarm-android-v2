package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
        val glyph: String,
        val tag: String,
    )

    val items = listOf(
        Item(ArrivalAppScreen.HOME, stringResource(R.string.nav_home), "⌂", "nav-home"),
        Item(ArrivalAppScreen.SEARCH, stringResource(R.string.nav_search), "⌕", "nav-search"),
        Item(ArrivalAppScreen.JOURNEY, stringResource(R.string.nav_journey), "↔", "nav-journey"),
        Item(ArrivalAppScreen.DEPARTURES, stringResource(R.string.nav_departures), "◷", "nav-departures"),
        Item(ArrivalAppScreen.SETTINGS, stringResource(R.string.nav_settings), "⚙", "nav-settings"),
    )

    NavigationBar(modifier = Modifier.testTag("bottom-navigation")) {
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
                icon = { Text(item.glyph) },
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
        modifier = modifier.fillMaxWidth().testTag("home-overview"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_overview_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_overview_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Text(
                    text = origin?.let { stringResource(R.string.origin_format, it.label) }
                        ?: stringResource(R.string.home_origin_missing),
                    modifier = Modifier.testTag("home-origin"),
                )
                Text(
                    text = destination?.let { stringResource(R.string.destination_format, it.label) }
                        ?: stringResource(R.string.home_destination_missing),
                    modifier = Modifier.testTag("home-destination"),
                )

                FilledTonalButton(
                    onClick = onUseCurrentLocation,
                    modifier = Modifier.fillMaxWidth().testTag("home-current-location"),
                ) {
                    Text(stringResource(R.string.current_location_origin))
                }
                OutlinedButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.fillMaxWidth().testTag("home-open-search"),
                ) {
                    Text(stringResource(R.string.home_search_action))
                }
                Button(
                    onClick = onOpenJourney,
                    enabled = origin != null && destination != null,
                    modifier = Modifier.fillMaxWidth().testTag("home-open-journey"),
                ) {
                    Text(stringResource(R.string.home_journey_action))
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        modifier = Modifier.testTag("home-map-status"),
                    )
                }
                Text(
                    text = stringResource(R.string.map_provider_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("home-map-provider"),
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
                    )
                    nearbySource?.let {
                        Text(
                            text = stringResource(R.string.source_format, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    nearby.take(4).forEachIndexed { index, candidate ->
                        OutlinedButton(
                            onClick = { onNearbyStopSelected(candidate) },
                            modifier = Modifier.fillMaxWidth().testTag("home-nearby-$index"),
                        ) {
                            Text(
                                stringResource(
                                    R.string.nearby_stop_format,
                                    stringResource(R.string.select_destination),
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
        }
    }
}
