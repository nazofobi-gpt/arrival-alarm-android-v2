package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val JOURNEY_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/** Read-only route view. Only verified GTFS shapes are drawn; no invented walking paths. */
@Suppress("DEPRECATION")
@Composable
internal fun JourneyRouteShapeMap(
    polylines: List<List<MapPoint>>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = context as? LifecycleOwner
    val destroyed = remember { AtomicBoolean(false) }
    val strokeColor = MaterialTheme.colorScheme.primary.toArgb()
    val description = stringResource(R.string.map_title)
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    DisposableEffect(mapView, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> if (destroyed.compareAndSet(false, true)) mapView.onDestroy()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            if (destroyed.compareAndSet(false, true)) mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { ready ->
            if (!destroyed.get()) {
                map = ready
                ready.uiSettings.setAttributionEnabled(true)
                ready.setStyle(Style.Builder().fromUri(JOURNEY_MAP_STYLE)) {
                    if (!destroyed.get()) styleReady = true
                }
            }
        }
    }

    LaunchedEffect(map, styleReady, polylines, strokeColor) {
        val ready = map ?: return@LaunchedEffect
        if (!styleReady || destroyed.get()) return@LaunchedEffect
        ready.clear()
        val segments = polylines.map { segment ->
            segment.filter(::isRenderableMapPoint)
                .map { LatLng(it.latitude, it.longitude) }
        }.filter { it.size >= 2 }
        if (segments.isEmpty()) return@LaunchedEffect

        segments.forEach { segment ->
            ready.addPolyline(
                PolylineOptions().addAll(segment).color(strokeColor).width(5f)
            )
        }
        val boundsBuilder = LatLngBounds.Builder()
        segments.flatten().forEach(boundsBuilder::include)
        val bounds = boundsBuilder.build()
        val padding = (28f * mapView.resources.displayMetrics.density).roundToInt()
        mapView.post {
            if (!destroyed.get() && map === ready && styleReady &&
                mapView.width > 0 && mapView.height > 0
            ) ready.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxWidth().height(220.dp)
            .semantics { contentDescription = description }
            .testTag("journey-route-map"),
    )
}
