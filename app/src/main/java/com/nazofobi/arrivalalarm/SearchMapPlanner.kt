package com.nazofobi.arrivalalarm

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

sealed interface SearchResult {
    val label: String
    data class Stop(val value: CatalogStop) : SearchResult { override val label = value.name }
    data class Route(val value: CatalogRoute) : SearchResult { override val label = "${value.shortName} • ${value.direction}" }
    data class Trip(val value: CatalogTrip, val route: CatalogRoute) : SearchResult { override val label = "${route.shortName} • ${route.direction} • ${value.departure}" }
}

data class MapPoint(val latitude: Double, val longitude: Double, val label: String)
data class NearbyCandidate(val stop: CatalogStop, val distanceMeters: Int, val bearingDegrees: Int)

data class RouteStop(
    val id: String? = null,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val arrival: String? = null,
    val plannedArrival: String? = null,
    val departure: String? = null,
    val plannedDeparture: String? = null,
    val platform: String? = null,
    val plannedPlatform: String? = null,
)

/**
 * GTFS stop-backed walking access/egress estimates derived from straight-line
 * stop distance and nominal walking speed, not pedestrian path geometry.
 */
data class RouteWalkingLegEvidence(
    val stopId: String,
    val stopName: String,
    val walkSeconds: Int,
)

data class RouteOption(
    val id: String,
    val origin: MapPoint,
    val destination: MapPoint,
    val line: String,
    val direction: String,
    val departure: String,
    val arrival: String,
    val walkingMinutes: Int,
    val transfers: Int,
    val tripIds: List<String> = emptyList(),
    val stops: List<RouteStop> = emptyList(),
    val refreshToken: String? = null,
    val sourceUpdatedAtEpochSeconds: Long? = null,
    val realtime: RouteRealtimeInfo? = null,
    val departureEpochMillis: Long? = null,
    val arrivalEpochMillis: Long? = null,
    // Null means this provider did not supply separately verified walking legs.
    val accessWalking: RouteWalkingLegEvidence? = null,
    val egressWalking: RouteWalkingLegEvidence? = null,
)

interface GeocodeProvider { fun search(query: String): List<MapPoint> }
class EmptyGeocodeProvider : GeocodeProvider { override fun search(query: String) = emptyList<MapPoint>() }

class SearchMapPlanner(
    private val catalog: GermanyTransitCatalog,
    private val geocoder: GeocodeProvider = EmptyGeocodeProvider(),
) {
    private companion object {
        // This direct-stop helper does not calculate arbitrary long walking access legs.
        const val MAX_DIRECT_STOP_SNAP_METERS = 2_000
    }

    fun search(query: String, limit: Int = 20): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        val stops = catalog.searchStops(q, limit).map(SearchResult::Stop)
        val routes = catalog.searchRoutes(q, limit).map(SearchResult::Route)
        val trips = catalog.searchTrips(q, limit).map { (trip, route) -> SearchResult.Trip(trip, route) }
        // Display labels are not identities: different providers (and distinct stops
        // or trips within one provider) can legitimately share the same label.
        val distinct = (stops + routes + trips).distinctBy { result ->
            when (result) {
                is SearchResult.Stop -> Triple("stop", result.value.providerId, result.value.id)
                is SearchResult.Route -> Triple("route", result.value.providerId, result.value.id)
                is SearchResult.Trip -> Triple("trip", result.value.providerId, result.value.id)
            }
        }
        // Stop-heavy queries must not hide matching lines and departures.
        // Preserve the result cap and original ranking when it already fits.
        val firstPerKind = listOfNotNull(
            distinct.firstOrNull { it is SearchResult.Stop },
            distinct.firstOrNull { it is SearchResult.Route },
            distinct.firstOrNull { it is SearchResult.Trip },
        )
        if (distinct.size <= limit || firstPerKind.size > limit) return distinct.take(limit)
        return (firstPerKind + distinct.filterNot { it in firstPerKind }).take(limit)
    }

    fun nearby(point: MapPoint, limit: Int = 5): List<NearbyCandidate> =
        if (limit <= 0 || !hasValidCoordinates(point)) emptyList() else catalog.nearestStops(point.latitude, point.longitude, limit).map {
            NearbyCandidate(it.stop, it.distanceMeters, bearing(point.latitude, point.longitude, it.stop.latitude, it.stop.longitude))
        }

    fun mapMarkers(center: MapPoint, limit: Int = 20): List<MapPoint> = nearby(center, limit).map {
        MapPoint(it.stop.latitude, it.stop.longitude, it.stop.name)
    }

    fun addressOrPoi(query: String): List<MapPoint> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        // External geocoders must not inject invalid map coordinates into selection.
        return geocoder.search(q).filter(::hasValidCoordinates)
    }

    fun pointFor(stop: CatalogStop) = MapPoint(stop.latitude, stop.longitude, stop.name)
    fun arbitraryPoint(latitude: Double, longitude: Double, label: String = "Harita noktası") = MapPoint(latitude, longitude, label)

    fun routeOptions(origin: MapPoint, destination: MapPoint): List<RouteOption> {
        // NaN comparisons can bypass distance bounds and fabricate a nearby stop.
        if (!hasValidCoordinates(origin) || !hasValidCoordinates(destination)) return emptyList()
        val originNearby = catalog.nearestStops(origin.latitude, origin.longitude, 1).firstOrNull() ?: return emptyList()
        val destinationNearby = catalog.nearestStops(destination.latitude, destination.longitude, 1).firstOrNull() ?: return emptyList()
        // A nationwide nearest stop can be hundreds of kilometres away from a map pin.
        if (originNearby.distanceMeters > MAX_DIRECT_STOP_SNAP_METERS ||
            destinationNearby.distanceMeters > MAX_DIRECT_STOP_SNAP_METERS
        ) return emptyList()
        return catalog.routesServing(originNearby.stop, destinationNearby.stop).flatMap { route ->
            // A route shape is not a scheduled journey: never synthesize trip IDs or departures.
            val trips = catalog.tripsForRoute(route.providerId, route.id)
            trips.take(3).mapIndexed { index, trip ->
                RouteOption(
                    id = "${route.id}:${trip.id}", origin = origin, destination = destination,
                    line = route.shortName, direction = route.direction, departure = trip.departure,
                    arrival = if (trip.departure == "Planlı") "Planlı" else trip.departure,
                    walkingMinutes = if (index == 0) 4 else 6 + index, transfers = 0,
                )
            }
        }
    }

    private fun hasValidCoordinates(point: MapPoint): Boolean =
        point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toInt()
    }
}
