package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-only projection of station/departure semantics. Provider capability gates remain explicit. */
@Composable
fun TransitExperiencePanel(
    stopId: String,
    stopName: String,
    lineName: String,
    direction: String,
    departures: List<Departure>,
    alerts: List<ServiceAlert>,
    isOfflineCache: Boolean,
    nowEpochSeconds: Long,
    providerCapabilities: TransitProviderCapabilities = TransitProviderCapabilities(),
    onSelectJourney: (String) -> Unit = {},
    statusMessage: String? = null,
    sourceLabel: String? = null,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val engine = remember(providerCapabilities) { TransitExperienceEngine(providerCapabilities) }
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
    }
    val store = remember(prefs) {
        val recentTripIds = prefs.getString("recent_trip_ids", "").orEmpty()
            .split('\u001F')
            .filter { it.isNotBlank() }
        TransitRecentsStore(
            favoriteIds = prefs.getStringSet("favorite_stop_ids", emptySet()).orEmpty().toList(),
            recentTripIds = recentTripIds,
        ) { favorites, recents ->
            prefs.edit()
                .putStringSet("favorite_stop_ids", favorites.toSet())
                .putString("recent_trip_ids", recents.joinToString("\u001F"))
                .apply()
        }
    }
    var favorite by remember(stopId) { mutableStateOf(store.isFavorite(stopId)) }
    var selectedTrip by remember { mutableStateOf<String?>(null) }
    val board = engine.departureBoard(departures, nowEpochSeconds)
    val cancelled = departures.filter { it.cancelled }

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.departures_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag("departure-board-title")
                .semantics { heading() },
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stop-detail"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsTransit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                    ) {
                        Text(
                            text = stopName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (lineName.isNotBlank() || direction.isNotBlank()) {
                            Text(
                                text = listOf(lineName, direction)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.testTag("line-detail"),
                            )
                        }
                    }
                    Surface(
                        onClick = { favorite = store.toggleFavorite(stopId) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("favorite-stop"),
                    ) {
                        Icon(
                            imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favorite) {
                                stringResource(R.string.favorite_remove)
                            } else {
                                stringResource(R.string.favorite_add)
                            },
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                val freshness = when {
                    loading -> stringResource(R.string.departures_loading)
                    isOfflineCache -> stringResource(R.string.departures_offline)
                    providerCapabilities.realtimeDepartures ->
                        stringResource(R.string.departures_live)
                    else -> stringResource(R.string.departures_planned)
                }
                Text(
                    text = freshness,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOfflineCache) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.testTag("departure-freshness"),
                )
                sourceLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.departures_source, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("departure-source"),
                    )
                }
                statusMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("departure-status"),
                    )
                }
            }
        }

        if (!loading && board.isEmpty() && cancelled.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("departures-empty"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.departures_empty),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (board.isNotEmpty()) {
            ArrivalSectionHeader(title = stringResource(R.string.departures_title))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    board.take(8).forEachIndexed { index, departure ->
                        DepartureRow(
                            departure = departure,
                            isOfflineCache = isOfflineCache,
                            onClick = {
                                selectedTrip = departure.tripId
                                store.recordTrip(departure.tripId)
                                departure.routeId?.let(onSelectJourney)
                            },
                            testTag = "departure-$index",
                        )
                    }
                }
            }
        }

        cancelled.take(3).forEachIndexed { index, departure ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cancelled-departure-$index"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = "${departure.line} → ${departure.direction}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.departure_cancelled),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        selectedTrip?.let { tripId ->
            val alternatives = engine.alternateDepartures(departures, tripId, nowEpochSeconds)
            Text(
                stringResource(R.string.alternate_departures, alternatives.size),
                modifier = Modifier.testTag("alternate-departures"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        engine.alerts(alerts).forEachIndexed { index, alert ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("service-alert-$index"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Column(
                        modifier = Modifier.padding(start = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = alert.title,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = alert.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.recent_trips, store.recents().size),
            modifier = Modifier.testTag("recent-trips"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DepartureRow(
    departure: Departure,
    isOfflineCache: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val source = if (departure.isRealtime && !isOfflineCache) {
        stringResource(R.string.departure_source_live)
    } else {
        stringResource(R.string.departure_source_planned)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.size(54.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = formatDepartureEpoch(departure.effectiveEpochSeconds),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = departure.line,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = departure.direction,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                )
            }
            Text(
                text = source,
                style = MaterialTheme.typography.bodySmall,
                color = if (departure.isRealtime && !isOfflineCache) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            departure.platform?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.departure_platform, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ArrowForward,
            contentDescription = stringResource(R.string.departure_select),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDepartureEpoch(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1_000))
