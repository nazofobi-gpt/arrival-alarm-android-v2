package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineTransitCachePersistenceInstrumentedTest {
    @Test
    fun cachedRouteSurvivesCacheRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val route = RouteOption(
            id = "route-re9",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
            line = "RE 9",
            direction = "Bremen Hbf",
            departure = "10:00",
            arrival = "10:45",
            walkingMinutes = 3,
            transfers = 0,
            tripIds = listOf("trip-re9"),
            stops = listOf(
                RouteStop(id = "stop-a", name = "Lohne"),
                RouteStop(id = "stop-b", name = "Bremen Hbf"),
            ),
        )
        val key = "52.665,8.237->53.083,8.813"
        OfflineTransitCache(
            backingStore = SharedPreferencesTransitCacheStore(context)
        ).put(CachedTransitPlan(key, listOf(route), emptyList(), 100L))

        val restored = OfflineTransitCache(
            backingStore = SharedPreferencesTransitCacheStore(context)
        ).routeOptions(key)

        assertEquals(1, restored.size)
        assertEquals("route-re9", restored.single().id)
        assertEquals(listOf("Lohne", "Bremen Hbf"), restored.single().stops.map { it.name })
        assertTrue(restored.single().tripIds.contains("trip-re9"))
    }

    @Test
    fun outOfRangeCachedRouteCoordinatesAreRejectedOnRestore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = SharedPreferencesTransitCacheStore(context)
        val validRoute = RouteOption(
            id = "valid-route",
            origin = MapPoint(53.083, 8.813, "Bremen"),
            destination = MapPoint(52.665, 8.237, "Lohne"),
            line = "RE",
            direction = "Lohne",
            departure = "10:00",
            arrival = "11:00",
            walkingMinutes = 0,
            transfers = 0,
            tripIds = emptyList(),
            stops = emptyList(),
        )
        OfflineTransitCache(backingStore = store).put(
            CachedTransitPlan("valid", listOf(validRoute), emptyList(), 100L)
        )
        assertEquals(1, OfflineTransitCache(backingStore = store).routeOptions("valid").size)

        val invalidRoutes = listOf(
            "invalid-latitude" to validRoute.copy(origin = MapPoint(91.0, 8.813, "Invalid")),
            "invalid-longitude" to validRoute.copy(destination = MapPoint(52.665, 181.0, "Invalid")),
        )
        invalidRoutes.forEach { (key, route) ->
            OfflineTransitCache(backingStore = store).put(
                CachedTransitPlan(key, listOf(route), emptyList(), 100L)
            )
            assertTrue(OfflineTransitCache(backingStore = store).routeOptions(key).isEmpty())
        }
    }


    @Test
    fun wrongTypedLegacyCacheIsIgnoredAndCanBeReplaced() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        prefs.edit().clear().putLong("plans_json", 123L).commit()
        try {
            val store = SharedPreferencesTransitCacheStore(context)
            assertTrue(store.load().isEmpty())
            val cache = OfflineTransitCache(backingStore = store)
            cache.put(CachedTransitPlan("recovered", emptyList(), emptyList(), 1_000L))
            assertEquals("recovered", store.load().single().key)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun corruptedCachedStopDoesNotSilentlyShortenJourney() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "ordered-stops",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
            line = "RE",
            direction = "Bremen",
            departure = "10:00",
            arrival = "11:00",
            walkingMinutes = 0,
            transfers = 0,
            stops = listOf(
                RouteStop(id = "start", name = "Lohne", latitude = 52.665, longitude = 8.237),
                RouteStop(id = "end", name = "Bremen Hbf", latitude = 53.083, longitude = 8.813),
            ),
        )
        OfflineTransitCache(backingStore = store).put(
            CachedTransitPlan("valid-stops", listOf(route), emptyList(), 100L)
        )
        assertEquals(2, OfflineTransitCache(backingStore = store)
            .routeOptions("valid-stops").single().stops.size)

        val invalidStops = listOf(
            route.stops[1].copy(latitude = 91.0),
            route.stops[1].copy(longitude = 181.0),
            route.stops[1].copy(name = ""),
        )
        invalidStops.forEachIndexed { index, invalidStop ->
            val key = "invalid-stop-$index"
            OfflineTransitCache(backingStore = store).put(
                CachedTransitPlan(
                    key, listOf(route.copy(stops = listOf(route.stops[0], invalidStop))),
                    emptyList(), 100L,
                )
            )
            assertTrue(OfflineTransitCache(backingStore = store).routeOptions(key).isEmpty())
        }
    }

    @Test
    fun cachedPlanKeyAndRouteIdMustRemainRealJsonStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "valid-route",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE", direction = "Bremen",
            departure = "10:00", arrival = "11:00",
            walkingMinutes = 0, transfers = 0,
        )
        try {
            store.save(listOf(CachedTransitPlan("valid-key", listOf(route), emptyList(), 100L)))
            val saved = org.json.JSONArray(requireNotNull(prefs.getString("plans_json", null)))
                .getJSONObject(0)
            val invalidIds: List<Any?> = listOf(
                null, org.json.JSONObject.NULL, 123, true,
                org.json.JSONArray().put("valid-route"),
                org.json.JSONObject().put("id", "valid-route"), "  ",
            )
            // A corrupt plan key must never be coerced into a routable cache entry.
            val plans = org.json.JSONArray()
            invalidIds.forEach { value ->
                val item = org.json.JSONObject(saved.toString())
                if (value == null) item.remove("key") else item.put("key", value)
                plans.put(item)
            }
            plans.put(saved)
            assertTrue(prefs.edit().putString("plans_json", plans.toString()).commit())
            assertEquals(listOf("valid-key"), store.load().map { it.key })

            // Invalid route identities cannot impersonate a real selected itinerary.
            val validRoute = saved.getJSONArray("routeOptions").getJSONObject(0)
            val routes = org.json.JSONArray()
            invalidIds.forEach { value ->
                val item = org.json.JSONObject(validRoute.toString())
                if (value == null) item.remove("id") else item.put("id", value)
                routes.put(item)
            }
            routes.put(validRoute)
            val plan = org.json.JSONObject(saved.toString()).put("routeOptions", routes)
            assertTrue(prefs.edit().putString(
                "plans_json", org.json.JSONArray().put(plan).toString()
            ).commit())
            val restored = store.load().single()
            assertEquals("valid-key", restored.key)
            assertEquals(listOf("valid-route"), restored.routeOptions.map { it.id })
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun cachedDepartureTripIdMustBeJsonStringWithoutCoercion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val cases: List<Pair<String, Any?>> = listOf(
            "missing" to null,
            "null" to org.json.JSONObject.NULL,
            "number" to 123,
            "boolean" to true,
            "array" to org.json.JSONArray().put("trip-good"),
            "object" to org.json.JSONObject().put("id", "trip-good"),
            "blank" to "  ",
            "valid" to "trip-good",
        )
        val departures = org.json.JSONArray()
        cases.forEach { (label, tripId) ->
            val departure = org.json.JSONObject()
                .put("line", label)
                .put("direction", "Bremen")
                .put("scheduledEpochSeconds", 1_700_000_000L)
            if (tripId != null) departure.put("tripId", tripId)
            departures.put(departure)
        }
        val plan = org.json.JSONObject()
            .put("key", "offline-trip-id-typing")
            .put("savedAtEpochSeconds", 100L)
            .put("routeOptions", org.json.JSONArray())
            .put("departures", departures)
        assertTrue(prefs.edit().clear().putString(
            "plans_json", org.json.JSONArray().put(plan).toString()
        ).commit())
        try {
            val restored = SharedPreferencesTransitCacheStore(context).load().single().departures
            assertEquals(listOf("trip-good"), restored.map { it.tripId })
            assertEquals(listOf("valid"), restored.map { it.line })
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun invalidCachedScheduledEpochsAreRejectedWithoutLosingValidDeparture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val departures = org.json.JSONArray()
        val cases: List<Pair<String, Any?>> = listOf(
            "missing" to null,
            "null" to org.json.JSONObject.NULL,
            "string" to "1700000000",
            "boolean" to true,
            "fraction" to 1_700_000_000.5,
            "zero" to 0L,
            "negative" to -1L,
            "overflow" to (Long.MAX_VALUE / 1_000L + 1L),
            "valid" to 1_700_000_000L,
        )
        cases.forEach { (id, timestamp) ->
            val departure = org.json.JSONObject()
                .put("tripId", id)
                .put("line", "RE")
                .put("direction", "Bremen")
            if (timestamp != null) departure.put("scheduledEpochSeconds", timestamp)
            departures.put(departure)
        }
        val cached = org.json.JSONObject()
            .put("key", "offline-departures")
            .put("savedAtEpochSeconds", 100L)
            .put("routeOptions", org.json.JSONArray())
            .put("departures", departures)
        prefs.edit().clear().putString(
            "plans_json", org.json.JSONArray().put(cached).toString()
        ).commit()
        try {
            val loaded = SharedPreferencesTransitCacheStore(context).load().single()
            assertEquals(listOf("valid"), loaded.departures.map { it.tripId })
            assertEquals(1_700_000_000L, loaded.departures.single().scheduledEpochSeconds)
        } finally {
            prefs.edit().clear().commit()
        }
    }
    @Test
    fun cachedDepartureCancellationRequiresActualJsonBoolean() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val cases: List<Pair<String, Any?>> = listOf(
            "cancelled" to true,
            "not-cancelled" to false,
            "legacy-missing" to null,
            "text-true" to "true",
            "text-false" to "false",
            "number-one" to 1,
            "number-zero" to 0,
            "explicit-null" to org.json.JSONObject.NULL,
            "object" to org.json.JSONObject().put("value", true),
            "array" to org.json.JSONArray().put(true),
        )
        val departures = org.json.JSONArray()
        cases.forEach { (tripId, flag) ->
            val item = org.json.JSONObject()
                .put("tripId", tripId)
                .put("line", "RE")
                .put("direction", "Bremen")
                .put("scheduledEpochSeconds", 1_700_000_000L)
            if (flag != null) item.put("cancelled", flag)
            departures.put(item)
        }
        val plan = org.json.JSONObject()
            .put("key", "offline-cancellation-validation")
            .put("savedAtEpochSeconds", 100L)
            .put("routeOptions", org.json.JSONArray())
            .put("departures", departures)
        assertTrue(prefs.edit().clear().putString(
            "plans_json", org.json.JSONArray().put(plan).toString()
        ).commit())
        try {
            val restored = SharedPreferencesTransitCacheStore(context).load().single().departures
            assertEquals(listOf("cancelled", "not-cancelled", "legacy-missing"),
                restored.map { it.tripId })
            assertEquals(listOf(true, false, false), restored.map { it.cancelled })
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun malformedCachedRealtimeEpochsFallBackToScheduledWithoutDroppingTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val cases: List<Pair<String, Any?>> = listOf(
            "missing" to null,
            "null" to org.json.JSONObject.NULL,
            "string" to "1700000300",
            "boolean" to true,
            "fraction" to 1_700_000_300.25,
            "zero" to 0L,
            "negative" to -1L,
            "overflow" to (Long.MAX_VALUE / 1_000L + 1L),
            "valid" to 1_700_000_300L,
        )
        val departures = org.json.JSONArray()
        cases.forEach { (tripId, realtime) ->
            val item = org.json.JSONObject()
                .put("tripId", tripId)
                .put("line", "RE")
                .put("direction", "Bremen")
                .put("scheduledEpochSeconds", 1_700_000_000L)
            if (realtime != null) item.put("realtimeEpochSeconds", realtime)
            departures.put(item)
        }
        val plan = org.json.JSONObject()
            .put("key", "offline-realtime")
            .put("savedAtEpochSeconds", 100L)
            .put("routeOptions", org.json.JSONArray())
            .put("departures", departures)
        prefs.edit().clear().putString(
            "plans_json", org.json.JSONArray().put(plan).toString()
        ).commit()
        try {
            val restored = SharedPreferencesTransitCacheStore(context).load().single().departures
            assertEquals(cases.map { it.first }, restored.map { it.tripId })
            restored.forEachIndexed { index, departure ->
                assertEquals(1_700_000_000L, departure.scheduledEpochSeconds)
                val expected: Long? = if (cases[index].first == "valid") 1_700_000_300L else null
                assertEquals(expected, departure.realtimeEpochSeconds)
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }


    @Test
    fun corruptOfflineRouteWalkAndTransferCountsAreRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "counts-route",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE",
            direction = "Bremen",
            departure = "10:00",
            arrival = "10:30",
            walkingMinutes = 3,
            transfers = 1,
        )
        try {
            store.save(listOf(CachedTransitPlan("counts", listOf(route), emptyList(), 100L)))
            val original = requireNotNull(prefs.getString("plans_json", null))
            fun restoreWith(field: String, value: Any?): List<RouteOption> {
                val copy = org.json.JSONArray(original)
                val saved = copy.getJSONObject(0).getJSONArray("routeOptions").getJSONObject(0)
                if (value == null) saved.remove(field) else saved.put(field, value)
                prefs.edit().putString("plans_json", copy.toString()).commit()
                return store.load().single().routeOptions
            }
            for (field in listOf("walkingMinutes", "transfers")) {
                for (invalid in listOf(
                    -1, Int.MIN_VALUE, "3", true, 1.5, Int.MAX_VALUE.toLong() + 1L,
                    org.json.JSONObject.NULL,
                )) {
                    assertTrue("Invalid $field=$invalid must be rejected", restoreWith(field, invalid).isEmpty())
                }
                // Missing legacy values retain the historical safe zero default.
                assertEquals(0, if (field == "walkingMinutes") restoreWith(field, null).single().walkingMinutes
                    else restoreWith(field, null).single().transfers)
                assertEquals(1, restoreWith(field, 0).size)
                assertEquals(1, restoreWith(field, 3).size)
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }


    @Test
    fun invalidOfflineSaveEpochDoesNotImpersonateAValidSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val cases: List<Pair<String, Any?>> = listOf(
            "missing" to null,
            "null" to org.json.JSONObject.NULL,
            "string" to "1700000000",
            "boolean" to true,
            "fractional" to 1_700_000_000.5,
            "zero" to 0L,
            "negative" to -1L,
            "overflow" to Long.MAX_VALUE,
            "valid" to 1_700_000_000L,
        )
        val plans = org.json.JSONArray()
        cases.forEach { (key, epoch) ->
            val plan = org.json.JSONObject()
                .put("key", key)
                .put("routeOptions", org.json.JSONArray())
                .put("departures", org.json.JSONArray())
            if (epoch != null) plan.put("savedAtEpochSeconds", epoch)
            plans.put(plan)
        }
        assertTrue(prefs.edit().clear().putString("plans_json", plans.toString()).commit())
        try {
            val restored = SharedPreferencesTransitCacheStore(context).load()
            assertEquals(listOf("valid"), restored.map { it.key })
            assertEquals(1_700_000_000L, restored.single().savedAtEpochSeconds)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun malformedStoredTripIdsRejectRouteWithoutInventingTripIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        try {
            prefs.edit().clear().commit()
            val route = RouteOption(
                id = "trip-id-validation",
                origin = MapPoint(52.665, 8.237, "Lohne"),
                destination = MapPoint(53.083, 8.813, "Bremen"),
                line = "RE",
                direction = "Bremen",
                departure = "10:00",
                arrival = "11:00",
                walkingMinutes = 0,
                transfers = 0,
                tripIds = listOf("trip-valid"),
            )
            store.save(listOf(CachedTransitPlan("trip-id-test", listOf(route), emptyList(), 100L)))
            val original = requireNotNull(prefs.getString("plans_json", null))
            fun restoreWith(value: Any?, remove: Boolean = false): List<RouteOption> {
                val plans = org.json.JSONArray(original)
                val storedRoute = plans.getJSONObject(0)
                    .getJSONArray("routeOptions").getJSONObject(0)
                if (remove) storedRoute.remove("tripIds") else storedRoute.put("tripIds", value)
                prefs.edit().putString("plans_json", plans.toString()).commit()
                return store.load().single().routeOptions
            }
            val invalid = listOf(
                org.json.JSONArray().put(123),
                org.json.JSONArray().put(true),
                org.json.JSONArray().put(org.json.JSONObject.NULL),
                org.json.JSONArray().put("trip-valid").put(false),
                org.json.JSONArray().put(""),
                "trip-valid",
                org.json.JSONObject().put("tripId", "trip-valid"),
            )
            invalid.forEach { value ->
                assertTrue("Malformed tripIds must reject the whole route: $value",
                    restoreWith(value).isEmpty())
            }
            assertEquals(listOf("trip-valid"),
                restoreWith(org.json.JSONArray().put("trip-valid")).single().tripIds)
            assertEquals(emptyList<String>(), restoreWith(null, remove = true).single().tripIds)
            assertEquals(emptyList<String>(), restoreWith(org.json.JSONObject.NULL).single().tripIds)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun corruptedCachedRouteSourceEpochIsRejectedWithoutJsonCoercion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        try {
            prefs.edit().clear().commit()
            val route = RouteOption(
                id = "timestamp-guard",
                origin = MapPoint(52.665, 8.237, "Lohne"),
                destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
                line = "RE", direction = "Bremen", departure = "10:00", arrival = "11:00",
                walkingMinutes = 0, transfers = 0, sourceUpdatedAtEpochSeconds = 1_700_000_000L,
            )
            store.save(listOf(CachedTransitPlan("timestamp", listOf(route), emptyList(), 100L)))
            val original = requireNotNull(prefs.getString("plans_json", null))
            fun restoredWith(value: Any?, remove: Boolean = false): List<RouteOption> {
                val plans = org.json.JSONArray(original)
                val savedRoute = plans.getJSONObject(0).getJSONArray("routeOptions").getJSONObject(0)
                if (remove) savedRoute.remove("sourceUpdatedAtEpochSeconds")
                else savedRoute.put("sourceUpdatedAtEpochSeconds", value)
                assertTrue(prefs.edit().putString("plans_json", plans.toString()).commit())
                return store.load().single().routeOptions
            }
            val invalid: List<Any> = listOf(
                "1700000000", true, -1L, 1.5, Long.MAX_VALUE,
                org.json.JSONObject().put("epoch", 1_700_000_000L),
                org.json.JSONArray().put(1_700_000_000L),
            )
            invalid.forEach { value ->
                assertTrue("Corrupt sourceUpdatedAtEpochSeconds=$value must reject route",
                    restoredWith(value).isEmpty())
            }
            assertEquals(1_700_000_000L,
                restoredWith(1_700_000_000L).single().sourceUpdatedAtEpochSeconds)
            assertEquals(0L, restoredWith(0L).single().sourceUpdatedAtEpochSeconds)
            assertEquals(null,
                restoredWith(org.json.JSONObject.NULL).single().sourceUpdatedAtEpochSeconds)
            assertEquals(null, restoredWith(null, remove = true)
                .single().sourceUpdatedAtEpochSeconds)
        } finally {
            prefs.edit().clear().commit()
        }
    }

}
