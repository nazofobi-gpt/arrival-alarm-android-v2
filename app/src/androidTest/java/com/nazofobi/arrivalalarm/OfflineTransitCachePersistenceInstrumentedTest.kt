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
}
