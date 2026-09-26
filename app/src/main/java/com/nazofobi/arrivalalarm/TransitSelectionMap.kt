package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

private const val OPEN_FREE_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun TransitSelectionMap(
    center: MapPoint,
    nearbyStops: List<NearbyStop>,
    origin: MapPoint?,
    destination: MapPoint?,
    mapPointLabel: String,
    onStopSelected: (CatalogStop) -> Unit,
    onMapPointSelected: (MapPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val targetZoom = if (origin == null && destination == null) 5.5 else 13.0

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(OPEN_FREE_MAP_STYLE),
        initialCameraPosition = CameraPosition(
            target = center.toMapPosition(),
            zoom = targetZoom,
        ),
    ) {
        nearbyStops.take(12).forEachIndexed { index, candidate ->
            key(candidate.stop.id) {
                val source = rememberGeoJsonSource(
                    GeoJsonData.Features(
                        Feature(
                            geometry = Point(
                                Position(
                                    latitude = candidate.stop.latitude,
                                    longitude = candidate.stop.longitude,
                                )
                            ),
                            properties = null,
                        )
                    ),
                    options = GeoJsonOptions(synchronousUpdate = true),
                )
                CircleLayer(
                    id = "nearby-stop-marker-$index",
                    source = source,
                    radius = const(7.dp),
                    color = const(colors.secondary),
                    strokeColor = const(colors.surface),
                    strokeWidth = const(2.dp),
                    hitPadding = 12.dp,
                    onClick = {
                        onStopSelected(candidate.stop)
                        ClickResult.Consume
                    },
                )
            }
        }

        origin?.let { point ->
            val source = rememberGeoJsonSource(
                GeoJsonData.Features(
                    Feature(geometry = Point(point.toMapPosition()), properties = null)
                ),
                options = GeoJsonOptions(synchronousUpdate = true),
            )
            CircleLayer(
                id = "origin-marker",
                source = source,
                radius = const(9.dp),
                color = const(colors.primary),
                strokeColor = const(colors.surface),
                strokeWidth = const(3.dp),
            )
        }

        destination?.let { point ->
            val source = rememberGeoJsonSource(
                GeoJsonData.Features(
                    Feature(geometry = Point(point.toMapPosition()), properties = null)
                ),
                options = GeoJsonOptions(synchronousUpdate = true),
            )
            CircleLayer(
                id = "destination-marker",
                source = source,
                radius = const(9.dp),
                color = const(colors.tertiary),
                strokeColor = const(colors.surface),
                strokeWidth = const(3.dp),
            )
        }
    }

    LaunchedEffect(center.latitude, center.longitude, targetZoom) {
        mapState.setCameraPosition(
            CameraPosition(
                target = center.toMapPosition(),
                zoom = targetZoom,
            )
        )
    }

    MaplibreMap(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .testTag("transit-map"),
        state = mapState,
        interactions = MapInteractions {
            callbacks {
                click {
                    onUnhandled { event ->
                        event.position?.let { position ->
                            onMapPointSelected(
                                MapPoint(
                                    latitude = position.latitude,
                                    longitude = position.longitude,
                                    label = mapPointLabel,
                                )
                            )
                        }
                        ClickResult.Consume
                    }
                }
            }
        },
    )
}

private fun MapPoint.toMapPosition(): Position =
    Position(latitude = latitude, longitude = longitude)
