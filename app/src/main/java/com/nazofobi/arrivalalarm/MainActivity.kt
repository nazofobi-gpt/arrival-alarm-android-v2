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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
            MaterialTheme {
                ArrivalAlarmApp(
                    oauthCallbackUri = oauthCallbackUri,
                    onOAuthCallbackConsumed = {
                        oauthCallbackUri = null
                        setIntent(Intent(intent).setData(null))
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
    var destinationStopId by remember { mutableStateOf<String?>(null) }
    var nearby by remember { mutableStateOf(emptyList<NearbyStop>()) }
    var nearbySource by remember { mutableStateOf<String?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var routeOptions by remember { mutableStateOf(emptyList<RouteOption>()) }
    var routeStatus by remember { mutableStateOf<String?>(null) }
    var routeBusy by remember { mutableStateOf(false) }
    val routeCache = remember { OfflineTransitCache() }
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
        graph.routeRegistry.clear()
        routeStatus = null
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
                routeOptions = options
                graph.routeRegistry.replace(options)
                routeStatus = status
                routeCache.put(CachedTransitPlan(key, options, emptyList(), System.currentTimeMillis() / 1000))
            } else {
                val cached = routeCache.routeOptions(key)
                routeOptions = cached
                graph.routeRegistry.replace(cached)
                routeStatus = if (cached.isNotEmpty()) context.getString(R.string.route_offline_fallback) else status
            }
        }
    }

    fun setDestination(stop: CatalogStop) {
        val point = MapPoint(stop.latitude, stop.longitude, stop.name)
        val currentOrigin = origin
        if (currentOrigin == null) {
            setOrigin(point)
        } else {
            val outcome = connectorPort.setDestination(point)
            journey = controller.state
            if (!outcome.applied) {
                routeStatus = localizedDomainMessage(context, outcome.message)
                return
            }
            destination = point
            destinationStopId = stop.id
            routeOptions = emptyList()
            graph.routeRegistry.clear()
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
            routeStatus = outcome.message
            return
        }
        routeStatus = context.getString(R.string.alarm_active)
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
        guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context))
        guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context))
        guidanceState = guidanceController.state
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
    }

    LaunchedEffect(controller) {
        while (true) {
            journey = controller.state
            connectorConnected = connectorSessionStore.hasUsableSession()
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
        runSearch(q)
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

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.titleMedium)

                if (!onboardingComplete) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("first-run-onboarding"),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.onboarding_steps))
                            Text(stringResource(R.string.onboarding_permission_note))
                            Text(
                                stringResource(R.string.onboarding_background_note),
                                modifier = Modifier.testTag("onboarding-privacy-note"),
                            )
                            Button(
                                onClick = {
                                    appPreferences.edit()
                                        .putBoolean("onboarding_complete", true)
                                        .apply()
                                    onboardingComplete = true
                                },
                                modifier = Modifier.testTag("onboarding-complete"),
                            ) {
                                Text(stringResource(R.string.continue_action))
                            }
                        }
                    }
                }

                Text(stringResource(R.string.connector_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    connectorSetupStatus,
                    modifier = Modifier.testTag("connector-setup-status"),
                )
                if (connectorConnected) {
                    Button(
                        onClick = { disconnectConnector() },
                        enabled = !connectorSetupBusy,
                        modifier = Modifier.testTag("connector-disconnect"),
                    ) {
                        Text(if (connectorSetupBusy) stringResource(R.string.processing) else stringResource(R.string.disconnect_chatgpt))
                    }
                } else {
                    Button(
                        onClick = { beginConnectorAuthorization() },
                        enabled = !connectorSetupBusy && connectorBaseUrl.isNotBlank(),
                        modifier = Modifier.testTag("connector-connect"),
                    ) {
                        Text(if (connectorSetupBusy) stringResource(R.string.processing) else stringResource(R.string.connect_chatgpt))
                    }
                }

                val dataText = when (val value = dataState) {
                    NationwideDataState.Idle -> stringResource(R.string.data_idle)
                    is NationwideDataState.Loading -> value.message
                    is NationwideDataState.Ready -> stringResource(R.string.data_ready, value.stopCount, value.sourceVersion, formatEpoch(value.fetchedAtEpochSeconds))
                    is NationwideDataState.Error -> stringResource(R.string.data_error, value.message)
                }
                Text(dataText, modifier = Modifier.testTag("nationwide-data-state"))
                if (dataState !is NationwideDataState.Ready && dataState !is NationwideDataState.Loading) {
                    Button(
                        onClick = { transit.ensureNationwideIndex { dataState = it } },
                        modifier = Modifier.testTag("nationwide-index-download"),
                    ) { Text(stringResource(R.string.download_germany_index)) }
                }

                Text(
                    if (selectingOrigin) stringResource(R.string.search_target_origin_status)
                    else stringResource(R.string.search_target_destination_status),
                    modifier = Modifier.testTag("search-target-status"),
                )
                Button(
                    onClick = { selectingOrigin = true },
                    enabled = !selectingOrigin,
                    modifier = Modifier.testTag("search-target-origin"),
                ) { Text(if (selectingOrigin) stringResource(R.string.selecting_origin) else stringResource(R.string.select_origin)) }
                Button(
                    onClick = { selectingOrigin = false },
                    enabled = origin != null && selectingOrigin,
                    modifier = Modifier.testTag("search-target-destination"),
                ) { Text(if (!selectingOrigin) stringResource(R.string.selecting_destination) else stringResource(R.string.select_destination)) }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        searchRequestId += 1
                    },
                    label = { Text(stringResource(R.string.search_stops_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("catalog-search"),
                    singleLine = true,
                )
                Button(
                    onClick = { runSearch() },
                    enabled = !searchBusy && query.trim().length >= 2,
                    modifier = Modifier.testTag("catalog-search-submit"),
                ) { Text(if (searchBusy) stringResource(R.string.searching) else stringResource(R.string.search_action)) }

                searchSource?.let { Text(stringResource(R.string.source_format, it), modifier = Modifier.testTag("search-source")) }
                searchMessage?.let { Text(it, modifier = Modifier.testTag("search-message")) }

                searchResults.take(12).forEachIndexed { index, stop ->
                    Button(
                        onClick = {
                            val point = MapPoint(stop.latitude, stop.longitude, stop.name)
                            if (selectingOrigin) setOrigin(point) else setDestination(stop)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("search-result-$index"),
                    ) {
                        val action = if (selectingOrigin) stringResource(R.string.select_origin) else stringResource(R.string.select_destination)
                        Text(stringResource(R.string.search_result_format, action, stop.name))
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
                ) { Text(stringResource(R.string.current_location_origin)) }
                locationMessage?.let { Text(it, modifier = Modifier.testTag("location-status")) }

                origin?.let {
                    Text(stringResource(R.string.origin_format, it.label), modifier = Modifier.testTag("origin-label"))
                }
                destination?.let {
                    Text(stringResource(R.string.destination_format, it.label), modifier = Modifier.testTag("destination-label"))
                }

                if (nearby.isNotEmpty()) {
                    Text(stringResource(R.string.nearby_stops), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    nearbySource?.let { Text(stringResource(R.string.source_format, it)) }
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
                            val action = if (selectingOrigin) stringResource(R.string.select_origin) else stringResource(R.string.select_destination)
                            Text(stringResource(R.string.nearby_stop_format, action, candidate.stop.name, candidate.distanceMeters))
                        }
                    }
                }

                if (origin != null && destination != null) {
                    Button(
                        onClick = { loadRoutes(origin!!, destination!!) },
                        enabled = !routeBusy,
                        modifier = Modifier.testTag("route-refresh"),
                    ) { Text(if (routeBusy) stringResource(R.string.route_calculating) else stringResource(R.string.refresh_routes)) }
                }
                routeStatus?.let { Text(it, modifier = Modifier.testTag("route-status")) }
                routeOptions.take(3).forEachIndexed { index, option ->
                    Text(
                        stringResource(R.string.route_option_format, option.line, option.direction, option.departure, option.arrival, option.walkingMinutes, option.transfers),
                        modifier = Modifier.testTag("route-option-$index"),
                    )
                }
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
                    val firstRoute = routeOptions.first()
                    TransitExperiencePanel(
                        stopId = destinationStopId
                            ?: "coord:${destinationPoint.latitude},${destinationPoint.longitude}",
                        stopName = destinationPoint.label,
                        lineName = firstRoute.line,
                        direction = firstRoute.direction,
                        departures = departures,
                        alerts = emptyList(),
                        isOfflineCache = routeStatus?.contains("çevrimdışı", ignoreCase = true) == true,
                        nowEpochSeconds = boardNow,
                        providerCapabilities = TransitProviderCapabilities(
                            realtimeDepartures = false,
                            serviceAlerts = false,
                        ),
                        onSelectJourney = { routeId ->
                            val outcome = connectorPort.selectJourney(routeId)
                            journey = controller.state
                            val selectedRoute = routeOptions.firstOrNull { it.id == routeId }
                            routeStatus = if (outcome.applied && selectedRoute != null) {
                                context.getString(R.string.route_selected, selectedRoute.line, selectedRoute.direction)
                            } else {
                                outcome.message
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("transit-experience-panel"),
                    )
                }

                Text(stringResource(R.string.phase_format, journeyPhaseText(journey.phase)), modifier = Modifier.testTag("phase"))
                journey.error?.let { Text(localizedDomainMessage(context, it), modifier = Modifier.testTag("error")) }
                Button(
                    onClick = {
                        if (ActiveJourneyPermissions.hasRequired(context)) {
                            armAndStartTracking()
                        } else {
                            alarmPermissionLauncher.launch(ActiveJourneyPermissions.runtimePermissions())
                        }
                    },
                    enabled = journey.phase == JourneyPhase.DESTINATION_SELECTED,
                    modifier = Modifier.testTag("arm"),
                ) { Text(stringResource(R.string.arm_alarm)) }
                Button(
                    onClick = {
                        val outcome = connectorPort.cancelArrivalAlarm()
                        journey = controller.state
                        if (outcome.applied) {
                            routeStatus = context.getString(R.string.alarm_cancelled)
                        } else {
                            routeStatus = outcome.message
                        }
                    },
                    enabled = journey.phase == JourneyPhase.ARMED || journey.phase == JourneyPhase.ARRIVED,
                    modifier = Modifier.testTag("cancel-alarm"),
                ) { Text(stringResource(R.string.cancel_alarm)) }

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

                Text(stringResource(R.string.inference_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
                                    routeStatus = selected.message
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

private fun localizedDomainMessage(context: Context, raw: String?): String {
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
private fun journeyPhaseText(phase: JourneyPhase): String = stringResource(
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
