package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val OPEN_FREE_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Suppress("DEPRECATION")
@Composable
fun TransitSelectionMap(
    center: MapPoint,
    nearbyStops: List<NearbyStop>,
    origin: MapPoint?,
    destination: MapPoint?,
    mapPointLabel: String,
    onStopSelected: (CatalogStop) -> Unit,
    onMapPointSelected: (MapPoint) -> Unit,
    onMapError: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val currentStopSelected by rememberUpdatedState(onStopSelected)
    val currentMapPointSelected by rememberUpdatedState(onMapPointSelected)
    val currentMapError by rememberUpdatedState(onMapError)
    val currentNearby by rememberUpdatedState(nearbyStops)
    val currentMapPointLabel by rememberUpdatedState(mapPointLabel)
    val destroyed = remember { AtomicBoolean(false) }
    val markerStops = remember { LinkedHashMap<Long, CatalogStop>() }

    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
        }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    if (destroyed.compareAndSet(false, true)) mapView.onDestroy()
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            if (destroyed.compareAndSet(false, true)) mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { message ->
            currentMapError(message)
        }
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.setAttributionEnabled(true)
            readyMap.addOnMapClickListener { latLng ->
                currentMapPointSelected(
                    MapPoint(
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                        label = currentMapPointLabel,
                    )
                )
                true
            }
            readyMap.setOnMarkerClickListener { marker ->
                markerStops[marker.id]?.let { stop ->
                    currentStopSelected(stop)
                    true
                } ?: false
            }
            readyMap.setStyle(Style.Builder().fromUri(OPEN_FREE_MAP_STYLE)) {
                styleReady = true
            }
        }
    }

    LaunchedEffect(
        map,
        styleReady,
        center.latitude,
        center.longitude,
        nearbyStops,
        origin,
        destination,
    ) {
        val readyMap = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        readyMap.clear()
        markerStops.clear()

        currentNearby.filter { candidate ->
            isRenderableMapPoint(
                MapPoint(candidate.stop.latitude, candidate.stop.longitude, candidate.stop.name)
            )
        }.take(12).forEach { candidate ->
            val marker = readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(candidate.stop.latitude, candidate.stop.longitude))
                    .title(candidate.stop.name)
            )
            markerStops[marker.id] = candidate.stop
        }

        origin?.takeIf(::isRenderableMapPoint)?.let { point ->
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(point.latitude, point.longitude))
                    .title(point.label)
            )
        }
        destination?.takeIf(::isRenderableMapPoint)?.let { point ->
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(point.latitude, point.longitude))
                    .title(point.label)
            )
        }

        // Framing only the origin at a fixed zoom hides distant destinations.
        // Fit actual user-selected endpoints while keeping the map optional.
        val cameraTargets = selectionMapCameraTargets(center, origin, destination)
        when (cameraTargets.size) {
            0 -> Unit // Ignore invalid coordinates instead of crashing MapLibre.
            1 -> {
                val point = cameraTargets.single()
                readyMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(point.latitude, point.longitude))
                    .zoom(if (origin == null && destination == null) 5.5 else 13.0)
                    .build()
            }
            else -> {
                val boundsBuilder = LatLngBounds.Builder()
                cameraTargets.forEach { point ->
                    boundsBuilder.include(LatLng(point.latitude, point.longitude))
                }
                val bounds = boundsBuilder.build()
                val padding = (40f * mapView.resources.displayMetrics.density).roundToInt()
                // Bounds updates need a measured MapView; styles may finish early.
                mapView.post {
                    if (!destroyed.get() && map === readyMap && styleReady &&
                        mapView.width > 0 && mapView.height > 0
                    ) {
                        readyMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                    }
                }
            }
        }
    }

    var coordinateLatitude by remember { mutableStateOf("") }
    var coordinateLongitude by remember { mutableStateOf("") }
    val manualPoint = remember(coordinateLatitude, coordinateLongitude, currentMapPointLabel) {
        MapCoordinateInput.parse(coordinateLatitude, coordinateLongitude, currentMapPointLabel)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .semantics { contentDescription = currentMapPointLabel }
                .testTag("transit-map"),
        )
        Text(
            text = stringResource(R.string.map_accessible_coordinates_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = coordinateLatitude,
            onValueChange = { coordinateLatitude = it },
            label = { Text(stringResource(R.string.map_coordinate_latitude)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("map-coordinate-latitude"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = coordinateLongitude,
            onValueChange = { coordinateLongitude = it },
            label = { Text(stringResource(R.string.map_coordinate_longitude)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("map-coordinate-longitude"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if ((coordinateLatitude.isNotBlank() || coordinateLongitude.isNotBlank()) && manualPoint == null) {
            Text(
                text = stringResource(R.string.map_coordinate_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("map-coordinate-error"),
            )
        }
        Button(
            onClick = { manualPoint?.let(currentMapPointSelected) },
            enabled = manualPoint != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("map-coordinate-select"),
        ) {
            Text(stringResource(R.string.map_coordinate_select))
        }
    }
}
