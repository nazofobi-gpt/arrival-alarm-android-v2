package com.nazofobi.arrivalalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { ArrivalAlarmApp() } }
    }
}

@Composable
fun ArrivalAlarmApp() {
    val controller = remember { JourneyController() }
    val transitController = remember { TransitController() }
    var state by remember { mutableStateOf(controller.state) }
    var transitState by remember { mutableStateOf(transitController.state) }
    fun act(block: JourneyController.() -> Unit) { controller.block(); state = controller.state }
    fun transitAct(block: TransitController.() -> Unit) { transitController.block(); transitState = transitController.state }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Varış Alarmı", style = MaterialTheme.typography.headlineMedium)
                Text("Durum: ${state.phase}", modifier = Modifier.testTag("phase"))
                state.distanceMeters?.let { Text("Hedefe ${it.toInt()} m") }
                state.error?.let { Text(it, modifier = Modifier.testTag("error")) }

                Button(
                    modifier = Modifier.testTag("start"),
                    onClick = { act { selectStart(GeoPoint(52.2689, 10.5268)) } },
                ) { Text("Başlangıcı belirle") }

                Button(
                    modifier = Modifier.testTag("destination"),
                    enabled = state.start != null,
                    onClick = { act { selectDestination(GeoPoint(52.2700, 10.5300)) } },
                ) { Text("Hedefi seç") }

                Button(
                    modifier = Modifier.testTag("arm"),
                    enabled = state.phase == JourneyPhase.DESTINATION_SELECTED,
                    onClick = { act { arm() } },
                ) { Text("Alarmı kur") }

                Button(
                    modifier = Modifier.testTag("approach"),
                    enabled = state.phase == JourneyPhase.ARMED,
                    onClick = { act { onDistanceChanged(200.0) } },
                ) { Text("Test: hedefe yaklaş") }

                Text("Statik transit: ${transitState.loadState}", modifier = Modifier.testTag("transit-state"))
                Button(
                    modifier = Modifier.testTag("search-airport"),
                    onClick = { transitAct { search("Flughafen") } },
                ) { Text("Durak ara: Flughafen") }
                if (transitState.matches.isNotEmpty()) {
                    Text(transitState.matches.joinToString { it.name }, modifier = Modifier.testTag("stop-results"))
                    Button(
                        modifier = Modifier.testTag("plan-airport"),
                        onClick = { transitAct { planFromFixtureStart("fixture-airport") } },
                    ) { Text("Bremen Hbf → Flughafen rotası") }
                }
                transitState.journey?.let { journey ->
                    val leg = journey.legs.single()
                    Text("${leg.line} • ${leg.direction}", modifier = Modifier.testTag("itinerary-line"))
                    Text(leg.stops.joinToString(" → ") { it.name }, modifier = Modifier.testTag("itinerary-stops"))
                    Text(transitState.message.orEmpty(), modifier = Modifier.testTag("transit-message"))
                }
                if (transitState.loadState == TransitLoadState.EMPTY || transitState.loadState == TransitLoadState.DEGRADED) {
                    Text(transitState.message.orEmpty(), modifier = Modifier.testTag("transit-message"))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrivalAlarmPreview() { MaterialTheme { ArrivalAlarmApp() } }
