package com.nazofobi.arrivalalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesTransitCacheStore(context: Context) : TransitCacheBackingStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)

    override fun load(): List<CachedTransitPlan> {
        val raw = prefs.getString("plans_json", null) ?: return emptyList()
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
    val key = optString("key").takeIf { it.isNotBlank() } ?: return null
    return CachedTransitPlan(
        key = key,
        routeOptions = optJSONArray("routeOptions").toRouteOptions(),
        departures = optJSONArray("departures").toDepartures(),
        savedAtEpochSeconds = optLong("savedAtEpochSeconds", 0L),
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

private fun JSONObject.toRouteOption(): RouteOption? {
    val origin = optJSONObject("origin")?.toMapPoint() ?: return null
    val destination = optJSONObject("destination")?.toMapPoint() ?: return null
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    return RouteOption(
        id = id,
        origin = origin,
        destination = destination,
        line = optString("line"),
        direction = optString("direction"),
        departure = optString("departure"),
        arrival = optString("arrival"),
        walkingMinutes = optInt("walkingMinutes", 0),
        transfers = optInt("transfers", 0),
        tripIds = optJSONArray("tripIds").toStrings(),
        stops = optJSONArray("stops").toRouteStops(),
        refreshToken = optNullableString("refreshToken"),
        sourceUpdatedAtEpochSeconds = optNullableLong("sourceUpdatedAtEpochSeconds"),
    )
}

private fun MapPoint.toJson(): JSONObject = JSONObject()
    .put("latitude", latitude)
    .put("longitude", longitude)
    .put("label", label)

private fun JSONObject.toMapPoint(): MapPoint? {
    val latitude = optDouble("latitude", Double.NaN)
    val longitude = optDouble("longitude", Double.NaN)
    if (!latitude.isFinite() || !longitude.isFinite()) return null
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

private fun JSONObject.toRouteStop(): RouteStop? {
    val name = optString("name").takeIf { it.isNotBlank() } ?: return null
    return RouteStop(
        id = optNullableString("id"),
        name = name,
        latitude = optNullableDouble("latitude"),
        longitude = optNullableDouble("longitude"),
        arrival = optNullableString("arrival"),
        plannedArrival = optNullableString("plannedArrival"),
        departure = optNullableString("departure"),
        plannedDeparture = optNullableString("plannedDeparture"),
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
    val tripId = optString("tripId").takeIf { it.isNotBlank() } ?: return null
    return Departure(
        tripId = tripId,
        line = optString("line"),
        direction = optString("direction"),
        scheduledEpochSeconds = optLong("scheduledEpochSeconds", 0L),
        realtimeEpochSeconds = optNullableLong("realtimeEpochSeconds"),
        cancelled = optBoolean("cancelled", false),
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

private fun JSONArray?.toStrings(): List<String> = buildList {
    val array = this@toStrings ?: return@buildList
    for (index in 0 until array.length()) {
        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.optNullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
