package com.nazofobi.arrivalalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesTransitCacheStore(context: Context) : TransitCacheBackingStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)

    override fun load(): List<CachedTransitPlan> {
        // Legacy/foreign preferences may contain a non-string value at this key.
        // A broken offline cache must not prevent the main app from starting.
        val raw = try {
            prefs.getString("plans_json", null)
        } catch (_: ClassCastException) {
            null
        } ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toCachedTransitPlan()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun save(plans: List<CachedTransitPlan>) {
        val array = JSONArray()
        plans.forEach { array.put(it.toJson()) }
        prefs.edit().putString("plans_json", array.toString()).apply()
    }
}

private fun CachedTransitPlan.toJson(): JSONObject = JSONObject()
    .put("key", key)
    .put("savedAtEpochSeconds", savedAtEpochSeconds)
    .put("routeOptions", JSONArray().apply { routeOptions.forEach { put(it.toJson()) } })
    .put("departures", JSONArray().apply { departures.forEach { put(it.toJson()) } })

private fun JSONObject.toCachedTransitPlan(): CachedTransitPlan? {
    // Never manufacture an offline cache key from a JSON number or boolean.
    val key = (opt("key") as? String)?.takeIf { it.isNotBlank() } ?: return null
    // A malformed or missing save time cannot be used to establish cache freshness.
    val savedAt = strictPositiveEpochSecondsOrNull("savedAtEpochSeconds") ?: return null
    return CachedTransitPlan(
        key = key,
        routeOptions = optJSONArray("routeOptions").toRouteOptions(),
        departures = optJSONArray("departures").toDepartures(),
        savedAtEpochSeconds = savedAt,
    )
}

private fun RouteOption.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("origin", origin.toJson())
    .put("destination", destination.toJson())
    .put("line", line)
    .put("direction", direction)
    .put("departure", departure)
    .put("arrival", arrival)
    .putNullable("departureEpochMillis", departureEpochMillis)
    .putNullable("arrivalEpochMillis", arrivalEpochMillis)
    .put("walkingMinutes", walkingMinutes)
    .put("transfers", transfers)
    .put("tripIds", JSONArray(tripIds))
    .put("stops", JSONArray().apply { stops.forEach { put(it.toJson()) } })
    .putNullable("refreshToken", refreshToken)
    .putNullable("sourceUpdatedAtEpochSeconds", sourceUpdatedAtEpochSeconds)

private fun JSONObject.toRouteOption(): RouteOption? {
    val origin = optJSONObject("origin")?.toMapPoint() ?: return null
    val destination = optJSONObject("destination")?.toMapPoint() ?: return null
    // Route identities must survive persistence without JSON primitive coercion.
    val id = (opt("id") as? String)?.takeIf { it.isNotBlank() } ?: return null
    val stopsJson = optJSONArray("stops")
    if (has("stops") && !isNull("stops") && stopsJson == null) return null
    val stops = stopsJson.toRouteStops()
    // A silently dropped stop changes the ordered itinerary and may hide the alarm target.
    if (stopsJson != null && stops.size != stopsJson.length()) return null
    // org.json optInt coerces malformed stored values, masking bad route metadata.
    val walkingMinutes = nonNegativeStoredIntOrNull("walkingMinutes") ?: return null
    val transfers = nonNegativeStoredIntOrNull("transfers") ?: return null
    val tripIds = when {
        !has("tripIds") || isNull("tripIds") -> emptyList()
        else -> optJSONArray("tripIds")?.toStrictTripIdsOrNull() ?: return null
    }
    // A malformed source timestamp must not impersonate verified live data.
    val sourceUpdatedAt = when {
        !has("sourceUpdatedAtEpochSeconds") || isNull("sourceUpdatedAtEpochSeconds") -> null
        else -> strictNonNegativeEpochSecondsOrNull("sourceUpdatedAtEpochSeconds") ?: return null
    }
    // Absolute scheduled instants must survive offline recreation together.
    // Reject partial, coerced, reversed or negative timestamps rather than
    // silently falling back to clock labels and showing an incorrect duration.
    val hasDepartureEpoch = has("departureEpochMillis") && !isNull("departureEpochMillis")
    val hasArrivalEpoch = has("arrivalEpochMillis") && !isNull("arrivalEpochMillis")
    if (hasDepartureEpoch != hasArrivalEpoch) return null
    val departureEpoch = if (hasDepartureEpoch) {
        strictNonNegativeEpochMillisOrNull("departureEpochMillis") ?: return null
    } else null
    val arrivalEpoch = if (hasArrivalEpoch) {
        strictNonNegativeEpochMillisOrNull("arrivalEpochMillis") ?: return null
    } else null
    if (departureEpoch != null && arrivalEpoch != null && arrivalEpoch < departureEpoch) return null
    return RouteOption(
        id = id,
        origin = origin,
        destination = destination,
        line = optString("line"),
        direction = optString("direction"),
        departure = optString("departure"),
        arrival = optString("arrival"),
        walkingMinutes = walkingMinutes,
        transfers = transfers,
        tripIds = tripIds,
        stops = stops,
        refreshToken = optNullableString("refreshToken"),
        sourceUpdatedAtEpochSeconds = sourceUpdatedAt,
        departureEpochMillis = departureEpoch,
        arrivalEpochMillis = arrivalEpoch,
    )
}

private fun MapPoint.toJson(): JSONObject = JSONObject()
    .put("latitude", latitude)
    .put("longitude", longitude)
    .put("label", label)

