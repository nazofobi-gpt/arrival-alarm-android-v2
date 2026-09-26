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
)

interface GeocodeProvider { fun search(query: String): List<MapPoint> }
class EmptyGeocodeProvider : GeocodeProvider { override fun search(query: String) = emptyList<MapPoint>() }

class SearchMapPlanner(
    private val catalog: GermanyTransitCatalog,
    private val geocoder: GeocodeProvider = EmptyGeocodeProvider(),
) {
    fun search(query: String, limit: Int = 20): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val stops = catalog.searchStops(q, limit).map(SearchResult::Stop)
        val routes = catalog.searchRoutes(q, limit).map(SearchResult::Route)
        val trips = catalog.searchTrips(q, limit).map { (trip, route) -> SearchResult.Trip(trip, route) }
        return (stops + routes + trips).distinctBy { it.label }.take(limit)
    }

    fun nearby(point: MapPoint, limit: Int = 5): List<NearbyCandidate> =
        catalog.nearestStops(point.latitude, point.longitude, limit).map {
            NearbyCandidate(it.stop, it.distanceMeters, bearing(point.latitude, point.longitude, it.stop.latitude, it.stop.longitude))
        }

    fun mapMarkers(center: MapPoint, limit: Int = 20): List<MapPoint> = nearby(center, limit).map {
        MapPoint(it.stop.latitude, it.stop.longitude, it.stop.name)
    }

    fun addressOrPoi(query: String): List<MapPoint> = geocoder.search(query.trim())

    fun pointFor(stop: CatalogStop) = MapPoint(stop.latitude, stop.longitude, stop.name)
    fun arbitraryPoint(latitude: Double, longitude: Double, label: String = "Harita noktası") = MapPoint(latitude, longitude, label)

    fun routeOptions(origin: MapPoint, destination: MapPoint): List<RouteOption> {
        val originStop = catalog.nearestStops(origin.latitude, origin.longitude, 1).firstOrNull()?.stop ?: return emptyList()
        val destinationStop = catalog.nearestStops(destination.latitude, destination.longitude, 1).firstOrNull()?.stop ?: return emptyList()
        return catalog.routesServing(originStop.id, destinationStop.id).flatMap { route ->
            val trips = catalog.tripsForRoute(route.id).ifEmpty { listOf(CatalogTrip("static-${route.id}", route.providerId, route.id, "Planlı")) }
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

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toInt()
    }
}
