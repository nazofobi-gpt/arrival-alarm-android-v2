package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    stopProviderId: String? = null,
    stopName: String,
    lineName: String,
    direction: String,
    departures: List<Departure>,
    alerts: List<ServiceAlert>,
    isOfflineCache: Boolean,
    nowEpochSeconds: Long,
    providerCapabilities: TransitProviderCapabilities = TransitProviderCapabilities(),
    onSelectJourney: (String) -> Unit = {},
    onSelectDeparture: ((Departure) -> Unit)? = null,
    statusMessage: String? = null,
    sourceLabel: String? = null,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val engine = remember(providerCapabilities, isOfflineCache) {
        TransitExperienceEngine(providerCapabilities, isOfflineCache = isOfflineCache)
    }
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
    }
    // History is private, device-only, and never collected without opt-in.
    var historyEnabled by remember(prefs) {
        mutableStateOf(try {
            prefs.getBoolean("recent_trip_history_enabled", false)
        } catch (_: ClassCastException) {
            false
        })
    }
    val store = remember(prefs) {
        // Wrong-typed legacy preferences must not crash the departure board.
        // Keep valid data in the other field, and recover malformed fields on
        // the next normal store update instead of clearing the entire store.
        val savedRecents = try {
            prefs.getString("recent_trip_ids", "").orEmpty()
        } catch (_: ClassCastException) {
            ""
        }
        val savedFavorites = try {
            prefs.getStringSet("favorite_stop_ids", emptySet()).orEmpty().toList()
        } catch (_: ClassCastException) {
            emptyList()
        }
        // Legacy implicit-history data is not retained or displayed without consent.
        if (!historyEnabled) prefs.edit().remove("recent_trip_ids").apply()
        val recentTripIds = if (historyEnabled) {
            savedRecents.split('\u001F').filter { it.isNotBlank() }
        } else emptyList()
        TransitRecentsStore(
            favoriteIds = savedFavorites,
            recentTripIds = recentTripIds,
        ) { favorites, recents ->
            prefs.edit()
                .putStringSet("favorite_stop_ids", favorites.toSet())
                .putString("recent_trip_ids", recents.joinToString("\u001F"))
                .apply()
        }
    }
    var favorite by remember(stopId) { mutableStateOf(store.isFavorite(stopId)) }
    var selectedDeparture by remember(stopId) { mutableStateOf<Departure?>(null) }
    var recentTripCount by remember(store) { mutableStateOf(store.recents().size) }
    // Stop-scoped route bookmarks never imply realtime alert subscriptions.
    // Fail closed on legacy/malformed preference types, preserving the departure board.
    var pinnedLineKeys by remember(prefs) {
        mutableStateOf(try {
            prefs.getStringSet("pinned_stop_line_keys_v1", emptySet())
                .orEmpty().filter(String::isNotBlank).toSet()
        } catch (_: ClassCastException) {
            emptySet()
        })
    }
    val board = engine.departureBoard(departures, nowEpochSeconds)
    val cancelled = upcomingCancelledDepartures(departures, nowEpochSeconds)

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.departures_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .testTag("departure-board-title")
                .semantics { heading() },
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stop-detail"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stopName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (lineName.isNotBlank() || direction.isNotBlank()) {
                    Text(
                        text = listOf(lineName, direction)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("line-detail"),
                    )
                }
                Text(
                    text = when {
                        loading -> stringResource(R.string.departures_loading)
                        isOfflineCache -> stringResource(R.string.departures_offline)
                        providerCapabilities.realtimeDepartures ->
                            stringResource(R.string.departures_live)
                        else -> stringResource(R.string.departures_planned)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOfflineCache) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("departure-freshness"),
                )
                sourceLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.departures_source, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("departure-source"),
                    )
                }
                statusMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("departure-status"),
                    )
                }
                OutlinedButton(
                    onClick = { favorite = store.toggleFavorite(stopId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("favorite-stop"),
                ) {
                    Text(
                        if (favorite) stringResource(R.string.favorite_remove)
                        else stringResource(R.string.favorite_add)
                    )
                }
            }
        }

        if (!loading && board.isEmpty() && cancelled.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("departures-empty"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = stringResource(R.string.departures_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        board.take(8).forEachIndexed { index, departure ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${departure.line} → ${departure.direction}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val source = if (departure.isRealtime && !isOfflineCache) {
                        stringResource(R.string.departure_source_live)
                    } else {
                        stringResource(R.string.departure_source_planned)
                    }
                    Text(
                        text = stringResource(
                            R.string.departure_time_source,
                            formatDepartureEpoch(departure.effectiveEpochSeconds),
                            source,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    departure.platform?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = stringResource(R.string.departure_platform, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            selectedDeparture = departure
                            if (historyEnabled) store.recordTrip(departure.tripId)
                            recentTripCount = store.recents().size
                            if (onSelectDeparture != null) onSelectDeparture(departure)
                            else departure.routeId?.let(onSelectJourney)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("departure-$index"),
                    ) {
                        Text(stringResource(R.string.departure_select))
                    }
                    pinnedStopLineKey(stopId, departure.routeId, stopProviderId)?.let { pinKey ->
                        val pinned = pinKey in pinnedLineKeys
                        if (pinned) {
                            Text(
                                text = stringResource(R.string.pin_line_local_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("pinned-departure-$index"),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val updated = if (pinKey in pinnedLineKeys) {
                                    pinnedLineKeys - pinKey
                                } else {
                                    pinnedLineKeys + pinKey
                                }
                                prefs.edit()
                                    .putStringSet("pinned_stop_line_keys_v1", updated)
                                    .apply()
                                pinnedLineKeys = updated
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("pin-departure-$index"),
                        ) {
                            Text(stringResource(
                                if (pinned) R.string.unpin_line_at_stop
                                else R.string.pin_line_at_stop,
                            ))
                        }
                    }
                }
            }
        }

        cancelled.forEachIndexed { index, departure ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cancelled-departure-$index"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${departure.line} → ${departure.direction}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(
                            R.string.departure_time_source,
                            formatDepartureEpoch(departure.scheduledEpochSeconds),
                            stringResource(R.string.departure_source_planned),
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.departure_cancelled),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        selectedDeparture?.let { selected ->
            if (board.any { it.sameScheduledInstanceAs(selected) }) {
                val alternatives = engine.alternateDepartures(departures, selected, nowEpochSeconds)
                Text(
                    stringResource(R.string.alternate_departures, alternatives.size),
                    modifier = Modifier.testTag("alternate-departures"),
                )
                alternatives.take(3).forEachIndexed { index, alternative ->
                    OutlinedButton(
                        onClick = {
                            selectedDeparture = alternative
                            if (historyEnabled) store.recordTrip(alternative.tripId)
                            recentTripCount = store.recents().size
                            if (onSelectDeparture != null) onSelectDeparture(alternative)
                            else alternative.routeId?.let(onSelectJourney)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("alternate-departure-$index"),
                    ) {
                        Text(
                            "${alternative.line} → ${alternative.direction} • " +
                                formatDepartureEpoch(alternative.effectiveEpochSeconds),
                        )
                    }
                }
            }
        }

        engine.alerts(alerts).forEachIndexed { index, alert ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("service-alert-$index"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(alert.title, fontWeight = FontWeight.SemiBold)
                    Text(alert.detail)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.recent_trips, recentTripCount),
                modifier = Modifier.testTag("recent-trips"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.recent_trips_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    val enable = !historyEnabled
                    if (!enable) store.clearRecents()
                    // Always erase the durable key on opt-out, even if memory was
                    // empty or the prior asynchronous persistence callback failed.
                    val editor = prefs.edit().putBoolean("recent_trip_history_enabled", enable)
                    if (!enable) editor.remove("recent_trip_ids")
                    editor.apply()
                    historyEnabled = enable
                    recentTripCount = store.recents().size
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("recent-trips-consent-toggle"),
            ) {
                Text(stringResource(
                    if (historyEnabled) R.string.recent_trips_disable
                    else R.string.recent_trips_enable,
                ))
            }
            if (historyEnabled && recentTripCount > 0) {
                OutlinedButton(
                    onClick = {
                        store.clearRecents()
                        recentTripCount = store.recents().size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("recent-trips-clear"),
                ) {
                    Text(stringResource(R.string.recent_trips_clear))
                }
            }
        }
    }
}

private fun formatDepartureEpoch(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1_000))
