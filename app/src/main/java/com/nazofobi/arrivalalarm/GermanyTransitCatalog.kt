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
    fun searchStops(query:String,limit:Int=20):List<CatalogStop>{ val q=query.trim(); if(q.isEmpty())return emptyList(); return snapshot.stops.filter{it.name.contains(q,true)}.take(limit) }
    fun searchRoutes(query:String,limit:Int=20):List<CatalogRoute>{ val q=query.trim(); if(q.isEmpty())return emptyList(); return snapshot.routes.filter{it.shortName.contains(q,true)||it.direction.contains(q,true)}.take(limit) }
    fun searchTrips(query:String,limit:Int=20):List<Pair<CatalogTrip,CatalogRoute>> { val q=query.trim(); if(q.isEmpty()) return emptyList(); return snapshot.trips.mapNotNull { trip -> snapshot.routes.firstOrNull { it.id==trip.routeId }?.let { trip to it } }.filter { (trip,route) -> trip.id.contains(q,true)||trip.departure.contains(q,true)||route.shortName.contains(q,true)||route.direction.contains(q,true) }.take(limit) }
    fun tripsForRoute(routeId:String)=snapshot.trips.filter{it.routeId==routeId}
    fun routesServing(originStopId:String,destinationStopId:String):List<CatalogRoute> = snapshot.routes.filter { route -> val a=route.stopIds.indexOf(originStopId); val b=route.stopIds.indexOf(destinationStopId); a>=0 && b>a }
    fun nearestStops(latitude:Double,longitude:Double,limit:Int=5):List<NearbyStop> =
        snapshot.stops
            .groupBy { stationKey(it) }
            .values
            .map { members ->
                val representative = members.minWith(
                    compareBy<CatalogStop>(
                        { haversineMeters(latitude, longitude, it.latitude, it.longitude) },
                        { it.id },
                    ),
                )
                NearbyStop(
                    stop = representative,
                    distanceMeters = haversineMeters(latitude, longitude, representative.latitude, representative.longitude).roundToInt(),
                    bearingDegrees = initialBearingDegrees(latitude, longitude, representative.latitude, representative.longitude),
                )
            }
            .sortedWith(compareBy<NearbyStop>({ it.distanceMeters }, { it.stop.name.lowercase() }, { it.stop.id }))
            .take(limit)
    private fun stationKey(stop: CatalogStop): String {
        stop.parentStationId?.trim()?.takeIf { it.isNotEmpty() }?.let { return "parent:$it" }
        val normalizedName = stop.name
            .lowercase()
            .replace(Regex("""\\p{M}+"""), "")
            .replace(Regex("""[^\\p{L}\\p{N}]+"""), " ")
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
    private fun haversineMeters(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double { val r=6_371_000.0; val p1=Math.toRadians(lat1); val p2=Math.toRadians(lat2); val dp=Math.toRadians(lat2-lat1); val dl=Math.toRadians(lon2-lon1); val a=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2); return 2*r*atan2(sqrt(a),sqrt(1-a)) }
}
