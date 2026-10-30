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
    fun restoredRoutePointLabelsRejectJsonCoercionAndPreserveLegacyMissingLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "strict-route-label",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE", direction = "Bremen",
            departure = "10:00", arrival = "11:00",
            walkingMinutes = 0, transfers = 0,
        )
        try {
            prefs.edit().clear().commit()
            store.save(listOf(CachedTransitPlan("labels", listOf(route), emptyList(), 100L)))
            val baseline = requireNotNull(prefs.getString("plans_json", null))
            fun restore(pointName: String, value: Any?, remove: Boolean = false): List<RouteOption> {
                val snapshot = org.json.JSONArray(baseline)
                val point = snapshot.getJSONObject(0).getJSONArray("routeOptions")
                    .getJSONObject(0).getJSONObject(pointName)
                if (remove) point.remove("label") else point.put("label", value)
                assertTrue(prefs.edit().putString("plans_json", snapshot.toString()).commit())
                return store.load().single().routeOptions
            }
            for (pointName in listOf("origin", "destination")) {
                for (invalid in listOf<Any>(
                    123, true,
                    org.json.JSONObject().put("label", "Bremen"),
                    org.json.JSONArray().put("Bremen"),
                )) {
                    assertTrue("Coerced $pointName label=$invalid must reject itinerary",
                        restore(pointName, invalid).isEmpty())
                }
                val legacyMissing = restore(pointName, null, remove = true).single()
                assertEquals("", if (pointName == "origin")
                    legacyMissing.origin.label else legacyMissing.destination.label)
                val legacyNull = restore(pointName, org.json.JSONObject.NULL).single()
                assertEquals("", if (pointName == "origin")
                    legacyNull.origin.label else legacyNull.destination.label)
                val valid = restore(pointName, "München Hbf").single()
                assertEquals("München Hbf", if (pointName == "origin")
                    valid.origin.label else valid.destination.label)
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun persistedRouteCoordinatesRejectCoercedJsonTypesWithoutLosingValidNumbers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "coordinate-type-guard",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE", direction = "Bremen", departure = "10:00", arrival = "11:00",
            walkingMinutes = 0, transfers = 0,
            stops = listOf(RouteStop(id = "start", name = "Lohne", latitude = 52.665, longitude = 8.237)),
        )
        try {
            prefs.edit().clear().commit()
            store.save(listOf(CachedTransitPlan("coordinate-test", listOf(route), emptyList(), 100L)))
            val baseline = requireNotNull(prefs.getString("plans_json", null))
            fun restoredWith(change: (org.json.JSONObject) -> Unit): List<RouteOption> {
                val plans = org.json.JSONArray(baseline)
                val stored = plans.getJSONObject(0).getJSONArray("routeOptions").getJSONObject(0)
                change(stored)
                assertTrue(prefs.edit().putString("plans_json", plans.toString()).commit())
                return store.load().single().routeOptions
            }
            assertEquals(1, restoredWith { }.size)
            val invalidTypes: List<Any> = listOf(
                "52.665", true, org.json.JSONArray().put(52.665),
                org.json.JSONObject().put("latitude", 52.665),
            )
            for (field in listOf("latitude", "longitude")) {
                val invalidValues = invalidTypes + if (field == "latitude") 91.0 else 181.0
                invalidValues.forEach { value ->
                    for (point in listOf("origin", "destination")) {
                        assertTrue("Required $point.$field=$value was accepted",
                            restoredWith { it.getJSONObject(point).put(field, value) }.isEmpty())
                    }
                    assertTrue("Invalid route stop $field=$value was silently dropped",
                        restoredWith { it.getJSONArray("stops").getJSONObject(0).put(field, value) }.isEmpty())
                }
            }
            assertTrue(restoredWith {
                it.getJSONObject("origin").put("latitude", org.json.JSONObject.NULL)
            }.isEmpty())
            assertEquals(null, restoredWith {
                it.getJSONArray("stops").getJSONObject(0).put("latitude", org.json.JSONObject.NULL)
            }.single().stops.single().latitude)
            // Zero and geographic boundaries are valid JSON numeric coordinates.
            val valid = restoredWith {
                it.getJSONObject("origin").put("latitude", -90.0).put("longitude", -180.0)
                it.getJSONObject("destination").put("latitude", 90.0).put("longitude", 180.0)
                it.getJSONArray("stops").getJSONObject(0).put("latitude", 0).put("longitude", 0)
            }.single()
            assertEquals(-90.0, valid.origin.latitude, 0.0)
            assertEquals(180.0, valid.destination.longitude, 0.0)
            assertEquals(0.0, valid.stops.single().latitude!!, 0.0)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun wrongTypedTopLevelCacheCollectionsRejectOnlyCorruptedRecord() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val departure = Departure("trip", "RE 9", "Bremen", 1_700_000_000L)
        try {
            prefs.edit().clear().commit()
            store.save(listOf(
                CachedTransitPlan("safe", emptyList(), listOf(departure), 100L),
                CachedTransitPlan("broken", emptyList(), listOf(departure), 100L),
            ))
            val baseline = requireNotNull(prefs.getString("plans_json", null))
            for (field in listOf("routeOptions", "departures")) {
                for (invalid in listOf<Any>(
                    "not-an-array", 42, true,
                    org.json.JSONObject().put("unexpected", true),
                )) {
                    val snapshot = org.json.JSONArray(baseline)
                    snapshot.getJSONObject(1).put(field, invalid)
                    assertTrue(prefs.edit().putString("plans_json", snapshot.toString()).commit())
                    // Never pretend that a present wrong-typed collection is empty.
                    assertEquals(listOf("safe"), store.load().map { it.key })
                }
                // Older records may omit optional collections or encode them as null.
                for (nullOrMissing in listOf(false, true)) {
                    val snapshot = org.json.JSONArray(baseline)
                    val item = snapshot.getJSONObject(1)
                    if (nullOrMissing) item.remove(field)
                    else item.put(field, org.json.JSONObject.NULL)
                    assertTrue(prefs.edit().putString("plans_json", snapshot.toString()).commit())
                    val restored = store.load()
                    assertEquals(listOf("safe", "broken"), restored.map { it.key })
                    val broken = restored.last()
                    if (field == "routeOptions") assertTrue(broken.routeOptions.isEmpty())
                    else assertTrue(broken.departures.isEmpty())
                }
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun wrongTypedRestoredStopNameRejectsRouteWithoutCoercion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "route-stop-name",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
            line = "RE", direction = "Bremen",
            departure = "10:00", arrival = "11:00",
            walkingMinutes = 0, transfers = 0,
            stops = listOf(RouteStop(id = "stop-a", name = "Lohne")),
        )
        try {
            prefs.edit().clear().commit()
            store.save(listOf(
                CachedTransitPlan("valid", listOf(route), emptyList(), 100L),
                CachedTransitPlan("damaged", listOf(route.copy(id = "damaged-route")), emptyList(), 101L),
            ))
            val baseline = requireNotNull(prefs.getString("plans_json", null))
            fun restoreWithStopName(value: Any?): List<CachedTransitPlan> {
                val snapshot = org.json.JSONArray(baseline)
                val stop = snapshot.getJSONObject(1).getJSONArray("routeOptions")
                    .getJSONObject(0).getJSONArray("stops").getJSONObject(0)
                if (value == null) stop.remove("name") else stop.put("name", value)
                assertTrue(prefs.edit().putString("plans_json", snapshot.toString()).commit())
                return store.load()
            }
            val malformed: List<Any?> = listOf(
                null, org.json.JSONObject.NULL, 123, true,
                org.json.JSONArray().put("Lohne"),
                org.json.JSONObject().put("name", "Lohne"), "",
            )
            malformed.forEach { value ->
                val restored = restoreWithStopName(value)
                assertEquals(listOf("valid", "damaged"), restored.map { it.key })
                assertEquals("Lohne", restored.first().routeOptions.single().stops.single().name)
                // Reject the entire corrupted itinerary, not only the invalid stop.
                assertTrue("Coerced stop name accepted: $value", restored.last().routeOptions.isEmpty())
            }
            assertEquals(
                "München Hauptbahnhof",
                restoreWithStopName("München Hauptbahnhof")
                    .last().routeOptions.single().stops.single().name,
            )
        } finally {
            prefs.edit().clear().commit()
        }
    }

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
            "beyond-year-9999" to 253_402_300_800L,
            "year-9999-boundary" to 253_402_300_799L,
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
            assertEquals(listOf("year-9999-boundary", "valid"), loaded.departures.map { it.tripId })
            assertEquals(1_700_000_000L, loaded.departures.single { it.tripId == "valid" }.scheduledEpochSeconds)
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
            "beyond-year-9999" to 253_402_300_800L,
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
                253_402_300_800L,
                org.json.JSONObject().put("epoch", 1_700_000_000L),
                org.json.JSONArray().put(1_700_000_000L),
            )
            invalid.forEach { value ->
                assertTrue("Corrupt sourceUpdatedAtEpochSeconds=$value must reject route",
                    restoredWith(value).isEmpty())
            }
            assertEquals(1_700_000_000L,
                restoredWith(1_700_000_000L).single().sourceUpdatedAtEpochSeconds)
            assertEquals(253_402_300_799L,
                restoredWith(253_402_300_799L).single().sourceUpdatedAtEpochSeconds)
            assertEquals(0L, restoredWith(0L).single().sourceUpdatedAtEpochSeconds)
            assertEquals(null,
                restoredWith(org.json.JSONObject.NULL).single().sourceUpdatedAtEpochSeconds)
            assertEquals(null, restoredWith(null, remove = true)
                .single().sourceUpdatedAtEpochSeconds)
        } finally {
            prefs.edit().clear().commit()
        }
    }


    @Test
    fun cachedMultidayRouteRetainsExactEpochInstantsAfterRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val departure = 1_700_000_000_000L
        val arrival = departure + 25L * 60L * 60L * 1_000L
        val route = RouteOption(
            id = "overnight-25h",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE", direction = "Bremen",
            departure = "23:30", arrival = "00:30",
            walkingMinutes = 0, transfers = 0,
            departureEpochMillis = departure, arrivalEpochMillis = arrival,
        )
        try {
            prefs.edit().clear().commit()
            OfflineTransitCache(backingStore = SharedPreferencesTransitCacheStore(context)).put(
                CachedTransitPlan("overnight", listOf(route), emptyList(), 100L)
            )
            val restored = OfflineTransitCache(
                backingStore = SharedPreferencesTransitCacheStore(context)
            ).routeOptions("overnight").single()
            assertEquals(departure, restored.departureEpochMillis)
            assertEquals(arrival, restored.arrivalEpochMillis)
            assertEquals(25L * 60L, (restored.arrivalEpochMillis!! -
                restored.departureEpochMillis!!) / 60_000L)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun malformedOfflineRouteEpochsRejectOnlyTheCorruptItinerary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val base = 1_700_000_000_000L
        val route = RouteOption(
            id = "valid-timing",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE", direction = "Bremen",
            departure = "23:30", arrival = "00:30",
            walkingMinutes = 0, transfers = 0,
            departureEpochMillis = base, arrivalEpochMillis = base + 90_000_000L,
        )
        try {
            prefs.edit().clear().commit()
            store.save(listOf(CachedTransitPlan("valid", listOf(route), emptyList(), 100L)))
            val original = requireNotNull(prefs.getString("plans_json", null))
            fun restoreModified(field: String, value: Any?, remove: Boolean = false): List<RouteOption> {
                val array = org.json.JSONArray(original)
                val saved = array.getJSONObject(0).getJSONArray("routeOptions").getJSONObject(0)
                if (remove) saved.remove(field) else saved.put(field, value)
                assertTrue(prefs.edit().putString("plans_json", array.toString()).commit())
                return store.load().single().routeOptions
            }
            for (field in listOf("departureEpochMillis", "arrivalEpochMillis")) {
                val invalid: List<Any> = listOf(
                    "1700000000000", true, -1L, 0L, 1.25, Long.MAX_VALUE,
                    org.json.JSONObject().put("epoch", base),
                    org.json.JSONArray().put(base),
                )
                invalid.forEach { value ->
                    assertTrue("Invalid $field=$value must reject route",
                        restoreModified(field, value).isEmpty())
                }
                // Missing/nullable legacy values are intentionally represented as unknown.
                assertEquals(null, if (field == "departureEpochMillis")
                    restoreModified(field, null, remove = true).single().departureEpochMillis
                else restoreModified(field, null, remove = true).single().arrivalEpochMillis)
                assertEquals(null, if (field == "departureEpochMillis")
                    restoreModified(field, org.json.JSONObject.NULL).single().departureEpochMillis
                else restoreModified(field, org.json.JSONObject.NULL).single().arrivalEpochMillis)
                assertEquals(1, restoreModified(field, base + 60_000L).size)
            }
            assertTrue("Arrival before departure cannot be used as route duration",
                restoreModified("arrivalEpochMillis", base - 1L).isEmpty())
            assertEquals(base + 90_000_000L,
                restoreModified("arrivalEpochMillis", base + 90_000_000L)
                    .single().arrivalEpochMillis)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun invalidOptionalOfflineProviderIdentifiersAreRejectedInsteadOfStringified() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "route-1",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE", direction = "Bremen",
            departure = "10:00", arrival = "11:00",
            walkingMinutes = 0, transfers = 0,
            stops = listOf(RouteStop(id = "stop-1", name = "Lohne")),
        )
        val departure = Departure("trip-1", "RE", "Bremen", 1_700_000_000L)
        try {
            prefs.edit().clear().commit()
            store.save(listOf(CachedTransitPlan("identity", listOf(route), listOf(departure), 100L)))
            val baseline = requireNotNull(prefs.getString("plans_json", null))

            fun restore(kind: String, value: Any): CachedTransitPlan {
                val plans = org.json.JSONArray(baseline)
                val plan = plans.getJSONObject(0)
                val target = when (kind) {
                    "refreshToken" -> plan.getJSONArray("routeOptions").getJSONObject(0)
                    "stopId" -> plan.getJSONArray("routeOptions").getJSONObject(0)
                        .getJSONArray("stops").getJSONObject(0)
                    else -> plan.getJSONArray("departures").getJSONObject(0)
                }
                target.put(if (kind == "stopId") "id" else kind, value)
                assertTrue(prefs.edit().putString("plans_json", plans.toString()).commit())
                return store.load().single()
            }

            val invalid = listOf<Any>(
                42, true, org.json.JSONObject().put("id", "spoof"),
                org.json.JSONArray().put("spoof"),
            )
            for (kind in listOf("refreshToken", "stopId", "routeId")) {
                invalid.forEach { value ->
                    val restored = restore(kind, value)
                    if (kind == "routeId") {
                        assertTrue("Invalid routeId=$value must reject departure", restored.departures.isEmpty())
                        assertEquals(1, restored.routeOptions.size)
                    } else {
                        assertTrue("Invalid $kind=$value must reject route", restored.routeOptions.isEmpty())
                        assertEquals(1, restored.departures.size)
                    }
                }
                val valid = restore(kind, "verified-id")
                val actual = when (kind) {
                    "refreshToken" -> valid.routeOptions.single().refreshToken
                    "stopId" -> valid.routeOptions.single().stops.single().id
                    else -> valid.departures.single().routeId
                }
                assertEquals("verified-id", actual)
                val legacy = restore(kind, org.json.JSONObject.NULL)
                assertEquals(1, legacy.routeOptions.size)
                assertEquals(1, legacy.departures.size)
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }


    @Test
    fun malformedScheduledTextCannotBePresentedAsOfflineTimetable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("offline_transit_cache", Context.MODE_PRIVATE)
        val store = SharedPreferencesTransitCacheStore(context)
        val route = RouteOption(
            id = "route-scheduled",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen"),
            line = "RE 9", direction = "Bremen",
            departure = "10:00", arrival = "11:00",
            walkingMinutes = 0, transfers = 0,
            stops = listOf(
                RouteStop(
                    id = "lohne", name = "Lohne",
                    plannedArrival = "09:59", plannedDeparture = "10:00",
                    plannedPlatform = "2",
                )
            ),
        )
        val departure = Departure(
            tripId = "trip-scheduled", line = "RE 9",
            direction = "Bremen", scheduledEpochSeconds = 1_700_000_000L,
        )
        try {
            assertTrue(prefs.edit().clear().commit())
            store.save(listOf(
                CachedTransitPlan("strict-display", listOf(route), listOf(departure), 100L),
            ))
            val baseline = requireNotNull(prefs.getString("plans_json", null))

            fun restore(scope: String, field: String, value: Any): CachedTransitPlan {
                val plans = org.json.JSONArray(baseline)
                val entry = plans.getJSONObject(0)
                val routeJson = entry.getJSONArray("routeOptions").getJSONObject(0)
                val target = when (scope) {
                    "route" -> routeJson
                    "stop" -> routeJson.getJSONArray("stops").getJSONObject(0)
                    else -> entry.getJSONArray("departures").getJSONObject(0)
                }
                target.put(field, value)
                assertTrue(prefs.edit().putString("plans_json", plans.toString()).commit())
                return store.load().single()
            }

            val invalidValues: List<Any> = listOf(
                42, true, org.json.JSONObject().put("fake", "Lohne"),
                org.json.JSONArray().put("fake"),
            )
            val fields = listOf(
                "route" to listOf("line", "direction", "departure", "arrival"),
                "stop" to listOf("plannedArrival", "plannedDeparture", "plannedPlatform"),
                "departure" to listOf("line", "direction"),
            )
            for ((scope, names) in fields) {
                for (field in names) {
                    for (bad in invalidValues) {
                        val result = restore(scope, field, bad)
                        if (scope == "departure") {
                            assertTrue("Wrong-typed departure $field=$bad must be rejected",
                                result.departures.isEmpty())
                            assertEquals(1, result.routeOptions.size)
                        } else {
                            assertTrue("Wrong-typed $scope $field=$bad must reject itinerary",
                                result.routeOptions.isEmpty())
                            assertEquals(1, result.departures.size)
                        }
                    }
                    val good = restore(scope, field, "verified text")
                    assertEquals(1, good.routeOptions.size)
                    assertEquals(1, good.departures.size)
                }
            }

            // Optional planned stop fields can still be absent in legacy records.
            val old = org.json.JSONArray(baseline)
            old.getJSONObject(0).getJSONArray("routeOptions").getJSONObject(0)
                .getJSONArray("stops").getJSONObject(0).remove("plannedPlatform")
            assertTrue(prefs.edit().putString("plans_json", old.toString()).commit())
            assertEquals(null, store.load().single().routeOptions.single()
                .stops.single().plannedPlatform)
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
