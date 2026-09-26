package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var oauthCallbackUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthCallbackUri = intent?.dataString
        enableEdgeToEdge()
        setContent {
            val uiPreferences = remember { ArrivalUiPreferences(applicationContext) }
            var themeMode by remember { mutableStateOf(uiPreferences.loadThemeMode()) }
            ArrivalAlarmTheme(mode = themeMode) {
                ArrivalAlarmApp(
                    oauthCallbackUri = oauthCallbackUri,
                    onOAuthCallbackConsumed = {
                        oauthCallbackUri = null
                        setIntent(Intent(intent).setData(null))
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        uiPreferences.saveThemeMode(mode)
                        themeMode = mode
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oauthCallbackUri = intent.dataString
    }
}

@Composable
fun ArrivalAlarmApp(
    oauthCallbackUri: String? = null,
    onOAuthCallbackConsumed: () -> Unit = {},
    themeMode: ArrivalThemeMode = ArrivalThemeMode.SYSTEM,
    onThemeModeChange: (ArrivalThemeMode) -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val graph = remember(context) { ArrivalAlarmRuntimeGraph.get(context) }
    val controller = graph.controller
    val connectorPort = graph.connectorPort
    val connectorRuntimeCoordinator = graph.connectorRuntimeCoordinator
    val connectorOAuth = graph.connectorOAuth
    val connectorSessionStore = graph.connectorSessionStore
    val connectorBaseUrl = BuildConfig.CONNECTOR_BASE_URL.trim().trimEnd('/')
    val coroutineScope = rememberCoroutineScope()
    val transit = remember { NationwideTransitGateway(context) }
    val currentLocation = remember { AndroidCurrentLocation(context) }
    val appPreferences = remember(context) {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    }
    val searchRecentsPreferences = remember(context) {
        context.getSharedPreferences("search_recents", Context.MODE_PRIVATE)
    }
    val transitExperiencePreferences = remember(context) {
        context.getSharedPreferences("transit_experience", Context.MODE_PRIVATE)
    }
    val guidanceSpeaker = remember { AndroidTextToSpeechSpeaker(context) }
    val guidancePreferences = remember(context) {
        context.getSharedPreferences("guidance_state", Context.MODE_PRIVATE)
    }
    val guidanceController = remember {
        GuidanceReliabilityController(
            guidanceSpeaker,
            SharedPreferencesGuidanceStateStore(guidancePreferences),
        )
    }

    var journey by remember { mutableStateOf(controller.state) }
    var onboardingComplete by remember {
        mutableStateOf(appPreferences.getBoolean("onboarding_complete", false))
    }
    var activeScreen by rememberSaveable { mutableStateOf(ArrivalAppScreen.HOME) }
    var dataState by remember { mutableStateOf(transit.currentState()) }
    var query by remember { mutableStateOf("") }
    var lastSearchedQuery by remember { mutableStateOf("") }
    var recentSearches by remember {
        mutableStateOf(
            searchRecentsPreferences.getString("queries", "").orEmpty()
                .split('\u001F')
                .filter { it.isNotBlank() }
                .take(5)
        )
    }
    var favoriteStopIds by remember {
        mutableStateOf(
            transitExperiencePreferences.getStringSet("favorite_stop_ids", emptySet())
                .orEmpty()
                .toSet()
        )
    }
    var searchBusy by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<TransitLocationResult>()) }
    var searchSource by remember { mutableStateOf<String?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var searchRequestId by remember { mutableStateOf(0L) }
    var selectingOrigin by remember { mutableStateOf(controller.state.start == null) }
    var origin by remember(controller) {
        mutableStateOf(
            controller.state.start?.let {
                MapPoint(it.latitude, it.longitude, context.getString(R.string.restored_origin))
            }
        )
    }
    var destination by remember(controller) {
        mutableStateOf(
            controller.state.destination?.let {
                MapPoint(it.latitude, it.longitude, context.getString(R.string.restored_destination))
            }
        )
    }
    var destinationStopId by remember { mutableStateOf<String?>(null) }
    var nearby by remember { mutableStateOf(emptyList<NearbyStop>()) }
    var nearbySource by remember { mutableStateOf<String?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var mapMessage by remember { mutableStateOf<String?>(null) }
    var routeOptions by remember { mutableStateOf(emptyList<RouteOption>()) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }
    var routeStatus by remember { mutableStateOf<String?>(null) }
    var routeOffline by remember { mutableStateOf(false) }
    var routeBusy by remember { mutableStateOf(false) }
    var stationDepartures by remember { mutableStateOf(emptyList<Departure>()) }
    var stationAlerts by remember { mutableStateOf(emptyList<ServiceAlert>()) }
    var stationSource by remember { mutableStateOf<String?>(null) }
    var stationStatus by remember { mutableStateOf<String?>(null) }
    var stationLoading by remember { mutableStateOf(false) }
    var stationResolved by remember { mutableStateOf(false) }
    var liveNowEpochSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1_000) }
    var readinessRefreshTick by remember { mutableStateOf(0) }
    val routeCache = remember(context) {
        OfflineTransitCache(backingStore = SharedPreferencesTransitCacheStore(context))
    }
    var guidanceState by remember { mutableStateOf(guidanceController.state) }
    var guidanceLanguage by remember {
        mutableStateOf(
            GuidanceLanguage.fromLocaleTag(
                guidancePreferences.getString("preferred_locale", null),
            )
        )
    }
    var inferenceEnabled by remember { mutableStateOf(false) }
    var inferenceResult by remember { mutableStateOf<TripInferenceResult?>(null) }
    var confirmedTripId by remember { mutableStateOf<String?>(null) }
    var connectorSetupBusy by remember { mutableStateOf(false) }
    var connectorConnected by remember {
        mutableStateOf(connectorSessionStore.hasUsableSession())
    }
    var connectorSetupStatus by remember {
        mutableStateOf(
            when {
                connectorConnected -> context.getString(R.string.connector_ready)
                connectorBaseUrl.isBlank() -> context.getString(R.string.connector_url_missing)
                else -> context.getString(R.string.connector_not_connected)
            }
        )
    }

    fun connectorStatusText(status: ConnectorRuntimeStatus): String = when (status.state) {
        ConnectorRuntimeState.CONNECTED -> context.getString(R.string.connector_active)
        ConnectorRuntimeState.SYNCING -> context.getString(R.string.connector_syncing)
        ConnectorRuntimeState.AUTH_EXPIRED -> context.getString(R.string.connector_auth_expired)
        ConnectorRuntimeState.DISCONNECTED -> context.getString(R.string.connector_session_missing)
        ConnectorRuntimeState.ERROR -> context.getString(R.string.connector_sync_failed)
        ConnectorRuntimeState.STOPPED -> context.getString(R.string.connector_stopped)
    }

    fun beginConnectorAuthorization() {
        if (connectorBaseUrl.isBlank() || connectorSetupBusy) return
        connectorSetupBusy = true
        connectorSetupStatus = context.getString(R.string.connector_preparing)
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { connectorOAuth.beginAuthorization(connectorBaseUrl) }
            }
            result.onSuccess { authorizationUrl ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(browserIntent) }
                    .onSuccess {
                        connectorSetupStatus = context.getString(R.string.connector_finish_browser)
                    }
                    .onFailure {
                        connectorSetupStatus = context.getString(R.string.connector_browser_failed)
                    }
            }.onFailure {
                connectorSetupStatus = context.getString(R.string.connector_oauth_config_failed)
            }
            connectorSetupBusy = false
        }
    }

    fun disconnectConnector() {
        if (connectorSetupBusy) return
        connectorSetupBusy = true
        connectorSetupStatus = context.getString(R.string.connector_disconnecting)
        coroutineScope.launch {
            val remotelyRevoked = withContext(Dispatchers.IO) {
                connectorOAuth.disconnect()
            }
            connectorConnected = false
            connectorSetupStatus = if (remotelyRevoked) {
                context.getString(R.string.connector_revoked)
            } else {
                context.getString(R.string.connector_local_only_disconnect)
            }
            connectorSetupBusy = false
        }
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
        destinationStopId = null
        routeOptions = emptyList()
        selectedRouteId = null
        graph.routeRegistry.clear()
        routeStatus = null
        routeOffline = false
        stationDepartures = emptyList()
        stationAlerts = emptyList()
        stationSource = null
        stationStatus = null
        stationLoading = false
        stationResolved = false
        selectingOrigin = false
        connectorPort.setOrigin(point)
        journey = controller.state
        loadNearby(point)
    }

    fun routeKey(a: MapPoint, b: MapPoint) =
        "${a.latitude},${a.longitude}->${b.latitude},${b.longitude}"

    fun loadRoutes(a: MapPoint, b: MapPoint) {
        routeBusy = true
        routeStatus = context.getString(R.string.route_calculating)
        val key = routeKey(a, b)
        transit.journeyOptions(a, b) { options, status ->
            routeBusy = false
            if (options.isNotEmpty()) {
                routeOffline = false
                routeOptions = options
                if (selectedRouteId !in options.map { it.id }) {
                    selectedRouteId = null
                }
                graph.routeRegistry.replace(options)
                routeStatus = status
                routeCache.put(CachedTransitPlan(key, options, emptyList(), System.currentTimeMillis() / 1000))
            } else {
                val cached = routeCache.routeOptions(key)
                routeOffline = cached.isNotEmpty()
                routeOptions = cached
                if (selectedRouteId !in cached.map { it.id }) {
                    selectedRouteId = null
                }
                graph.routeRegistry.replace(cached)
                routeStatus = if (cached.isNotEmpty()) context.getString(R.string.route_offline_fallback) else status
            }
        }
    }

    fun loadStationDepartures(stop: CatalogStop) {
        stationLoading = true
        stationResolved = false
        stationDepartures = emptyList()
        stationAlerts = emptyList()
        stationSource = null
        stationStatus = null
        transit.stationDepartures(stop) { snapshot ->
            stationLoading = false
            if (snapshot.error == null) {
                stationResolved = true
                stationDepartures = snapshot.departures
                stationAlerts = snapshot.alerts
                stationSource = snapshot.sourceLabel
                stationStatus = null
            } else {
                stationResolved = false
                stationStatus = context.getString(R.string.departures_live_unavailable)
            }
        }
    }

    fun setDestinationPoint(point: MapPoint, stopId: String? = null): Boolean {
        val currentOrigin = origin
        if (currentOrigin == null) {
            setOrigin(point)
            return false
        }
        val outcome = connectorPort.setDestination(point)
        journey = controller.state
        if (!outcome.applied) {
            routeStatus = localizedDomainMessage(context, outcome.message)
            return false
        }
        destination = point
        destinationStopId = stopId
        routeOffline = false
        routeOptions = emptyList()
        selectedRouteId = null
        stationDepartures = emptyList()
        stationAlerts = emptyList()
        stationSource = null
        stationStatus = null
        stationLoading = false
        stationResolved = false
        graph.routeRegistry.clear()
        loadRoutes(currentOrigin, point)
        activeScreen = ArrivalAppScreen.ROUTES
        return true
    }

    fun setDestination(stop: CatalogStop) {
        if (
            setDestinationPoint(
                point = MapPoint(stop.latitude, stop.longitude, stop.name),
                stopId = stop.id,
            )
        ) {
            loadStationDepartures(stop)
        }
    }

    fun saveRecentSearch(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.length < 2) return
        val next = (listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) })
            .take(5)
        recentSearches = next
        searchRecentsPreferences.edit()
            .putString("queries", next.joinToString("\u001F"))
            .apply()
    }

    fun runSearch(rawQuery: String = query, recordRecent: Boolean = false) {
        val q = rawQuery.trim()
        if (q.length < 2) return
        if (recordRecent) saveRecentSearch(q)
        lastSearchedQuery = q
        val requestId = searchRequestId + 1
        searchRequestId = requestId
        searchBusy = true
        searchMessage = null
        transit.searchLocations(q) callback@{ values, source ->
            if (requestId != searchRequestId) return@callback
            searchBusy = false
            searchResults = values
            searchSource = source
            searchMessage = if (values.isEmpty()) context.getString(R.string.no_stop_found) else null
        }
    }

    fun useCurrentLocation() {
        locationMessage = context.getString(R.string.getting_location)
        currentLocation.request { result ->
            result.onSuccess { point ->
                locationMessage = null
                setOrigin(MapPoint(point.latitude, point.longitude, context.getString(R.string.current_location_label)))
            }.onFailure {
                locationMessage = localizedDomainMessage(context, it.message)
            }
        }
    }

    fun refreshReadiness() {
        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
        guidanceState = guidanceController.state
        readinessRefreshTick += 1
    }

    fun selectGuidanceLanguage(language: GuidanceLanguage) {
        guidanceLanguage = language
        guidancePreferences.edit()
            .putString("preferred_locale", language.localeTag)
            .apply()
    }

    fun armAndStartTracking() {
        val outcome = connectorPort.armArrivalAlarm()
        journey = controller.state
        if (!outcome.applied) {
            routeStatus = localizedDomainMessage(context, outcome.message)
            return
        }
        routeStatus = context.getString(R.string.alarm_active)
        activeScreen = ArrivalAppScreen.LIVE
    }

    fun selectRouteOption(option: RouteOption) {
        val outcome = connectorPort.selectJourney(option.id)
        journey = controller.state
        if (outcome.applied) {
            selectedRouteId = option.id
            liveNowEpochSeconds = System.currentTimeMillis() / 1_000
            routeStatus = context.getString(
                R.string.route_selected,
                option.line,
                option.direction,
            )
            activeScreen = ArrivalAppScreen.JOURNEY
        } else {
            routeStatus = localizedDomainMessage(context, outcome.message)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) useCurrentLocation()
        else locationMessage = context.getString(R.string.location_permission_denied)
    }

    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (ActiveJourneyPermissions.hasRequired(context)) {
            armAndStartTracking()
        } else {
            routeStatus = context.getString(R.string.alarm_permissions_required)
        }
    }

    val guidancePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshReadiness()
    }

    val readinessPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshReadiness()
    }

    LaunchedEffect(journey.phase, selectedRouteId) {
        liveNowEpochSeconds = System.currentTimeMillis() / 1_000
        if (journey.phase == JourneyPhase.ARMED) {
            while (controller.state.phase == JourneyPhase.ARMED) {
                delay(1_000)
                journey = controller.state
                guidanceState = guidanceController.state
                liveNowEpochSeconds = System.currentTimeMillis() / 1_000
            }
            journey = controller.state
            guidanceState = guidanceController.state
            liveNowEpochSeconds = System.currentTimeMillis() / 1_000
        }
    }

    LaunchedEffect(oauthCallbackUri) {
        val callback = oauthCallbackUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        connectorSetupBusy = true
        connectorSetupStatus = context.getString(R.string.connector_oauth_verifying)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                connectorOAuth.completeAuthorization(callback)
                connectorRuntimeCoordinator.syncNow()
            }
        }
        result.onSuccess { runtimeStatus ->
            connectorConnected = connectorSessionStore.hasUsableSession()
            connectorSetupStatus = connectorStatusText(runtimeStatus)
        }.onFailure {
            connectorConnected = connectorSessionStore.hasUsableSession()
            connectorSetupStatus = context.getString(R.string.connector_oauth_rejected)
        }
        connectorSetupBusy = false
        onOAuthCallbackConsumed()
    }

    LaunchedEffect(Unit) {
        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
        guidanceState = guidanceController.state

        val restoredOrigin = origin
        val restoredDestination = destination
        if (restoredOrigin != null) {
            loadNearby(restoredOrigin)
        }
        if (restoredOrigin != null && restoredDestination != null && routeOptions.isEmpty()) {
            val key = routeKey(restoredOrigin, restoredDestination)
            val cached = routeCache.routeOptions(key)
            if (cached.isNotEmpty()) {
                routeOffline = true
                routeOptions = cached
                graph.routeRegistry.replace(cached)
                routeStatus = context.getString(R.string.route_offline_fallback)
            } else {
                loadRoutes(restoredOrigin, restoredDestination)
            }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            journey = controller.state
            connectorConnected = connectorSessionStore.hasUsableSession()
            favoriteStopIds = transitExperiencePreferences
                .getStringSet("favorite_stop_ids", emptySet())
                .orEmpty()
                .toSet()
            delay(1_000)
        }
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
        if (q != lastSearchedQuery) {
            runSearch(q)
        }
    }

    DisposableEffect(connectorRuntimeCoordinator) {
        connectorRuntimeCoordinator.acquire("arrival-app-ui")
        onDispose { connectorRuntimeCoordinator.release("arrival-app-ui") }
    }

    DisposableEffect(Unit) {
        onDispose {
            guidanceSpeaker.shutdown()
            transit.close()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (onboardingComplete) {
                ArrivalBottomNavigation(
                    current = activeScreen,
                    onSelect = { activeScreen = it },
                )
            }
        },
    ) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .testTag("adaptive-content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.titleMedium)

                if (!onboardingComplete) {
                    FirstRunOnboarding(
                        onContinue = {
                            appPreferences.edit()
                                .putBoolean("onboarding_complete", true)
                                .apply()
                            onboardingComplete = true
                            activeScreen = ArrivalAppScreen.HOME
                        },
                    )
                }

                if (onboardingComplete) {
                val readinessState = remember(
                    readinessRefreshTick,
                    themeMode,
                    dataState,
                    guidanceState,
                    journey.phase,
                    connectorConnected,
                    connectorBaseUrl,
                ) {
                    SettingsReadinessState.from(
                        context = context,
                        themeMode = themeMode,
                        dataState = dataState,
                        guidanceState = guidanceState,
                        backgroundJourneyActive = journey.phase == JourneyPhase.ARMED,
                        connectorConfigured = connectorBaseUrl.isNotBlank(),
                        connectorConnected = connectorConnected,
                    )
                }
                if (activeScreen == ArrivalAppScreen.SETTINGS) {
                SettingsReadinessPanel(
                    state = readinessState,
                    connectorBusy = connectorSetupBusy,
                    connectorStatus = connectorSetupStatus,
                    onThemeModeChange = onThemeModeChange,
                    onRequestJourneyPermissions = {
                        readinessPermissionLauncher.launch(
                            ActiveJourneyPermissions.runtimePermissions()
                        )
                    },
                    onRequestGuidancePermissions = {
                        guidancePermissionLauncher.launch(
                            AndroidGuidancePermissions.runtimePermissions()
                        )
                    },
                    onRefreshReadiness = { refreshReadiness() },
                    onOpenAppSettings = { openArrivalAlarmAppSettings(context) },
                    onRetryData = {
                        transit.ensureNationwideIndex { dataState = it }
                    },
                    onConnectorAction = {
                        if (connectorConnected) disconnectConnector()
                        else beginConnectorAuthorization()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                }

                if (activeScreen == ArrivalAppScreen.HOME) {
                    HomeOverviewPanel(
                        origin = origin,
                        destination = destination,
                        nearby = nearby,
                        nearbySource = nearbySource,
                        favoriteStopIds = favoriteStopIds,
                        mapMessage = mapMessage,
                        onUseCurrentLocation = {
                            if (currentLocation.hasPermission()) {
                                useCurrentLocation()
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            }
                        },
                        onOpenSearch = { activeScreen = ArrivalAppScreen.SEARCH },
                        onOpenJourney = { activeScreen = ArrivalAppScreen.JOURNEY },
                        onMapStopSelected = { stop ->
                            if (origin == null) {
                                setOrigin(MapPoint(stop.latitude, stop.longitude, stop.name))
                            } else {
                                setDestination(stop)
                            }
                        },
                        onMapPointSelected = { point ->
                            if (origin == null) {
                                setOrigin(point)
                            } else {
                                setDestinationPoint(point)
                            }
                        },
                        onMapError = {
                            mapMessage = context.getString(R.string.map_load_failed)
                        },
                        onNearbyStopSelected = { candidate ->
                            if (origin == null) {
                                setOrigin(
                                    MapPoint(
                                        candidate.stop.latitude,
                                        candidate.stop.longitude,
                                        candidate.stop.name,
                                    )
                                )
                            } else {
                                setDestination(candidate.stop)
                            }
                        },
                    )
                }

                if (activeScreen == ArrivalAppScreen.SEARCH) {
                HomeSearchPanel(
                    selectingOrigin = selectingOrigin,
                    query = query,
                    searchBusy = searchBusy,
                    searchResults = searchResults,
                    searchSource = searchSource,
                    searchMessage = searchMessage,
                    locationMessage = locationMessage,
                    origin = origin,
                    destination = destination,
                    mapMessage = mapMessage,
                    nearby = nearby,
                    nearbySource = nearbySource,
                    recentSearches = recentSearches,
                    favoriteStopIds = favoriteStopIds,
                    onSelectOriginTarget = { selectingOrigin = true },
                    onSelectDestinationTarget = { selectingOrigin = false },
                    onQueryChange = {
                        query = it
                        searchRequestId += 1
                    },
                    onSearch = { runSearch(recordRecent = true) },
                    onSearchResultSelected = { result ->
                        if (selectingOrigin) {
                            setOrigin(result.point)
                        } else if (result.stop != null) {
                            setDestination(result.stop)
                        } else {
                            setDestinationPoint(result.point)
                        }
                    },
                    onRecentSearchSelected = { recent ->
                        query = recent
                        runSearch(recent, recordRecent = true)
                    },
                    onClearRecentSearches = {
                        recentSearches = emptyList()
                        searchRecentsPreferences.edit().remove("queries").apply()
                    },
                    onUseCurrentLocation = {
                        if (currentLocation.hasPermission()) {
                            useCurrentLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        }
                    },
                    onMapStopSelected = { stop ->
                        if (selectingOrigin) {
                            setOrigin(MapPoint(stop.latitude, stop.longitude, stop.name))
                        } else {
                            setDestination(stop)
                        }
                    },
                    onMapPointSelected = { point ->
                        if (selectingOrigin) {
                            setOrigin(point)
                        } else {
                            setDestinationPoint(point)
                        }
                    },
                    onMapError = {
                        mapMessage = context.getString(R.string.map_load_failed)
                    },
                    onNearbyStopSelected = { candidate ->
                        val point = MapPoint(
                            candidate.stop.latitude,
                            candidate.stop.longitude,
                            candidate.stop.name,
                        )
                        if (selectingOrigin) {
                            setOrigin(point)
                        } else {
                            setDestination(candidate.stop)
                        }
                    },
                )
                }

                if (activeScreen == ArrivalAppScreen.JOURNEY) {
                    if (origin == null || destination == null) {
                        ScreenEmptyState(
                            title = stringResource(R.string.journey_empty_title),
                            body = stringResource(R.string.journey_empty_body),
                            actionLabel = stringResource(R.string.journey_empty_action),
                            onAction = { activeScreen = ArrivalAppScreen.SEARCH },
                            testTag = "journey-empty-state",
                        )
                    } else {
                RouteJourneyPanel(
                    origin = origin,
                    destination = destination,
                    routeOptions = routeOptions,
                    routeBusy = routeBusy,
                    routeStatus = routeStatus,
                    routeOffline = routeOffline,
                    selectedRouteId = selectedRouteId,
                    journey = journey,
                    onRefreshRoutes = {
                        val from = origin
                        val to = destination
                        if (from != null && to != null) {
                            loadRoutes(from, to)
                        }
                    },
                    onSelectRoute = { option ->
                        selectRouteOption(option)
                    },
                    onArmAlarm = {
                        if (ActiveJourneyPermissions.hasRequired(context)) {
                            armAndStartTracking()
                        } else {
                            alarmPermissionLauncher.launch(
                                ActiveJourneyPermissions.runtimePermissions()
                            )
                        }
                    },
                    onCancelAlarm = {
                        val outcome = connectorPort.cancelArrivalAlarm()
                        journey = controller.state
                        if (outcome.applied) {
                            routeStatus = context.getString(R.string.alarm_cancelled)
                        } else {
                            routeStatus = localizedDomainMessage(context, outcome.message)
                        }
                    },
                )

                routeOptions.firstOrNull { it.id == selectedRouteId }?.let { selectedRoute ->
                    if (
                        journey.phase == JourneyPhase.DESTINATION_SELECTED ||
                        journey.phase == JourneyPhase.ARMED ||
                        journey.phase == JourneyPhase.ARRIVED
                    ) {
                        LiveTripPanel(
                            selectedRoute = selectedRoute,
                            journey = journey,
                            guidanceState = guidanceState,
                            routeOffline = routeOffline,
                            nowEpochSeconds = liveNowEpochSeconds,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                    }
                }

                if (activeScreen == ArrivalAppScreen.DEPARTURES) {
                val destinationPoint = destination
                if (routeOptions.isNotEmpty() && destinationPoint != null) {
                    val boardNow = System.currentTimeMillis() / 1_000
                    val departures = routeOptions.map { option ->
                        Departure(
                            tripId = option.tripIds.firstOrNull() ?: option.id,
                            line = option.line,
                            direction = option.direction,
                            scheduledEpochSeconds = clockToEpoch(option.departure, boardNow),
                            routeId = option.id,
                        )
                    }
                    val selectedRoute = routeOptions.firstOrNull { it.id == selectedRouteId }
                        ?: routeOptions.first()
                    val boardDepartures = if (stationResolved) stationDepartures else departures
                    val boardAlerts = if (stationResolved) stationAlerts else emptyList()
                    TransitExperiencePanel(
                        stopId = destinationStopId
                            ?: "coord:${destinationPoint.latitude},${destinationPoint.longitude}",
                        stopName = destinationPoint.label,
                        lineName = selectedRoute.line,
                        direction = selectedRoute.direction,
                        departures = boardDepartures,
                        alerts = boardAlerts,
                        isOfflineCache = !stationResolved && routeOffline,
                        nowEpochSeconds = boardNow,
                        providerCapabilities = TransitProviderCapabilities(
                            realtimeDepartures = stationResolved &&
                                stationDepartures.any { it.isRealtime },
                            serviceAlerts = stationResolved && stationAlerts.isNotEmpty(),
                        ),
                        onSelectJourney = { routeId ->
                            routeOptions.firstOrNull { it.id == routeId }
                                ?.let { selectRouteOption(it) }
                        },
                        statusMessage = stationStatus
                            ?: if (!stationResolved && !stationLoading) {
                                context.getString(R.string.departures_route_fallback)
                            } else null,
                        sourceLabel = if (stationResolved) stationSource else null,
                        loading = stationLoading,
                        modifier = Modifier.fillMaxWidth().testTag("transit-experience-panel"),
                    )
                } else {
                    ScreenEmptyState(
                        title = stringResource(R.string.departures_empty_title),
                        body = stringResource(R.string.departures_empty_body),
                        actionLabel = stringResource(R.string.departures_empty_action),
                        onAction = { activeScreen = ArrivalAppScreen.SEARCH },
                        testTag = "departures-empty-state",
                    )
                }
                }

                if (activeScreen == ArrivalAppScreen.SETTINGS) {
                Text(stringResource(R.string.guidance_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(guidanceStatusText(guidanceState), modifier = Modifier.testTag("guidance-status"))
                Text(stringResource(R.string.guidance_language_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.guidance_language_status, guidanceLanguage.displayName),
                    modifier = Modifier.testTag("guidance-language-status"),
                )
                GuidanceLanguage.values().forEach { language ->
                    Button(
                        onClick = { selectGuidanceLanguage(language) },
                        enabled = guidanceLanguage != language,
                        modifier = Modifier.fillMaxWidth().testTag("guidance-language-${language.localeTag}"),
                    ) {
                        Text(
                            if (guidanceLanguage == language) stringResource(R.string.guidance_language_selected, language.displayName)
                            else language.displayName
                        )
                    }
                }
                Button(
                    onClick = { guidancePermissionLauncher.launch(AndroidGuidancePermissions.runtimePermissions()) },
                    modifier = Modifier.testTag("guidance-permissions"),
                ) { Text(stringResource(R.string.guidance_permissions)) }
                Button(
                    onClick = {
                        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
                        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
                        guidanceState = guidanceController.state
                    },
                    modifier = Modifier.testTag("guidance-route-refresh"),
                ) { Text(stringResource(R.string.guidance_refresh_route)) }
                destination?.let { target ->
                    Button(
                        onClick = {
                            guidanceController.requestGuidance(
                                GuidanceUtterance(
                                    key = "destination:${target.label}:${guidanceLanguage.localeTag}",
                                    text = guidanceLanguage.destinationText(target.label),
                                    localeTag = guidanceLanguage.localeTag,
                                )
                            )
                            guidanceState = guidanceController.state
                        },
                        enabled = guidanceState.permissionState != GuidancePermissionState.DENIED,
                        modifier = Modifier.testTag("guidance-destination"),
                    ) { Text(stringResource(R.string.guidance_speak_destination)) }
                }

                val inferenceTitle = stringResource(R.string.inference_title)
                Text(inferenceTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Switch(
                    checked = inferenceEnabled,
                    onCheckedChange = {
                        inferenceEnabled = it
                        inferenceResult = null
                        confirmedTripId = null
                    },
                    modifier = Modifier
                        .semantics { contentDescription = inferenceTitle }
                        .testTag("trip-inference-toggle"),
                )
                Text(
                    if (inferenceEnabled) stringResource(R.string.inference_enabled)
                    else stringResource(R.string.inference_disabled),
                    modifier = Modifier.testTag("trip-inference-status"),
                )
                Button(
                    onClick = {
                        val anchor = origin
                        if (anchor == null || routeOptions.isEmpty()) {
                            inferenceResult = TripInferenceResult(emptyList(), null, context.getString(R.string.inference_prerequisite))
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
                                        TripInferenceResult(emptyList(), null, localizedDomainMessage(context, it.message))
                                    },
                                )
                            }
                        }
                    },
                    enabled = inferenceEnabled && routeOptions.isNotEmpty() && origin != null,
                    modifier = Modifier.testTag("trip-inference-run"),
                ) { Text(stringResource(R.string.inference_run)) }
                inferenceResult?.let { result ->
                    val accepted = result.accepted
                    if (accepted == null) {
                        Text(result.reason, modifier = Modifier.testTag("trip-inference-result"))
                    } else {
                        Text(
                            stringResource(R.string.inference_result, accepted.candidate.line, accepted.candidate.direction, (accepted.confidence * 100).toInt()),
                            modifier = Modifier.testTag("trip-inference-result"),
                        )
                        Text(accepted.explanation, modifier = Modifier.testTag("trip-inference-explanation"))
                        Button(
                            onClick = {
                                val selected = connectorPort.selectJourney(accepted.candidate.routeId)
                                if (selected.applied) {
                                    confirmedTripId = accepted.candidate.routeId
                                    journey = controller.state
                                } else {
                                    routeStatus = localizedDomainMessage(context, selected.message)
                                }
                            },
                            modifier = Modifier.testTag("trip-inference-confirm"),
                        ) { Text(stringResource(R.string.inference_confirm)) }
                    }
                }
                confirmedTripId?.let {
                    Text(
                        stringResource(R.string.inference_confirmed, it),
                        modifier = Modifier.testTag("trip-inference-confirmed"),
                    )
                }
                }
                }
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

internal fun localizedDomainMessage(context: Context, raw: String?): String {
    if (raw.isNullOrBlank()) return context.getString(R.string.unknown_error)
    return when (raw) {
        "Önce başlangıç seçilmeli" -> context.getString(R.string.error_start_required)
        "Başlangıç ve hedef gerekli" -> context.getString(R.string.error_start_destination_required)
        "İptal edilecek kurulu alarm yok" -> context.getString(R.string.error_no_alarm)
        "Rota artık mevcut değil; güncel rota seçilmeli" -> context.getString(R.string.error_route_stale)
        "Önce sefer seçilmeli" -> context.getString(R.string.error_trip_required)
        "Kurulu varış alarmı yok" -> context.getString(R.string.error_alarm_missing)
        "Konum izni gerekli" -> context.getString(R.string.location_permission_required)
        "Konum servisi kapalı" -> context.getString(R.string.location_service_off)
        "Güncel konum alınamadı" -> context.getString(R.string.current_location_unavailable)
        "Konum henüz hazır değil" -> context.getString(R.string.location_not_ready)
        else -> raw
    }
}

@Composable
private fun transitLocationKindText(kind: TransitLocationKind): String = stringResource(
    when (kind) {
        TransitLocationKind.STOP -> R.string.location_kind_stop
        TransitLocationKind.ADDRESS -> R.string.location_kind_address
        TransitLocationKind.POI -> R.string.location_kind_poi
    }
)

@Composable
internal fun journeyPhaseText(phase: JourneyPhase): String = stringResource(
    when (phase) {
        JourneyPhase.EMPTY -> R.string.phase_empty
        JourneyPhase.START_SELECTED -> R.string.phase_start_selected
        JourneyPhase.DESTINATION_SELECTED -> R.string.phase_destination_selected
        JourneyPhase.ARMED -> R.string.phase_armed
        JourneyPhase.ARRIVED -> R.string.phase_arrived
    }
)

@Composable
private fun guidanceStatusText(state: GuidanceReliabilityState): String = stringResource(
    when {
        state.pending != null && state.permissionState == GuidancePermissionState.DENIED -> R.string.guidance_pending
        state.pending != null -> R.string.guidance_tts_unavailable
        state.lastSpokenKey != null && state.audioRoute == GuidanceAudioRoute.BLUETOOTH_CONNECTED -> R.string.guidance_sent_bluetooth
        state.lastSpokenKey != null -> R.string.guidance_sent_device
        state.permissionState == GuidancePermissionState.DENIED -> R.string.guidance_permission_needed
        state.permissionState == GuidancePermissionState.PARTIAL -> R.string.guidance_partial
        state.audioRoute == GuidanceAudioRoute.BLUETOOTH_CONNECTED -> R.string.guidance_bluetooth
        else -> R.string.guidance_ready
    }
)

private fun formatEpoch(epochSeconds: Long): String =
    if (epochSeconds <= 0L) ""
    else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(epochSeconds * 1000))

@Preview(showBackground = true)
@Composable
private fun ArrivalAlarmPreview() {
    MaterialTheme { ArrivalAlarmApp() }
}