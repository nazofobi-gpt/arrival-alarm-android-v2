package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
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

        currentNearby.take(12).forEach { candidate ->
            val marker = readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(candidate.stop.latitude, candidate.stop.longitude))
                    .title(candidate.stop.name)
            )
            markerStops[marker.id] = candidate.stop
        }

        origin?.let { point ->
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(point.latitude, point.longitude))
                    .title(point.label)
            )
        }
        destination?.let { point ->
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(point.latitude, point.longitude))
                    .title(point.label)
            )
        }

        readyMap.cameraPosition = CameraPosition.Builder()
            .target(LatLng(center.latitude, center.longitude))
            .zoom(if (origin == null && destination == null) 5.5 else 13.0)
            .build()
    }

    var coordinateLatitude by remember { mutableStateOf("") }
    var coordinateLongitude by remember { mutableStateOf("") }
    val coordinatePoint = MapCoordinateInput.parse(
        coordinateLatitude,
        coordinateLongitude,
        mapPointLabel,
    )

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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = coordinateLatitude,
            onValueChange = { coordinateLatitude = it },
            label = { Text(stringResource(R.string.map_coordinate_latitude)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("map-coordinate-latitude"),
        )
        OutlinedTextField(
            value = coordinateLongitude,
            onValueChange = { coordinateLongitude = it },
            label = { Text(stringResource(R.string.map_coordinate_longitude)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("map-coordinate-longitude"),
        )
        if (coordinateLatitude.isNotBlank() &&
            coordinateLongitude.isNotBlank() &&
            coordinatePoint == null
        ) {
            Text(
                text = stringResource(R.string.map_coordinate_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("map-coordinate-error"),
            )
        }
        Button(
            onClick = { coordinatePoint?.let(currentMapPointSelected) },
            enabled = coordinatePoint != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("map-coordinate-select"),
        ) {
            Text(stringResource(R.string.map_coordinate_select))
        }
    }
}
