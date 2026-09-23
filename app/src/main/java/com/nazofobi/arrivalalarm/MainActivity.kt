package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
        fun point(prefix: String): GeoPoint? =
            if (prefs.contains("${prefix}_lat") && prefs.contains("${prefix}_lon")) {
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
    val transit = remember { NationwideTransitGateway(context) }
    val currentLocation = remember { AndroidCurrentLocation(context) }
    val guidanceSpeaker = remember { AndroidTextToSpeechSpeaker(context) }
    val guidanceController = remember {
        GuidanceReliabilityController(
            guidanceSpeaker,
            SharedPreferencesGuidanceStateStore(context.getSharedPreferences("guidance_state", Context.MODE_PRIVATE)),
        )
    }

    var journey by remember { mutableStateOf(controller.state) }
    var dataState by remember { mutableStateOf(transit.currentState()) }
    var query by remember { mutableStateOf("") }
    var searchBusy by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<CatalogStop>()) }
    var searchSource by remember { mutableStateOf<String?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var origin by remember { mutableStateOf<MapPoint?>(null) }
    var destination by remember { mutableStateOf<MapPoint?>(null) }
    var nearby by remember { mutableStateOf(emptyList<NearbyStop>()) }
    var nearbySource by remember { mutableStateOf<String?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var guidanceState by remember { mutableStateOf(guidanceController.state) }
    var inferenceEnabled by remember { mutableStateOf(false) }

    fun act(block: JourneyController.() -> Unit) {
        controller.block()
        journey = controller.state
    }

    fun loadNearby(point: MapPoint) {
        transit.nearbyStops(point.latitude, point.longitude) { values, source ->
            nearby = values
            nearbySource = source
        }
    }

    fun setOrigin(point: MapPoint) {
        origin = point
        destination = null
        act { selectStart(GeoPoint(point.latitude, point.longitude)) }
        loadNearby(point)
    }

    fun setDestination(stop: CatalogStop) {
        val point = MapPoint(stop.latitude, stop.longitude, stop.name)
        if (origin == null) {
            setOrigin(point)
        } else {
            destination = point
            act { selectDestination(GeoPoint(point.latitude, point.longitude)) }
        }
    }

    fun runSearch() {
        val q = query.trim()
        searchBusy = true
        searchMessage = null
        transit.searchStops(q) { values, source ->
            searchBusy = false
            searchResults = values
            searchSource = source
            searchMessage = if (values.isEmpty()) "Durak bulunamadı" else null
        }
    }

    fun useCurrentLocation() {
        locationMessage = "Konum alınıyor…"
        currentLocation.request { result ->
            result.onSuccess { point ->
                locationMessage = null
                setOrigin(MapPoint(point.latitude, point.longitude, "Mevcut konum"))
            }.onFailure {
                locationMessage = it.message ?: "Konum alınamadı"
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) useCurrentLocation()
        else locationMessage = "Konum izni reddedildi; durak araması yine kullanılabilir"
    }

    val guidancePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
        guidanceState = guidanceController.state
    }

    LaunchedEffect(Unit) {
        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
        guidanceState = guidanceController.state
    }

    DisposableEffect(Unit) {
        onDispose {
            guidanceSpeaker.shutdown()
            transit.close()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Varış Alarmı", style = MaterialTheme.typography.headlineMedium)
                Text("Almanya transit arama", style = MaterialTheme.typography.titleMedium)

                val dataText = when (val value = dataState) {
                    NationwideDataState.Idle -> "Almanya veri indeksi henüz hazır değil"
                    is NationwideDataState.Loading -> value.message
                    is NationwideDataState.Ready -> "Almanya tam durak indeksi hazır • ${value.stopCount} durak • gtfs.de/DELFI"
                    is NationwideDataState.Error -> "Tam indeks yüklenemedi • canlı arama açık • ${value.message}"
                }
                Text(dataText, modifier = Modifier.testTag("nationwide-data-state"))
                if (dataState !is NationwideDataState.Ready && dataState !is NationwideDataState.Loading) {
                    Button(
                        onClick = { transit.ensureNationwideIndex { dataState = it } },
                        modifier = Modifier.testTag("nationwide-index-download"),
                    ) { Text("Tam Almanya durak indeksini indir") }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Almanya'da durak ara") },
                    modifier = Modifier.fillMaxWidth().testTag("catalog-search"),
                    singleLine = true,
                )
                Button(
                    onClick = { runSearch() },
                    enabled = !searchBusy && query.trim().length >= 2,
                    modifier = Modifier.testTag("catalog-search-submit"),
                ) { Text(if (searchBusy) "Aranıyor…" else "Ara") }

                searchSource?.let { Text("Kaynak: $it", modifier = Modifier.testTag("search-source")) }
                searchMessage?.let { Text(it, modifier = Modifier.testTag("search-message")) }

                searchResults.take(12).forEachIndexed { index, stop ->
                    Button(
                        onClick = { setDestination(stop) },
                        modifier = Modifier.fillMaxWidth().testTag("search-result-$index"),
                    ) {
                        val action = if (origin == null) "Başlangıç" else "Varış"
                        Text("$action • ${stop.name}")
                    }
                }

                Button(
                    onClick = {
                        if (currentLocation.hasPermission()) useCurrentLocation()
                        else locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    modifier = Modifier.testTag("current-location-origin"),
                ) { Text("Mevcut konumu başlangıç yap") }
                locationMessage?.let { Text(it, modifier = Modifier.testTag("location-status")) }

                origin?.let {
                    Text("Başlangıç: ${it.label}", modifier = Modifier.testTag("origin-label"))
                }
                destination?.let {
                    Text("Varış: ${it.label}", modifier = Modifier.testTag("destination-label"))
                }

                if (nearby.isNotEmpty()) {
                    Text("En yakın duraklar", style = MaterialTheme.typography.titleSmall)
                    nearbySource?.let { Text("Kaynak: $it") }
                    nearby.take(8).forEachIndexed { index, candidate ->
                        Button(
                            onClick = { setDestination(candidate.stop) },
                            modifier = Modifier.fillMaxWidth().testTag("nearby-stop-$index"),
                        ) {
                            Text("${candidate.stop.name} • ${candidate.distanceMeters} m")
                        }
                    }
                }

                Text("Durum: ${journey.phase}", modifier = Modifier.testTag("phase"))
                journey.error?.let { Text(it, modifier = Modifier.testTag("error")) }
                Button(
                    onClick = { act { arm() } },
                    enabled = journey.phase == JourneyPhase.DESTINATION_SELECTED,
                    modifier = Modifier.testTag("arm"),
                ) { Text("Varış alarmını kur") }

                Text("Sesli yönlendirme", style = MaterialTheme.typography.titleMedium)
                Text(guidanceState.status, modifier = Modifier.testTag("guidance-status"))
                Button(
                    onClick = { guidancePermissionLauncher.launch(AndroidGuidancePermissions.runtimePermissions()) },
                    modifier = Modifier.testTag("guidance-permissions"),
                ) { Text("Ses/notification izinlerini kontrol et") }
                Button(
                    onClick = {
                        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
                        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
                        guidanceState = guidanceController.state
                    },
                    modifier = Modifier.testTag("guidance-route-refresh"),
                ) { Text("Ses rotasını yenile") }
                destination?.let { target ->
                    Button(
                        onClick = {
                            guidanceController.requestGuidance(
                                GuidanceUtterance("destination", "Hedef ${target.label}", "tr-TR")
                            )
                            guidanceState = guidanceController.state
                        },
                        enabled = guidanceState.permissionState != GuidancePermissionState.DENIED,
                        modifier = Modifier.testTag("guidance-destination"),
                    ) { Text("Hedefi seslendir") }
                }

                Text("Sefer algılama (isteğe bağlı)", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = inferenceEnabled,
                    onCheckedChange = { inferenceEnabled = it },
                    modifier = Modifier.testTag("trip-inference-toggle"),
                )
                Text(
                    if (inferenceEnabled) "Açık • kullanıcı onayı olmadan alarm/hedef kurulmaz"
                    else "Kapalı • otomatik sefer tahmini yapılmaz",
                    modifier = Modifier.testTag("trip-inference-status"),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrivalAlarmPreview() {
    MaterialTheme { ArrivalAlarmApp() }
}
