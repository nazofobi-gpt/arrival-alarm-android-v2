package com.nazofobi.arrivalalarm

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object NearbyStationCanonicalizer {
    fun canonicalize(
        latitude: Double,
        longitude: Double,
        candidates: List<NearbyStop>,
        limit: Int,
    ): List<NearbyStop> {
        val parentIds = candidates.mapNotNull {
            it.stop.parentStationId?.trim()?.takeIf(String::isNotEmpty)
        }.toSet()

        return candidates
            .groupBy { candidate ->
                val stop = candidate.stop
                when {
                    stop.parentStationId?.isNotBlank() == true -> "parent:" + stop.parentStationId
                    stop.id in parentIds -> "parent:" + stop.id
                    else -> fallbackKey(stop)
                }
            }
            .values
            .map { members ->
                val parentId = members.mapNotNull {
                    it.stop.parentStationId?.trim()?.takeIf(String::isNotEmpty)
                }.minOrNull()
                val existingParent = parentId?.let { id ->
                    members.firstOrNull { it.stop.id == id }?.stop
                }
                val base = existingParent ?: members.minBy { it.stop.id }.stop
                val canonical = if (parentId != null && existingParent == null) {
                    base.copy(id = parentId, parentStationId = null)
                } else {
                    base.copy(parentStationId = null)
                }
                val childStops = members
                    .map { it.stop }
                    .filter { it.id != canonical.id }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareBy<CatalogStop>(
                            { it.name.lowercase(Locale.GERMANY) },
                            { it.id },
                        ),
                    )
                NearbyStop(
                    stop = canonical,
                    distanceMeters = haversineMeters(
                        latitude, longitude, canonical.latitude, canonical.longitude,
                    ).roundToInt(),
                    bearingDegrees = initialBearingDegrees(
                        latitude, longitude, canonical.latitude, canonical.longitude,
                    ),
                    childStops = childStops,
                )
            }
            .sortedWith(
                compareBy<NearbyStop>(
                    { it.distanceMeters },
                    { it.stop.name.lowercase(Locale.GERMANY) },
                    { it.stop.id },
                ),
            )
            .take(limit)
    }

    private fun fallbackKey(stop: CatalogStop): String {
        val normalizedName = stop.name.lowercase(Locale.GERMANY)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        val latBucket = (stop.latitude * 10_000).roundToInt()
        val lonBucket = (stop.longitude * 10_000).roundToInt()
        return "fallback:$normalizedName:$latBucket:$lonBucket"
    }

    private fun initialBearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Int {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(deltaLon)
        return ((Math.toDegrees(atan2(y, x)).roundToInt() % 360) + 360) % 360
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val radius = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * radius * atan2(sqrt(a), sqrt(1 - a))
    }
}
