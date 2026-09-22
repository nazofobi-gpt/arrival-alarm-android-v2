package com.nazofobi.arrivalalarm

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class SharedPreferencesJourneyStateStore(context: Context) : JourneyStateStore {
    private val prefs = context.getSharedPreferences("journey_state", Context.MODE_PRIVATE)

    override fun load(): JourneyUiState? {
        if (!prefs.contains("phase")) return null
        fun point(prefix: String): GeoPoint? = if (prefs.contains("${prefix}_lat") && prefs.contains("${prefix}_lon")) {
            GeoPoint(
                Double.fromBits(prefs.getLong("${prefix}_lat", 0L)),
                Double.fromBits(prefs.getLong("${prefix}_lon", 0L)),
            )
        } else null
        val distance = if (prefs.contains("distance")) Double.fromBits(prefs.getLong("distance", 0L)) else null
        val phase = runCatching { JourneyPhase.valueOf(prefs.getString("phase", JourneyPhase.EMPTY.name)!!) }
            .getOrDefault(JourneyPhase.EMPTY)
        return JourneyUiState(phase, point("start"), point("destination"), distance)
    }

    override fun save(state: JourneyUiState) {
        prefs.edit().apply {
            clear()
            putString("phase", state.phase.name)
            state.start?.let { putLong("start_lat", it.latitude.toBits()); putLong("start_lon", it.longitude.toBits()) }
            state.destination?.let { putLong("destination_lat", it.latitude.toBits()); putLong("destination_lon", it.longitude.toBits()) }
            state.distanceMeters?.let { putLong("distance", it.toBits()) }
        }.apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { ArrivalAlarmApp() } }
    }
}

