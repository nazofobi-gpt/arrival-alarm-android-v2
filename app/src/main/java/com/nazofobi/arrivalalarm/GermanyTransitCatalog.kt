package com.nazofobi.arrivalalarm

import kotlin.math.*

data class TransitProvider(val id:String,val name:String,val coverage:String,val staticSource:String,val license:String,val sourceVersion:String,val fetchedAt:String,val realtimeCapability:Boolean,val realtimeSource:String?=null)
data class CatalogStop(
    val id: String,
    val providerId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val parentStationId: String? = null,
)
data class CatalogRoute(val id:String,val providerId:String,val shortName:String,val direction:String,val stopIds:List<String>)
data class CatalogTrip(val id:String,val providerId:String,val routeId:String,val departure:String)
data class NearbyStop(
    val stop: CatalogStop,
    val distanceMeters: Int,
    val bearingDegrees: Int? = null,
    val childStops: List<CatalogStop> = emptyList(),
) {
    val directionArrow: String
        get() = bearingDegrees?.let { bearing ->
            when (((bearing % 360) + 360) % 360) {
                in 23..67 -> "↗"
                in 68..112 -> "→"
                in 113..157 -> "↘"
                in 158..202 -> "↓"
                in 203..247 -> "↙"
                in 248..292 -> "←"
                in 293..337 -> "↖"
                else -> "↑"
            }
        }.orEmpty()
}
data class CatalogSnapshot(val providers:List<TransitProvider>,val stops:List<CatalogStop>,val routes:List<CatalogRoute>,val trips:List<CatalogTrip>,val generatedAt:String,val stale:Boolean=false)

/**
 * In-memory catalog used by deterministic unit fixtures and provider adapters.
 * There is deliberately no fixture default: production must opt into a real data source.
 */
class GermanyTransitCatalog(private val snapshot: CatalogSnapshot) {
    // Preserve the first route when a malformed feed has duplicate route IDs.
    // Use provider + route ID so unrelated feeds cannot cross-associate trips.
    // Lookup once rather than scanning every route for every trip search result.
    private val routesByProviderAndId = snapshot.routes.asReversed().associateBy { it.providerId to it.id }

    fun searchStops(query:String,limit:Int=20):List<CatalogStop> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        return snapshot.stops.asSequence().filter { it.name.contains(q, true) }
            .distinctBy { it.providerId to it.id }
            .take(limit).toList()
    }

    fun searchRoutes(query:String,limit:Int=20):List<CatalogRoute> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        return snapshot.routes.asSequence().filter {
            it.shortName.contains(q, true) || it.direction.contains(q, true)
        }.distinctBy { it.providerId to it.id }.take(limit).toList()
    }

    fun searchTrips(query:String,limit:Int=20):List<Pair<CatalogTrip,CatalogRoute>> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        return snapshot.trips.asSequence()
            .mapNotNull { trip -> routesByProviderAndId[trip.providerId to trip.routeId]?.let { trip to it } }
            .filter { (trip, route) ->
                trip.id.contains(q, true) || trip.departure.contains(q, true) ||
                    route.shortName.contains(q, true) || route.direction.contains(q, true)
            }
            .distinctBy { (trip, _) -> trip.providerId to trip.id }
            .take(limit)
            .toList()
    }

    // A GTFS route ID is only unique within one provider. Legacy callers lacking
    // provider context must not accidentally return trips from two transit feeds.
    fun tripsForRoute(routeId: String): List<CatalogTrip> {
        val providers = snapshot.routes.asSequence()
            .filter { it.id == routeId }
            .map { it.providerId }
            .distinct()
            .take(2)
            .toList()
        return if (providers.size == 1) tripsForRoute(providers.single(), routeId) else emptyList()
    }

    fun tripsForRoute(providerId: String, routeId: String): List<CatalogTrip> {
        if (providerId.isBlank() || routeId.isBlank() ||
            (providerId to routeId) !in routesByProviderAndId
        ) return emptyList()
        return snapshot.trips.filter { it.providerId == providerId && it.routeId == routeId }
    }
    /**
     * GTFS stop IDs are provider-local. A route must match the selected origin
     * and destination in the same provider, not just two coincidentally equal IDs.
     * Cross-provider travel requires an explicit transfer graph, which this
     * direct-route catalog does not implement.
     */
    fun routesServing(originStop: CatalogStop, destinationStop: CatalogStop): List<CatalogRoute> {
        if (originStop.providerId.isBlank() || originStop.providerId != destinationStop.providerId ||
            originStop.id.isBlank() || destinationStop.id.isBlank()
        ) return emptyList()
        return snapshot.routes.filter { route ->
            if (route.providerId != originStop.providerId) return@filter false
            val boarding = route.stopIds.indexOf(originStop.id)
            // A loop may revisit the alighting stop after boarding.
            val alighting = route.stopIds.lastIndexOf(destinationStop.id)
            boarding >= 0 && alighting > boarding
        }
    }

    fun nearestStops(latitude:Double,longitude:Double,limit:Int=5):List<NearbyStop> {
        // Bad GPS/provider coordinates must not crash distance rounding or reach map UI.
        if (limit <= 0 || !latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) return emptyList()
        return NearbyStationCanonicalizer.canonicalize(
            latitude = latitude,
            longitude = longitude,
            candidates = snapshot.stops.asSequence()
                .filter { stop ->
                    stop.latitude.isFinite() && stop.latitude in -90.0..90.0 &&
                        stop.longitude.isFinite() && stop.longitude in -180.0..180.0
                }
                .map { stop ->
                NearbyStop(
                    stop = stop,
                    distanceMeters = haversineMeters(
                        latitude,
                        longitude,
                        stop.latitude,
                        stop.longitude,
                    ).roundToInt(),
                    bearingDegrees = initialBearingDegrees(
                        latitude,
                        longitude,
                        stop.latitude,
                        stop.longitude,
                    ),
                )
            }
                .toList(),
            limit = limit,
        )
    }

    private fun stationKey(stop: CatalogStop): String {
        stop.parentStationId?.trim()?.takeIf { it.isNotEmpty() }?.let { return "parent:$it" }
        val normalizedName = stop.name
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
        val latBucket = (stop.latitude * 10_000).roundToInt()
        val lonBucket = (stop.longitude * 10_000).roundToInt()
        return "fallback:$normalizedName:$latBucket:$lonBucket"
    }

    private fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(deltaLon)
        return ((Math.toDegrees(atan2(y, x)).roundToInt() % 360) + 360) % 360
    }

    fun provider(id:String)=snapshot.providers.firstOrNull{it.id==id}
    fun isStale()=snapshot.stale
    private fun haversineMeters(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double { val r=6_371_000.0; val p1=Math.toRadians(lat1); val p2=Math.toRadians(lat2); val dp=Math.toRadians(lat2-lat1); val dl=Math.toRadians(lon2-lon1); val a=(sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2)).coerceIn(0.0,1.0); return 2*r*atan2(sqrt(a),sqrt(1-a)) }
}
