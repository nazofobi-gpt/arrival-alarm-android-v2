package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaleRouteCallbackUiInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    private var priorOnboarding = false
    private var existedOnboarding = false

    @Before fun before() {
        existedOnboarding = prefs.contains("onboarding_complete")
        priorOnboarding = prefs.getBoolean("onboarding_complete", false)
        prefs.edit().putBoolean("onboarding_complete", true).commit()
        context.getSharedPreferences("journey_state", Context.MODE_PRIVATE).edit().clear().commit()
        ArrivalAlarmRuntimeGraph.resetForTests()
    }

    @After fun after() {
        if (existedOnboarding) {
            prefs.edit().putBoolean("onboarding_complete", priorOnboarding).commit()
        } else {
            prefs.edit().remove("onboarding_complete").commit()
        }
        ArrivalAlarmRuntimeGraph.resetForTests()
    }

    @Test fun oldJourneyResponseCannotOverwriteSecondDestinationInRealComposeScreen() {
        val transit = DelayedTransit()
        rule.setContent { ArrivalAlarmApp(transitOverride = transit) }
        rule.onNodeWithTag("nav-search").performClick()
        selectAddress("Lohne")
        selectAddress("Achim")
        rule.runOnIdle { assertEquals(listOf("Achim"), transit.requestedDestinations) }
        rule.onNodeWithTag("nav-search").performClick()
        selectAddress("Bremen")
        rule.runOnIdle {
            assertEquals(listOf("Achim", "Bremen"), transit.requestedDestinations)
            transit.pendingJourneys[1](emptyList(), "BREMEN-CURRENT")
            transit.pendingJourneys[0](emptyList(), "ACHIM-STALE")
        }
        rule.onNodeWithTag("route-status").assertTextContains("BREMEN-CURRENT")
    }

    private fun selectAddress(name: String) {
        rule.onNodeWithTag("catalog-search").performTextReplacement(name)
        rule.onNodeWithTag("catalog-search-submit").performClick()
        rule.onNodeWithTag("search-result-0").performClick()
    }

    private class DelayedTransit : ArrivalTransitClient {
        val requestedDestinations = mutableListOf<String>()
        val pendingJourneys = mutableListOf<(List<RouteOption>, String) -> Unit>()

        override fun currentState() = NationwideDataState.Idle
        override fun ensureNationwideIndex(callback: (NationwideDataState) -> Unit) {
            callback(NationwideDataState.Idle)
        }
        override fun searchLocationsResolved(
            query: String,
            limit: Int,
            callback: (NationwideLookupResolution<TransitLocationResult>) -> Unit,
        ) {
            val place = when (query.trim().lowercase()) {
                "lohne" -> MapPoint(52.665, 8.237, "Lohne")
                "achim" -> MapPoint(52.700, 8.400, "Achim")
                "bremen" -> MapPoint(53.080, 8.810, "Bremen")
                else -> null
            }
            val result: NationwideLookupResolution<TransitLocationResult> =
                if (place == null) NationwideLookupResolution.NoResult()
                else NationwideLookupResolution.Results(
                    values = listOf(
                        TransitLocationResult(
                            key = "test:${place.label}",
                            kind = TransitLocationKind.ADDRESS,
                            label = place.label,
                            latitude = place.latitude,
                            longitude = place.longitude,
                        )
                    ),
                    sourceLabel = "test-fixture",
                )
            callback(result)
        }
        override fun nearbyStopsResolved(
            latitude: Double,
            longitude: Double,
            limit: Int,
            callback: (NationwideLookupResolution<NearbyStop>) -> Unit,
        ) {
            callback(NationwideLookupResolution.NoResult())
        }
        override fun stationDepartures(
            stop: CatalogStop,
            limit: Int,
            callback: (StationDeparturesSnapshot) -> Unit,
        ) {
            callback(StationDeparturesSnapshot())
        }
        override fun journeyOptions(
            origin: MapPoint,
            destination: MapPoint,
            limit: Int,
            callback: (List<RouteOption>, String) -> Unit,
        ) {
            requestedDestinations.add(destination.label)
            pendingJourneys.add(callback)
        }
        override fun close() {}
    }
}
