package com.nazofobi.arrivalalarm

import kotlin.math.*

data class TransitProvider(
    val id: String,
    val name: String,
    val coverage: String,
    val staticSource: String,
    val license: String,
    val sourceVersion: String,
    val fetchedAt: String,
    val realtimeCapability: Boolean,
    val realtimeSource: String? = null,
)

data class CatalogStop(
    val id: String,
    val providerId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class CatalogRoute(
    val id: String,
    val providerId: String,
    val shortName: String,
    val direction: String,
    val stopIds: List<String>,
)

data class CatalogTrip(
    val id: String,
    val providerId: String,
    val routeId: String,
    val departure: String,
)

data class NearbyStop(val stop: CatalogStop, val distanceMeters: Int)

data class CatalogSnapshot(
    val providers: List<TransitProvider>,
    val stops: List<CatalogStop>,
    val routes: List<CatalogRoute>,
    val trips: List<CatalogTrip>,
    val generatedAt: String,
    val stale: Boolean = false,
)

/**
 * Offline-first normalized transit catalog. Production importers may replace the frozen fixtures;
 * UI/search semantics remain independent from any one transport association.
 */
class GermanyTransitCatalog(private val snapshot: CatalogSnapshot = GermanyTransitFixtures.snapshot) {
    fun searchStops(query: String, limit: Int = 20): List<CatalogStop> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return snapshot.stops.filter { it.name.contains(q, ignoreCase = true) }.take(limit)
    }

    fun searchRoutes(query: String, limit: Int = 20): List<CatalogRoute> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return snapshot.routes.filter {
            it.shortName.contains(q, ignoreCase = true) || it.direction.contains(q, ignoreCase = true)
        }.take(limit)
    }

    fun tripsForRoute(routeId: String): List<CatalogTrip> = snapshot.trips.filter { it.routeId == routeId }

    fun nearestStops(latitude: Double, longitude: Double, limit: Int = 5): List<NearbyStop> =
        snapshot.stops.map { NearbyStop(it, haversineMeters(latitude, longitude, it.latitude, it.longitude).roundToInt()) }
            .sortedBy { it.distanceMeters }
            .take(limit)

    fun provider(id: String): TransitProvider? = snapshot.providers.firstOrNull { it.id == id }
    fun isStale(): Boolean = snapshot.stale

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

object GermanyTransitFixtures {
    val snapshot = CatalogSnapshot(
        providers = listOf(
            TransitProvider("delfi", "DELFI / GTFS Deutschland", "Germany", "https://gtfs.de/en/feeds/de_full/", "Creative Commons 4.0", "2026-09", "2026-09-22", true, "https://www.gtfs.de/de/realtime/"),
            TransitProvider("vbb", "VBB", "Berlin-Brandenburg", "https://unternehmen.vbb.de/en/digital-services/datasets/", "Open Data; provider terms apply", "weekly-2026-09", "2026-09-22", true, "VBB GTFS-RT"),
            TransitProvider("mvv", "MVV", "Munich region", "https://www.mvv-muenchen.de/en/service-support/mvv-content-for-developers/", "CC BY; attribution required", "GTFS 09/2026", "2026-09-22", false),
        ),
        stops = listOf(
            CatalogStop("de:hb:hbf", "delfi", "Bremen Hbf", 53.0834, 8.8137),
            CatalogStop("de:hb:airport", "delfi", "Flughafen Bremen", 53.0531, 8.7866),
            CatalogStop("de:be:hbf", "vbb", "Berlin Hauptbahnhof", 52.5251, 13.3694),
            CatalogStop("de:be:alex", "vbb", "Alexanderplatz", 52.5219, 13.4132),
            CatalogStop("de:by:mhbf", "mvv", "München Hauptbahnhof", 48.1402, 11.5586),
            CatalogStop("de:by:marien", "mvv", "Marienplatz", 48.1372, 11.5755),
        ),
        routes = listOf(
            CatalogRoute("r-hb-6", "delfi", "6", "Flughafen", listOf("de:hb:hbf", "de:hb:airport")),
            CatalogRoute("r-be-s5", "vbb", "S5", "Strausberg Nord", listOf("de:be:hbf", "de:be:alex")),
            CatalogRoute("r-by-s8", "mvv", "S8", "Herrsching", listOf("de:by:mhbf", "de:by:marien")),
        ),
        trips = listOf(
            CatalogTrip("t-hb-6-1200", "delfi", "r-hb-6", "12:00"),
            CatalogTrip("t-be-s5-1205", "vbb", "r-be-s5", "12:05"),
            CatalogTrip("t-by-s8-1210", "mvv", "r-by-s8", "12:10"),
        ),
        generatedAt = "2026-09-22T17:49:34+02:00",
    )
}