@Composable
fun ArrivalAlarmApp() {
    val context = LocalContext.current.applicationContext
    val controller = remember { JourneyController(stateStore = SharedPreferencesJourneyStateStore(context)) }
    val staticRepository = remember { FixtureStaticTransitRepository() }
    val transitController = remember { TransitController(staticRepository) }
    val realtimeRepository = remember {
        OverlayTransitRepository(staticRepository, VbnJsonRealtimeSource(), EpochClock { System.currentTimeMillis() / 1000 })
    }
    var state by remember { mutableStateOf(controller.state) }
    var transitState by remember { mutableStateOf(transitController.state) }
    var realtime by remember { mutableStateOf<TransitRealtimeOverlay?>(null) }
    var inferenceEnabled by remember { mutableStateOf(false) }
    var inferenceResult by remember { mutableStateOf<TripInferenceResult?>(null) }
    var confirmedTripId by remember { mutableStateOf<String?>(null) }
    fun act(block: JourneyController.() -> Unit) { controller.block(); state = controller.state }
    fun transitAct(block: TransitController.() -> Unit) { transitController.block(); transitState = transitController.state }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Varış Alarmı", style = MaterialTheme.typography.headlineMedium)
                Text("Durum: ${state.phase}", modifier = Modifier.testTag("phase"))
                state.distanceMeters?.let { Text("Hedefe ${it.toInt()} m") }
                state.error?.let { Text(it, modifier = Modifier.testTag("error")) }
                Button(onClick = { act { selectStart(GeoPoint(52.2689, 10.5268)) } }, modifier = Modifier.testTag("start")) { Text("Başlangıcı belirle") }
                Button(onClick = { act { selectDestination(GeoPoint(52.2700, 10.5300)) } }, modifier = Modifier.testTag("destination"), enabled = state.start != null) { Text("Hedefi seç") }
                Button(onClick = { act { arm() } }, modifier = Modifier.testTag("arm"), enabled = state.phase == JourneyPhase.DESTINATION_SELECTED) { Text("Alarmı kur") }
                Button(onClick = { act { onDistanceChanged(200.0) } }, modifier = Modifier.testTag("approach"), enabled = state.phase == JourneyPhase.ARMED) { Text("Test: hedefe yaklaş") }

                Text("Statik transit: ${transitState.loadState}", modifier = Modifier.testTag("transit-state"))
                Button(onClick = { transitAct { search("Flughafen") } }, modifier = Modifier.testTag("search-airport")) { Text("Durak ara: Flughafen") }
                if (transitState.matches.isNotEmpty()) {
                    Text(transitState.matches.joinToString { it.name }, modifier = Modifier.testTag("stop-results"))
                    Button(
                        onClick = {
                            transitAct { planFromFixtureStart("fixture-airport") }
                            realtime = realtimeRepository.plan("fixture-bremen-hbf", "fixture-airport")?.realtime
                        },
                        modifier = Modifier.testTag("plan-airport"),
                    ) { Text("Bremen Hbf → Flughafen rotası") }
                }
                transitState.journey?.let { journey ->
                    val leg = journey.legs.single()
                    Text("${leg.line} • ${leg.direction}", modifier = Modifier.testTag("itinerary-line"))
                    Text(leg.stops.joinToString(" → ") { it.name }, modifier = Modifier.testTag("itinerary-stops"))
                    Text(transitState.message.orEmpty(), modifier = Modifier.testTag("transit-message"))
                    Text(realtime?.status ?: "Canlı veri bekleniyor • statik rota", modifier = Modifier.testTag("realtime-status"))
                }
                if (transitState.loadState == TransitLoadState.EMPTY || transitState.loadState == TransitLoadState.DEGRADED) {
                    Text(transitState.message.orEmpty(), modifier = Modifier.testTag("transit-message"))
                }

                Text("Sefer algılama (isteğe bağlı)", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (inferenceEnabled) "Açık • öneri hiçbir zaman alarmı otomatik kurmaz" else "Kapalı • konumdan sefer tahmini yapılmaz",
                    modifier = Modifier.testTag("trip-inference-status"),
                )
                Switch(
                    checked = inferenceEnabled,
                    onCheckedChange = {
                        inferenceEnabled = it
                        inferenceResult = null
                        confirmedTripId = null
                    },
                    modifier = Modifier.testTag("trip-inference-toggle"),
                )
                Button(
                    onClick = {
                        val now = System.currentTimeMillis() / 1000
                        val observation = MovementObservation(
                            point = GeoPoint(53.0831, 8.8131),
                            speedMps = 8.0,
                            bearingDegrees = 180.0,
                            epochSeconds = now,
                        )
                        val candidates = listOf(
                            TripCandidate(
                                routeId = "fixture-6-south",
                                line = "6",
                                direction = "Flughafen Bremen",
                                anchor = GeoPoint(53.0830, 8.8130),
                                expectedBearingDegrees = 180.0,
                                scheduledEpochSeconds = now,
                                realtimeDelaySeconds = realtime?.delaySeconds,
                            ),
                            TripCandidate(
                                routeId = "fixture-6-north",
                                line = "6",
                                direction = "Universität",
                                anchor = GeoPoint(53.0830, 8.8130),
                                expectedBearingDegrees = 0.0,
                                scheduledEpochSeconds = now + 600,
                            ),
                        )
                        inferenceResult = TripInferenceEngine(
                            TripInferenceSettings(enabled = inferenceEnabled)
                        ).infer(observation, candidates)
                    },
                    enabled = inferenceEnabled,
                    modifier = Modifier.testTag("trip-inference-run"),
                ) { Text("Sefer adayı hesapla") }
                inferenceResult?.let { result ->
                    val accepted = result.accepted
                    if (accepted == null) {
                        Text(result.reason, modifier = Modifier.testTag("trip-inference-result"))
                    } else {
                        Text(
                            "${accepted.candidate.line} • ${accepted.candidate.direction} • güven %${(accepted.confidence * 100).toInt()}",
                            modifier = Modifier.testTag("trip-inference-result"),
                        )
                        Text(accepted.explanation, modifier = Modifier.testTag("trip-inference-explanation"))
                        Button(
                            onClick = { confirmedTripId = accepted.candidate.routeId },
                            modifier = Modifier.testTag("trip-inference-confirm"),
                        ) { Text("Bu seferdeyim") }
                    }
                }
                confirmedTripId?.let {
                    Text(
                        "Sefer onaylandı: $it • hedef/alarm ayrıca kullanıcı tarafından seçilmeli",
                        modifier = Modifier.testTag("trip-inference-confirmed"),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrivalAlarmPreview() { MaterialTheme { ArrivalAlarmApp() } }
