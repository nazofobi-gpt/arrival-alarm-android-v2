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
    // A present, wrong-typed collection is not a verified empty cache. Reject
    // that entry rather than silently hiding its itinerary or departure board.
    // Missing/null arrays remain supported for older cache records.
    val routesJson = optJSONArray("routeOptions")
    val departuresJson = optJSONArray("departures")
    if ((has("routeOptions") && !isNull("routeOptions") && routesJson == null) ||
        (has("departures") && !isNull("departures") && departuresJson == null)
    ) return null
    return CachedTransitPlan(
        key = key,
        routeOptions = routesJson.toRouteOptions(),
        departures = departuresJson.toDepartures(),
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
    .put("walkingMinutes", walkingMinutes)
    .put("transfers", transfers)
    .put("tripIds", JSONArray(tripIds))
    .put("stops", JSONArray().apply { stops.forEach { put(it.toJson()) } })
    .putNullable("refreshToken", refreshToken)
    .putNullable("sourceUpdatedAtEpochSeconds", sourceUpdatedAtEpochSeconds)
    .putNullable("departureEpochMillis", departureEpochMillis)
    .putNullable("arrivalEpochMillis", arrivalEpochMillis)

private fun JSONObject.toRouteOption(): RouteOption? {
    val origin = optJSONObject("origin")?.toMapPoint() ?: return null
    val destination = optJSONObject("destination")?.toMapPoint() ?: return null
    // Route identities must survive persistence without JSON primitive coercion.
    val id = (opt("id") as? String)?.takeIf { it.isNotBlank() } ?: return null
    // A corrupt refresh token cannot become a provider request identity.
    if (hasNonStringOptional("refreshToken")) return null
    // Do not stringify booleans or JSON containers into displayed route facts.
    if (listOf("line", "direction", "departure", "arrival").any { hasNonStringOptional(it) }) return null
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
    // Legacy itineraries have no exact instants; malformed present values cannot be trusted.
    val departureEpochMillis = when {
        !has("departureEpochMillis") || isNull("departureEpochMillis") -> null
        else -> strictPositiveStoredEpochMillisOrNull("departureEpochMillis") ?: return null
    }
    val arrivalEpochMillis = when {
        !has("arrivalEpochMillis") || isNull("arrivalEpochMillis") -> null
        else -> strictPositiveStoredEpochMillisOrNull("arrivalEpochMillis") ?: return null
    }
    // Reject impossible itinerary chronology rather than show a misleading duration.
    if (departureEpochMillis != null && arrivalEpochMillis != null &&
        arrivalEpochMillis < departureEpochMillis
    ) return null
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
        departureEpochMillis = departureEpochMillis,
        arrivalEpochMillis = arrivalEpochMillis,
    )
}

private fun MapPoint.toJson(): JSONObject = JSONObject()
    .put("latitude", latitude)
    .put("longitude", longitude)
    .put("label", label)

private fun JSONObject.toMapPoint(): MapPoint? {
    // Saved coordinates must be JSON numbers; org.json otherwise coerces strings.
    val latitude = strictStoredCoordinateOrNull("latitude", -90.0, 90.0) ?: return null
    val longitude = strictStoredCoordinateOrNull("longitude", -180.0, 180.0) ?: return null
    // A stored label must be a real JSON string; do not coerce numbers or booleans.
    // Older route snapshots may omit the optional label altogether.
    val label = when {
        !has("label") || isNull("label") -> ""
        else -> opt("label") as? String ?: return null
    }
    return MapPoint(latitude, longitude, label)
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
    // A number, boolean or JSON container cannot be treated as an actual stop name.
    val name = (opt("name") as? String)?.takeIf { it.isNotBlank() } ?: return null
    // An optional physical stop ID must not be manufactured from a JSON primitive.
    if (hasNonStringOptional("id")) return null
    // Only scheduled baselines may survive offline; corrupt planned facts must
    // reject the stop instead of being rendered as text from another JSON type.
    if (listOf("plannedArrival", "plannedDeparture", "plannedPlatform")
            .any { hasNonStringOptional(it) }
    ) return null
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
    // Reject a forged route identity, rather than stringifying a stored JSON number.
    if (hasNonStringOptional("routeId")) return null
    if (listOf("line", "direction").any { hasNonStringOptional(it) }) return null
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

private fun JSONObject.hasNonStringOptional(key: String): Boolean =
    has(key) && !isNull(key) && opt(key) !is String

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** Reject JSON coercions and times beyond year 9999 before offline departures are shown. */
private fun JSONObject.strictPositiveEpochSecondsOrNull(key: String): Long? =
    (opt(key) as? Number)?.toString()?.toLongOrNull()
        ?.takeIf { it > 0L && it <= 253_402_300_799L }

/** Strict numeric whole seconds through year 9999; zero means unknown age. */
private fun JSONObject.strictNonNegativeEpochSecondsOrNull(key: String): Long? =
    (opt(key) as? Number)?.toString()?.toLongOrNull()
        ?.takeIf { it >= 0L && it <= 253_402_300_799L }

/** Exact positive numeric milliseconds, bounded to the supported calendar range through year 9999. */
private fun JSONObject.strictPositiveStoredEpochMillisOrNull(key: String): Long? =
    (opt(key) as? Number)?.toString()?.toLongOrNull()
        ?.takeIf { it > 0L && it <= 253_402_300_799_999L }

private fun JSONObject.optNullableDouble(key: String): Double? =
    (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

/** Reject nonnumeric JSON coordinates rather than accepting org.json's conversions. */
private fun JSONObject.strictStoredCoordinateOrNull(key: String, min: Double, max: Double): Double? =
    (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it in min..max }

private fun JSONObject.hasInvalidCoordinate(key: String, min: Double, max: Double): Boolean {
    if (!has(key) || isNull(key)) return false
    return strictStoredCoordinateOrNull(key, min, max) == null
}
