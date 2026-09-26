package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-only projection of G-140 transit experience semantics. Provider capability gates remain in TransitExperienceEngine. */
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

    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Yakındaki kalkışlar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("departure-board-title"))
        Text(
            when {
                isOfflineCache -> "Çevrimdışı önbellek • planlı saatler"
                providerCapabilities.realtimeDepartures -> "Canlı + planlı saatler"
                else -> "Planlı saatler"
            },
            modifier = Modifier.testTag("departure-freshness"),
        )
        Card(Modifier.fillMaxWidth().testTag("stop-detail")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stopName, style = MaterialTheme.typography.titleSmall)
                Text("$lineName • $direction", modifier = Modifier.testTag("line-detail"))
                Button(onClick = { favorite = store.toggleFavorite(stopId) }, modifier = Modifier.testTag("favorite-stop")) {
                    Text(if (favorite) "Favoriden çıkar" else "Favoriye ekle")
                }
            }
        }
        board.take(5).forEachIndexed { index, departure ->
            Button(onClick = { selectedTrip = departure.tripId; store.recordTrip(departure.tripId) }, modifier = Modifier.fillMaxWidth().testTag("departure-$index")) {
                val source = if (departure.isRealtime && !isOfflineCache) "canlı" else "planlı"
                Text("${departure.line} → ${departure.direction} • ${formatDepartureEpoch(departure.effectiveEpochSeconds)} • $source")
            }
        }
        selectedTrip?.let { tripId ->
            val alternatives = engine.alternateDepartures(departures, tripId, nowEpochSeconds)
            Text("Alternatif kalkışlar: ${alternatives.size}", modifier = Modifier.testTag("alternate-departures"))
        }
        engine.alerts(alerts).forEachIndexed { index, alert ->
            Card(Modifier.fillMaxWidth().testTag("service-alert-$index")) {
                Column(Modifier.padding(12.dp)) { Text(alert.title); Text(alert.detail) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Son yolculuklar: ${store.recents().size}", modifier = Modifier.testTag("recent-trips"))
        }
    }
}


private fun formatDepartureEpoch(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(epochSeconds * 1_000))