private fun JSONObject.toMapPoint(): MapPoint? {
    val latitude = optDouble("latitude", Double.NaN)
    val longitude = optDouble("longitude", Double.NaN)
    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
        !longitude.isFinite() || longitude !in -180.0..180.0
    ) return null
    return MapPoint(latitude, longitude, optString("label"))
}

private fun RouteStop.toJson(): JSONObject = JSONObject()
    .putNullable("id", id)
    .put("name", name)
    .putNullable("latitude", latitude)
    .putNullable("longitude", longitude)
    .putNullable("arrival", arrival)
    .putNullable("plannedArrival", plannedArrival)
    .putNullable("departure", departure)
    .putNullable("plannedDeparture", plannedDeparture)
    .putNullable("platform", platform)
    .putNullable("plannedPlatform", plannedPlatform)

private fun JSONObject.toRouteStop(): RouteStop? {
    val name = optString("name").takeIf { it.isNotBlank() } ?: return null
    // Optional coordinates may be absent, but supplied invalid values cannot be trusted.
    if (hasInvalidCoordinate("latitude", -90.0, 90.0) ||
        hasInvalidCoordinate("longitude", -180.0, 180.0)
    ) return null
    return RouteStop(
        id = optNullableString("id"),
        name = name,
        latitude = optNullableDouble("latitude"),
        longitude = optNullableDouble("longitude"),
        arrival = optNullableString("arrival"),
        plannedArrival = optNullableString("plannedArrival"),
        departure = optNullableString("departure"),
        plannedDeparture = optNullableString("plannedDeparture"),
        platform = optNullableString("platform"),
        plannedPlatform = optNullableString("plannedPlatform"),
    )
}

private fun Departure.toJson(): JSONObject = JSONObject()
    .put("tripId", tripId)
    .put("line", line)
    .put("direction", direction)
    .put("scheduledEpochSeconds", scheduledEpochSeconds)
    .putNullable("realtimeEpochSeconds", realtimeEpochSeconds)
    .put("cancelled", cancelled)
    .putNullable("platform", platform)
    .putNullable("routeId", routeId)

private fun JSONObject.toDeparture(): Departure? {
    // A coerced numeric/boolean JSON value is not an authoritative GTFS trip identity.
    val tripId = (opt("tripId") as? String)?.takeIf { it.isNotBlank() } ?: return null
    // JSON getters silently coerce missing, malformed or fractional times to epoch zero.
    // Keep offline departures only when a positive whole-second timestamp is proven.
    val scheduled = strictPositiveEpochSecondsOrNull("scheduledEpochSeconds") ?: return null
    // A coerced string such as "true" must never create an unverified cancellation.
    // Missing legacy fields retain the previous non-cancelled default.
    val cancelled = if (!has("cancelled")) false else opt("cancelled") as? Boolean ?: return null
    return Departure(
        tripId = tripId,
        line = optString("line"),
        direction = optString("direction"),
        scheduledEpochSeconds = scheduled,
        realtimeEpochSeconds = strictPositiveEpochSecondsOrNull("realtimeEpochSeconds"),
        cancelled = cancelled,
        platform = optNullableString("platform"),
        routeId = optNullableString("routeId"),
    )
}

private fun JSONArray?.toRouteOptions(): List<RouteOption> = buildList {
    val array = this@toRouteOptions ?: return@buildList
    for (index in 0 until array.length()) array.optJSONObject(index)?.toRouteOption()?.let(::add)
}

private fun JSONArray?.toRouteStops(): List<RouteStop> = buildList {
    val array = this@toRouteStops ?: return@buildList
    for (index in 0 until array.length()) array.optJSONObject(index)?.toRouteStop()?.let(::add)
}

private fun JSONArray?.toDepartures(): List<Departure> = buildList {
    val array = this@toDepartures ?: return@buildList
    for (index in 0 until array.length()) array.optJSONObject(index)?.toDeparture()?.let(::add)
}

/** Never coerce numbers, booleans or nulls into an offline trip identity. */
private fun JSONArray.toStrictTripIdsOrNull(): List<String>? {
    val ids = ArrayList<String>(length())
    for (index in 0 until length()) {
        val id = opt(index) as? String ?: return null
        if (id.isBlank()) return null
        ids.add(id)
    }
    return ids
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

/** Missing legacy counts default to zero; supplied counts must be finite, whole and >= 0. */
private fun JSONObject.nonNegativeStoredIntOrNull(key: String): Int? {
    if (!has(key)) return 0
    return (opt(key) as? Number)?.toString()?.toIntOrNull()?.takeIf { it >= 0 }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** Reject JSON coercions that could turn corrupt realtime times into false live departures. */
private fun JSONObject.strictPositiveEpochSecondsOrNull(key: String): Long? =
    (opt(key) as? Number)?.toString()?.toLongOrNull()
        ?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }

/** Strictly numeric, whole and bounded persisted source epoch; zero means unknown age. */
private fun JSONObject.strictNonNegativeEpochSecondsOrNull(key: String): Long? =
    (opt(key) as? Number)?.toString()?.toLongOrNull()
        ?.takeIf { it >= 0L && it <= Long.MAX_VALUE / 1_000L }

/** Persisted absolute route instants accept JSON integral numbers only, never coercions. */
private fun JSONObject.strictNonNegativeEpochMillisOrNull(key: String): Long? =
    (opt(key) as? Number)?.toString()?.toLongOrNull()?.takeIf { it >= 0L }

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

private fun JSONObject.hasInvalidCoordinate(key: String, min: Double, max: Double): Boolean {
    if (!has(key) || isNull(key)) return false
    val value = optDouble(key, Double.NaN)
    return !value.isFinite() || value !in min..max
}
