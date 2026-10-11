package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCoreReadinessTest {
    private fun state(
        search: CoreReadinessCheck = CoreReadinessCheck.UNCHECKED,
        route: CoreReadinessCheck = CoreReadinessCheck.UNCHECKED,
        data: NationwideDataState = NationwideDataState.Ready(42, 1_000L, "feed-test"),
        locationPermission: Boolean = true,
    ) = SettingsReadinessState(
        themeMode = ArrivalThemeMode.SYSTEM,
        locationPermission = locationPermission,
        notificationPermission = true,
        locationServicesAvailable = true,
        bluetoothPermission = true,
        guidancePermissionState = GuidancePermissionState.GRANTED,
        audioRoute = GuidanceAudioRoute.DEVICE,
        dataState = data,
        backgroundJourneyActive = false,
        connectorConfigured = false,
        connectorConnected = false,
        searchCheck = search,
        routingCheck = route,
    )

    @Test
    fun downloadedIndexAloneCannotProduceFalseReady() {
        assertEquals(ReadinessLevel.INFO, state().overallLevel)
    }

    @Test
    fun failedSearchCannotBeHiddenBySuccessfulRoutingOrReadyData() {
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(
                search = CoreReadinessCheck.NO_USABLE_RESULT,
                route = CoreReadinessCheck.WORKING,
            ).overallLevel,
        )
    }

    @Test
    fun cachedRouteDoesNotPassAsFullyHealthy() {
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(
                search = CoreReadinessCheck.WORKING,
                route = CoreReadinessCheck.DEGRADED,
            ).overallLevel,
        )
    }

    @Test
    fun onlyObservedWorkingSearchAndRoutePassTheCoreReadyGate() {
        assertEquals(
            ReadinessLevel.READY,
            state(
                search = CoreReadinessCheck.WORKING,
                route = CoreReadinessCheck.WORKING,
            ).overallLevel,
        )
    }

    @Test
    fun missingLocationPermissionStillBlocksAndEmptyIndexNeverPasses() {
        assertEquals(
            ReadinessLevel.BLOCKED,
            state(
                search = CoreReadinessCheck.WORKING,
                route = CoreReadinessCheck.WORKING,
                locationPermission = false,
            ).overallLevel,
        )
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(
                search = CoreReadinessCheck.WORKING,
                route = CoreReadinessCheck.WORKING,
                data = NationwideDataState.Ready(0, 1_000L, "empty"),
            ).overallLevel,
        )
    }
}
