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
}
