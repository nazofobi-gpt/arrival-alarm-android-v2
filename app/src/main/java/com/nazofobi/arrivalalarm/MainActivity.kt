package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
import kotlinx.coroutines.delay

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
    val screenAssistPermission = remember { ScreenAssistPermission(context) }
    val screenAssistSession = remember { ScreenAssistSession() }
    val screenAssistRealtimeSession = remember { ScreenAssistRealtimeSession() }
    val screenAssistUiController = remember { ScreenAssistUiController() }
    val screenAssistRuntime = remember { ScreenAssistCaptureRuntime(context) }
    val screenAssistBrokerCredentials = remember { ScreenAssistBrokerCredentialStore(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val screenAssistRealtimeRuntime = remember {
        ScreenAssistRealtimeRuntime(
            session = screenAssistRealtimeSession,
            brokerTokenResponse = {
                val brokerUrl = BuildConfig.SCREEN_ASSIST_BROKER_URL.trim()
                check(brokerUrl.isNotEmpty()) {
                    "Screen Assist token broker is not configured"
                }
                ScreenAssistBrokerTransport(
                    brokerUrl = brokerUrl,
                    authTokenProvider = { screenAssistBrokerCredentials.read() },
                ).requestTokenResponse()
            },
            socketFactory = { ScreenAssistOpenAiWebRtcSocket(context) },
        )
    }

    var journey by remember { mutableStateOf(controller.state) }
    var dataState by remember { mutableStateOf(transit.currentState()) }
    var query by remember { mutableStateOf("") }
    var searchBusy by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<CatalogStop>()) }
    var searchSource by remember { mutableStateOf<String?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var searchRequestId by remember { mutableStateOf(0L) }
    var selectingOrigin by remember { mutableStateOf(true) }
    var origin by remember { mutableStateOf<MapPoint?>(null) }
    var destination by remember { mutableStateOf<MapPoint?>(null) }
    var nearby by remember { mutableStateOf(emptyList<NearbyStop>()) }
    var nearbySource by remember { mutableStateOf<String?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var routeOptions by remember { mutableStateOf(emptyList<RouteOption>()) }
    var routeStatus by remember { mutableStateOf<String?>(null) }
    var routeBusy by remember { mutableStateOf(false) }
    val routeCache = remember { OfflineTransitCache() }
    var guidanceState by remember { mutableStateOf(guidanceController.state) }
    var inferenceEnabled by remember { mutableStateOf(false) }
    var inferenceResult by remember { mutableStateOf<TripInferenceResult?>(null) }
    var confirmedTripId by remember { mutableStateOf<String?>(null) }
    var screenAssistBrokerConfigured by remember {
        mutableStateOf(screenAssistBrokerCredentials.hasCredential())
    }
    var screenAssistBrokerCredentialDraft by remember { mutableStateOf("") }
    var screenAssistGuidance by remember { mutableStateOf<String?>(null) }
    var screenAssistUiState by remember {
        mutableStateOf(
            screenAssistUiController.reduce(
                screenAssistSession.state,
                screenAssistRealtimeSession.phase,
            )
        )
    }

    fun refreshScreenAssistUi() {
        screenAssistUiState = screenAssistUiController.reduce(
            screenAssistSession.state,
            screenAssistRealtimeSession.phase,
        )
    }

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
        routeOptions = emptyList()
        routeStatus = null
        selectingOrigin = false
        act { selectStart(GeoPoint(point.latitude, point.longitude)) }
        loadNearby(point)
    }

    fun routeKey(a: MapPoint, b: MapPoint) =
        "${a.latitude},${a.longitude}->${b.latitude},${b.longitude}"

    fun loadRoutes(a: MapPoint, b: MapPoint) {
        routeBusy = true
        routeStatus = "Rota hesaplanıyor…"
        val key = routeKey(a, b)
        transit.journeyOptions(a, b) { options, status ->
            routeBusy = false
            if (options.isNotEmpty()) {
                routeOptions = options
                routeStatus = status
                routeCache.put(CachedTransitPlan(key, options, emptyList(), System.currentTimeMillis() / 1000))
            } else {
                val cached = routeCache.routeOptions(key)
                routeOptions = cached
                routeStatus = if (cached.isNotEmpty()) "Canlı rota yok • son başarılı çevrimdışı rota" else status
            }
        }
    }

    fun setDestination(stop: CatalogStop) {
        val point = MapPoint(stop.latitude, stop.longitude, stop.name)
        val currentOrigin = origin
        if (currentOrigin == null) {
            setOrigin(point)
        } else {
            destination = point
            act { selectDestination(GeoPoint(point.latitude, point.longitude)) }
            loadRoutes(currentOrigin, point)
        }
    }

    fun runSearch(rawQuery: String = query) {
        val q = rawQuery.trim()
        if (q.length < 2) return
        val requestId = searchRequestId + 1
        searchRequestId = requestId
        searchBusy = true
        searchMessage = null
        transit.searchStops(q) callback@{ values, source ->
            if (requestId != searchRequestId) return@callback
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

    val screenAssistConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val grant = screenAssistPermission.granted(result.resultCode, result.data)
        if (grant == null) {
            screenAssistSession.consentDenied()
            refreshScreenAssistUi()
        } else {
            screenAssistSession.acceptGrant(grant)
            screenAssistGuidance = null
            screenAssistRealtimeRuntime.connect(
                onConnected = { sender ->
                    mainHandler.post {
                        if (screenAssistSession.state.phase != ScreenAssistPhase.READY) {
                            sender.stop()
                            return@post
                        }

                        ScreenAssistRuntimeBridge.attach(sender)
                        val metrics = context.resources.displayMetrics
                        val started = runCatching {
                            screenAssistRuntime.start(
                                grant = grant,
                                width = metrics.widthPixels,
                                height = metrics.heightPixels,
                                densityDpi = metrics.densityDpi,
                            )
                        }.isSuccess

                        if (started && screenAssistSession.start()) {
                            refreshScreenAssistUi()
                        } else {
                            ScreenAssistRuntimeBridge.detach(sender)
                            screenAssistRealtimeRuntime.stop()
                            screenAssistSession.captureFailed()
                            refreshScreenAssistUi()
                        }
                    }
                },
                onGuidanceDelta = { delta ->
                    mainHandler.post {
                        val current = screenAssistGuidance.orEmpty()
                        screenAssistGuidance = (current + delta).takeLast(2_000)
                    }
                },
                onFailure = {
                    mainHandler.post {
                        ScreenAssistRuntimeBridge.detach()
                        screenAssistSession.stop()
                        refreshScreenAssistUi()
                    }
                },
            )
            refreshScreenAssistUi()
        }
    }

    LaunchedEffect(Unit) {
        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
        guidanceState = guidanceController.state
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            searchResults = emptyList()
            searchSource = null
            searchMessage = null
            searchBusy = false
            return@LaunchedEffect
        }
        delay(1_000)
        runSearch(q)
    }

    DisposableEffect(Unit) {
        ScreenAssistRuntimeBridge.observe(
            onProjectionStopped = {
                mainHandler.post {
                    ScreenAssistRuntimeBridge.detach()
                    screenAssistRealtimeRuntime.stop()
                    screenAssistSession.projectionRevoked()
                    refreshScreenAssistUi()
                }
            },
            onCaptureError = {
                mainHandler.post {
                    ScreenAssistRuntimeBridge.detach()
                    screenAssistRealtimeRuntime.stop()
                    screenAssistSession.captureFailed()
                    refreshScreenAssistUi()
                }
            },
        )
        onDispose {
            ScreenAssistRuntimeBridge.clearObserver()
            ScreenAssistRuntimeBridge.detach()
            screenAssistRealtimeRuntime.close()
            screenAssistRuntime.stop()
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

                ScreenAssistPanel(
                    state = screenAssistUiState,
                    onStart = {
                        ScreenAssistRuntimeBridge.detach()
                        screenAssistRealtimeRuntime.stop()
                        screenAssistGuidance = null
                        screenAssistSession.requestConsent()
                        refreshScreenAssistUi()
                        screenAssistConsentLauncher.launch(screenAssistPermission.createCaptureIntent())
                    },
                    onPause = {
                        if (screenAssistSession.pause()) {
                            screenAssistRuntime.pause()
                            refreshScreenAssistUi()
                        }
                    },
                    onResume = {
                        if (screenAssistSession.resume()) {
                            screenAssistRuntime.resume()
                            refreshScreenAssistUi()
                        }
                    },
                    onStop = {
                        ScreenAssistRuntimeBridge.detach()
                        screenAssistRealtimeRuntime.stop()
                        screenAssistRuntime.stop()
                        screenAssistSession.stop()
                        screenAssistGuidance = null
                        refreshScreenAssistUi()
                    },
                    brokerCredentialConfigured = screenAssistBrokerConfigured,
                    brokerCredentialDraft = screenAssistBrokerCredentialDraft,
                    onBrokerCredentialDraftChange = { screenAssistBrokerCredentialDraft = it },
                    onSaveBrokerCredential = {
                        val saved = runCatching {
                            screenAssistBrokerCredentials.save(screenAssistBrokerCredentialDraft)
                        }.isSuccess
                        if (saved) {
                            screenAssistBrokerCredentialDraft = ""
                            screenAssistBrokerConfigured = true
                        }
                    },
                    onClearBrokerCredential = {
                        ScreenAssistRuntimeBridge.detach()
                        screenAssistRealtimeRuntime.stop()
                        screenAssistRuntime.stop()
                        screenAssistSession.stop()
                        screenAssistBrokerCredentials.clear()
                        screenAssistBrokerConfigured = false
                        screenAssistBrokerCredentialDraft = ""
                        screenAssistGuidance = null
                        refreshScreenAssistUi()
                    },
                    guidanceText = screenAssistGuidance,
                    modifier = Modifier.testTag("screen-assist-panel"),
                )

                Text("Almanya transit arama", style = MaterialTheme.typography.titleMedium)

                val dataText = when (val value = dataState) {
                    NationwideDataState.Idle -> "Almanya veri indeksi henüz hazır değil"
                    is NationwideDataState.Loading -> value.message
                    is NationwideDataState.Ready -> "Almanya tam durak indeksi hazır • ${value.stopCount} durak • gtfs.de/DELFI • sürüm ${value.sourceVersion} • ${formatEpoch(value.fetchedAtEpochSeconds)}"
                    is NationwideDataState.Error -> "Tam indeks yüklenemedi • canlı arama açık • ${value.message}"
                }
                Text(dataText, modifier = Modifier.testTag("nationwide-data-state"))
                if (dataState !is NationwideDataState.Ready && dataState !is NationwideDataState.Loading) {
                    Button(
                        onClick = { transit.ensureNationwideIndex { dataState = it } },
                        modifier = Modifier.testTag("nationwide-index-download"),
                    ) { Text("Tam Almanya durak indeksini indir") }
                }

                Text(
                    if (selectingOrigin) "Arama sonucu başlangıç durağı olarak seçilecek"
                    else "Arama sonucu varış durağı olarak seçilecek",
                    modifier = Modifier.testTag("search-target-status"),
                )
                Button(
                    onClick = { selectingOrigin = true },
                    enabled = !selectingOrigin,
                    modifier = Modifier.testTag("search-target-origin"),
                ) { Text(if (selectingOrigin) "Başlangıç seçiliyor" else "Başlangıç seç") }
                Button(
                    onClick = { selectingOrigin = false },
                    enabled = origin != null && selectingOrigin,
                    modifier = Modifier.testTag("search-target-destination"),
                ) { Text(if (!selectingOrigin) "Varış seçiliyor" else "Varış seç") }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        searchRequestId += 1
                    },
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
                        onClick = {
                            val point = MapPoint(stop.latitude, stop.longitude, stop.name)
                            if (selectingOrigin) setOrigin(point) else setDestination(stop)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("search-result-$index"),
                    ) {
                        val action = if (selectingOrigin) "Başlangıç" else "Varış"
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
                            onClick = {
                                val point = MapPoint(
                                    candidate.stop.latitude,
                                    candidate.stop.longitude,
                                    candidate.stop.name,
                                )
                                if (selectingOrigin) setOrigin(point) else setDestination(candidate.stop)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("nearby-stop-$index"),
                        ) {
                            val action = if (selectingOrigin) "Başlangıç" else "Varış"
                            Text("$action • ${candidate.stop.name} • ${candidate.distanceMeters} m")
                        }
                    }
                }

                if (origin != null && destination != null) {
                    Button(
                        onClick = { loadRoutes(origin!!, destination!!) },
                        enabled = !routeBusy,
                        modifier = Modifier.testTag("route-refresh"),
                    ) { Text(if (routeBusy) "Rota hesaplanıyor…" else "Rotaları yenile") }
                }
                routeStatus?.let { Text(it, modifier = Modifier.testTag("route-status")) }
                routeOptions.take(3).forEachIndexed { index, option ->
                    Text(
                        "${option.line} • ${option.direction} • ${option.departure} → ${option.arrival} • ${option.walkingMinutes} dk yürüme • ${option.transfers} aktarma",
                        modifier = Modifier.testTag("route-option-$index"),
                    )
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
                    onCheckedChange = {
                        inferenceEnabled = it
                        inferenceResult = null
                        confirmedTripId = null
                    },
                    modifier = Modifier.testTag("trip-inference-toggle"),
                )
                Text(
                    if (inferenceEnabled) "Açık • yalnız gerçek rota + cihaz konumundan öneri; kullanıcı onayı olmadan alarm/hedef kurulmaz"
                    else "Kapalı • otomatik sefer tahmini yapılmaz",
                    modifier = Modifier.testTag("trip-inference-status"),
                )
                Button(
                    onClick = {
                        val anchor = origin
                        if (anchor == null || routeOptions.isEmpty()) {
                            inferenceResult = TripInferenceResult(emptyList(), null, "Önce gerçek başlangıç ve rota gerekli")
                        } else {
                            currentLocation.request { location ->
                                inferenceResult = location.fold(
                                    onSuccess = { point ->
                                        val now = System.currentTimeMillis() / 1000
                                        val observation = MovementObservation(point, 0.0, null, now)
                                        val candidates = routeOptions.map { option ->
                                            TripCandidate(
                                                routeId = option.id,
                                                line = option.line,
                                                direction = option.direction,
                                                anchor = GeoPoint(anchor.latitude, anchor.longitude),
                                                expectedBearingDegrees = null,
                                                scheduledEpochSeconds = clockToEpoch(option.departure, now),
                                            )
                                        }
                                        TripInferenceEngine(TripInferenceSettings(enabled = true)).infer(observation, candidates)
                                    },
                                    onFailure = {
                                        TripInferenceResult(emptyList(), null, it.message ?: "Gerçek cihaz konumu alınamadı")
                                    },
                                )
                            }
                        }
                    },
                    enabled = inferenceEnabled && routeOptions.isNotEmpty() && origin != null,
                    modifier = Modifier.testTag("trip-inference-run"),
                ) { Text("Gerçek rotalardan sefer adayı hesapla") }
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

private fun clockToEpoch(clock: String, nowEpochSeconds: Long): Long {
    val hour = clock.take(2).toIntOrNull() ?: return nowEpochSeconds
    val minute = clock.drop(3).take(2).toIntOrNull() ?: return nowEpochSeconds
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowEpochSeconds * 1000
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis / 1000 < nowEpochSeconds - 2 * 60 * 60) add(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.timeInMillis / 1000
}

private fun formatEpoch(epochSeconds: Long): String =
    if (epochSeconds <= 0L) "zaman bilinmiyor"
    else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(epochSeconds * 1000))

@Preview(showBackground = true)
@Composable
private fun ArrivalAlarmPreview() {
    MaterialTheme { ArrivalAlarmApp() }
}
