package com.nazofobi.arrivalalarm

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
        fun point(prefix: String): GeoPoint? = if (prefs.contains("${prefix}_lat") && prefs.contains("${prefix}_lon")) GeoPoint(Double.fromBits(prefs.getLong("${prefix}_lat",0L)),Double.fromBits(prefs.getLong("${prefix}_lon",0L))) else null
        val distance=if(prefs.contains("distance")) Double.fromBits(prefs.getLong("distance",0L)) else null
        val phase=runCatching{JourneyPhase.valueOf(prefs.getString("phase",JourneyPhase.EMPTY.name)!!)}.getOrDefault(JourneyPhase.EMPTY)
        return JourneyUiState(phase,point("start"),point("destination"),distance)
    }
    override fun save(state: JourneyUiState){ prefs.edit().apply{ clear();putString("phase",state.phase.name);state.start?.let{putLong("start_lat",it.latitude.toBits());putLong("start_lon",it.longitude.toBits())};state.destination?.let{putLong("destination_lat",it.latitude.toBits());putLong("destination_lon",it.longitude.toBits())};state.distanceMeters?.let{putLong("distance",it.toBits())}}.apply() }
}

class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{MaterialTheme{ArrivalAlarmApp()}}} }

@Composable fun ArrivalAlarmApp(){
    val context=LocalContext.current.applicationContext
    val controller=remember{JourneyController(stateStore=SharedPreferencesJourneyStateStore(context))}
    val staticRepository=remember{FixtureStaticTransitRepository()}; val transitController=remember{TransitController(staticRepository)}
    val catalog=remember{GermanyTransitCatalog()}; val planner=remember{SearchMapPlanner(catalog)}
    val realtimeRepository=remember{OverlayTransitRepository(staticRepository,VbnJsonRealtimeSource(),EpochClock{System.currentTimeMillis()/1000})}
    var state by remember{mutableStateOf(controller.state)}; var transitState by remember{mutableStateOf(transitController.state)}; var realtime by remember{mutableStateOf<TransitRealtimeOverlay?>(null)}
    var query by remember{mutableStateOf("")}; var searchResults by remember{mutableStateOf(emptyList<SearchResult>())}; var origin by remember{mutableStateOf<MapPoint?>(null)}; var destinationPoint by remember{mutableStateOf<MapPoint?>(null)}; var nearby by remember{mutableStateOf(emptyList<NearbyCandidate>())}; var routeOptions by remember{mutableStateOf(emptyList<RouteOption>())}
    var inferenceEnabled by remember{mutableStateOf(false)}; var inferenceResult by remember{mutableStateOf<TripInferenceResult?>(null)}; var confirmedTripId by remember{mutableStateOf<String?>(null)}
    val guidanceSpeaker=remember{AndroidTextToSpeechSpeaker(context)}; val guidanceController=remember{GuidanceReliabilityController(guidanceSpeaker,SharedPreferencesGuidanceStateStore(context.getSharedPreferences("guidance_state",Context.MODE_PRIVATE)))}; var guidanceState by remember{mutableStateOf(guidanceController.state)}
    val guidancePermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context));guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context));guidanceState=guidanceController.state}
    LaunchedEffect(Unit){guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context));guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context));guidanceState=guidanceController.state}
    DisposableEffect(Unit){onDispose{guidanceSpeaker.shutdown()}}
    fun act(block:JourneyController.()->Unit){controller.block();state=controller.state}; fun transitAct(block:TransitController.()->Unit){transitController.block();transitState=transitController.state}
    fun refreshRoutes(){routeOptions=if(origin!=null&&destinationPoint!=null)planner.routeOptions(origin!!,destinationPoint!!) else emptyList()}

    Scaffold(modifier=Modifier.fillMaxSize()){innerPadding->Surface(Modifier.fillMaxSize().padding(innerPadding)){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Varış Alarmı",style=MaterialTheme.typography.headlineMedium)
        Text("Almanya transit arama",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(value=query,onValueChange={query=it;searchResults=planner.search(it)},label={Text("Durak, hat veya sefer ara")},modifier=Modifier.testTag("catalog-search"))
        searchResults.take(6).forEachIndexed{index,result->Button(onClick={when(result){is SearchResult.Stop->{val p=planner.pointFor(result.value);if(origin==null)origin=p else destinationPoint=p;nearby=planner.nearby(p);refreshRoutes()};is SearchResult.Route->query=result.label;is SearchResult.Trip->query=result.label}},modifier=Modifier.testTag("search-result-$index")){Text(result.label)}}
        Button(onClick={val p=planner.arbitraryPoint(53.0834,8.8137,"Mevcut konum (demo)");origin=p;nearby=planner.nearby(p);refreshRoutes()},modifier=Modifier.testTag("current-location-origin")){Text("Mevcut konumu başlangıç yap")}
        origin?.let{Text("Başlangıç: ${it.label}",modifier=Modifier.testTag("origin-label"))}; destinationPoint?.let{Text("Varış: ${it.label}",modifier=Modifier.testTag("destination-label"))}
        if(nearby.isNotEmpty()){Text("En yakın duraklar",style=MaterialTheme.typography.titleSmall);nearby.take(5).forEachIndexed{index,c->Button(onClick={destinationPoint=planner.pointFor(c.stop);refreshRoutes()},modifier=Modifier.testTag("nearby-stop-$index")){Text("${c.stop.name} • ${c.distanceMeters} m • ${c.bearingDegrees}°")}}}
        Button(onClick={val p=planner.arbitraryPoint(53.0531,8.7866,"Harita pini: Flughafen Bremen");destinationPoint=p;refreshRoutes()},modifier=Modifier.testTag("map-pin-destination")){Text("Harita pinini varış yap")}
        routeOptions.take(3).forEachIndexed{index,r->Text("${r.line} • ${r.direction} • ${r.departure} → ${r.arrival} • ${r.walkingMinutes} dk yürüme • ${r.transfers} aktarma",modifier=Modifier.testTag("route-option-$index"))}

        Text("Durum: ${state.phase}",modifier=Modifier.testTag("phase"));state.distanceMeters?.let{Text("Hedefe ${it.toInt()} m")};state.error?.let{Text(it,modifier=Modifier.testTag("error"))}
        Button(onClick={act{selectStart(GeoPoint(52.2689,10.5268))}},modifier=Modifier.testTag("start")){Text("Başlangıcı belirle")};Button(onClick={act{selectDestination(GeoPoint(52.2700,10.5300))}},modifier=Modifier.testTag("destination"),enabled=state.start!=null){Text("Hedefi seç")};Button(onClick={act{arm()}},modifier=Modifier.testTag("arm"),enabled=state.phase==JourneyPhase.DESTINATION_SELECTED){Text("Alarmı kur")};Button(onClick={act{onDistanceChanged(200.0)}},modifier=Modifier.testTag("approach"),enabled=state.phase==JourneyPhase.ARMED){Text("Test: hedefe yaklaş")}
        Text("Statik transit: ${transitState.loadState}",modifier=Modifier.testTag("transit-state"));Button(onClick={transitAct{search("Flughafen")}},modifier=Modifier.testTag("search-airport")){Text("Durak ara: Flughafen")}
        if(transitState.matches.isNotEmpty()){Text(transitState.matches.joinToString{it.name},modifier=Modifier.testTag("stop-results"));Button(onClick={transitAct{planFromFixtureStart("fixture-airport")};realtime=realtimeRepository.plan("fixture-bremen-hbf","fixture-airport")?.realtime},modifier=Modifier.testTag("plan-airport")){Text("Bremen Hbf → Flughafen rotası")}}
        transitState.journey?.let{journey->val leg=journey.legs.single();Text("${leg.line} • ${leg.direction}",modifier=Modifier.testTag("itinerary-line"));Text(leg.stops.joinToString(" → "){it.name},modifier=Modifier.testTag("itinerary-stops"));Text(transitState.message.orEmpty(),modifier=Modifier.testTag("transit-message"));Text(realtime?.status?:"Canlı veri bekleniyor • statik rota",modifier=Modifier.testTag("realtime-status"))}
        if(transitState.loadState==TransitLoadState.EMPTY||transitState.loadState==TransitLoadState.DEGRADED)Text(transitState.message.orEmpty(),modifier=Modifier.testTag("transit-message"))
        Text("Sesli yönlendirme güvenilirliği",style=MaterialTheme.typography.titleMedium);Text(guidanceState.status,modifier=Modifier.testTag("guidance-status"));Button(onClick={guidancePermissionLauncher.launch(AndroidGuidancePermissions.runtimePermissions())},modifier=Modifier.testTag("guidance-permissions")){Text("Guidance izinlerini kontrol et")};Button(onClick={guidanceController.onPermissions(AndroidGuidancePermissions.snapshot(context));guidanceController.onAudioRoute(AndroidGuidanceAudio.currentRoute(context));guidanceState=guidanceController.state},modifier=Modifier.testTag("guidance-route-refresh")){Text("Ses rotasını yenile")}
        transitState.journey?.let{journey->val stops=journey.legs.single().stops;val next=stops.getOrNull(1)?:stops.last();val destination=stops.last();Button(onClick={guidanceController.requestGuidance(GuidanceUtterance("stop:${next.id}","Sıradaki durak ${next.name}","tr-TR"));guidanceState=guidanceController.state},enabled=guidanceState.permissionState!=GuidancePermissionState.DENIED,modifier=Modifier.testTag("guidance-next-stop")){Text("Sıradaki durağı seslendir")};Button(onClick={guidanceController.requestGuidance(GuidanceUtterance("destination:${destination.id}","Hedef ${destination.name}","tr-TR"));guidanceState=guidanceController.state},enabled=guidanceState.permissionState!=GuidancePermissionState.DENIED,modifier=Modifier.testTag("guidance-destination")){Text("Hedefi seslendir")}}
        Text("Sefer algılama (isteğe bağlı)",style=MaterialTheme.typography.titleMedium);Text(if(inferenceEnabled)"Açık • öneri hiçbir zaman alarmı otomatik kurmaz" else "Kapalı • konumdan sefer tahmini yapılmaz",modifier=Modifier.testTag("trip-inference-status"));Switch(checked=inferenceEnabled,onCheckedChange={inferenceEnabled=it;inferenceResult=null;confirmedTripId=null},modifier=Modifier.testTag("trip-inference-toggle"))
        Button(onClick={val now=System.currentTimeMillis()/1000;val observation=MovementObservation(GeoPoint(53.0831,8.8131),8.0,180.0,now);val candidates=listOf(TripCandidate("fixture-6-south","6","Flughafen Bremen",GeoPoint(53.0830,8.8130),180.0,now,realtime?.delaySeconds),TripCandidate("fixture-6-north","6","Universität",GeoPoint(53.0830,8.8130),0.0,now+600));inferenceResult=TripInferenceEngine(TripInferenceSettings(enabled=inferenceEnabled)).infer(observation,candidates)},enabled=inferenceEnabled,modifier=Modifier.testTag("trip-inference-run")){Text("Sefer adayı hesapla")}
        inferenceResult?.let{result->val accepted=result.accepted;if(accepted==null)Text(result.reason,modifier=Modifier.testTag("trip-inference-result")) else {Text("${accepted.candidate.line} • ${accepted.candidate.direction} • güven %${(accepted.confidence*100).toInt()}",modifier=Modifier.testTag("trip-inference-result"));Text(accepted.explanation,modifier=Modifier.testTag("trip-inference-explanation"));Button(onClick={confirmedTripId=accepted.candidate.routeId},modifier=Modifier.testTag("trip-inference-confirm")){Text("Bu seferdeyim")}}};confirmedTripId?.let{Text("Sefer onaylandı: $it • hedef/alarm ayrıca kullanıcı tarafından seçilmeli",modifier=Modifier.testTag("trip-inference-confirmed"))}
    }}}}
}

@Preview(showBackground=true) @Composable private fun ArrivalAlarmPreview(){MaterialTheme{ArrivalAlarmApp()}}
