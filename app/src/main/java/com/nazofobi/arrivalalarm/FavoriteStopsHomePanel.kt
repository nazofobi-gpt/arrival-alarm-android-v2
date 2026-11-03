package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Existing saved stop IDs remain local to this device. Only stops found in
 * the current GTFS index can open a departure board; missing IDs are not
 * reconstructed from old coordinates or guessed provider responses.
 */
@Composable
fun FavoriteStopsHomePanel(
    favoriteIds: Set<String>,
    stops: List<CatalogStop>,
    onOpenDepartures: (CatalogStop) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val resolved = stops.filter { it.id in favoriteIds && isFavoriteStopIdValid(it.id) }
        .distinctBy { it.id }
        .take(8)
    Card(modifier = Modifier.fillMaxWidth().testTag("home-favorites-panel")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_favorite_stops_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            when {
                favoriteIds.isEmpty() -> {
                    Text(stringResource(R.string.home_favorite_stops_empty))
                    TextButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag("home-favorites-search"),
                    ) {
                        Text(stringResource(R.string.home_favorite_stops_search))
                    }
                }
                resolved.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.home_favorite_stops_unavailable),
                        modifier = Modifier.testTag("home-favorites-unavailable"),
                    )
                }
                else -> {
                    resolved.forEachIndexed { index, stop ->
                        OutlinedButton(
                            onClick = { onOpenDepartures(stop) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("home-favorite-stop-$index"),
                        ) {
                            Text(
                                stringResource(
                                    R.string.home_favorite_stops_departures,
                                    stop.name,
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
