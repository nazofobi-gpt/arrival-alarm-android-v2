package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReadinessStateTest {
    private fun state(
        search: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
        route: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
        locationGranted: Boolean = true,
    ): SettingsReadinessState = SettingsReadinessState(
        themeMode = ArrivalThemeMode.SYSTEM,
        locationPermission = locationGranted,
        notificationPermission = true,
        locationServicesAvailable = true,
        bluetoothPermission = true,
        guidancePermissionState = GuidancePermissionState.GRANTED,
        audioRoute = GuidanceAudioRoute.DEVICE,
        dataState = NationwideDataState.Ready(100, 123L, "test-feed"),
        backgroundJourneyActive = false,
        connectorConfigured = false,
        connectorConnected = false,
        searchHealth = search,
        routingHealth = route,
    )

    @Test fun localIndexReadyDoesNotProveSearchAndRoutingHealth() {
        assertEquals(ReadinessLevel.ACTION_NEEDED, state().overallLevel)
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(search = TransitFunctionHealth.WORKING).overallLevel,
        )
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(route = TransitFunctionHealth.WORKING).overallLevel,
        )
    }

    @Test fun bothUsableFunctionalPathsAreNeededForOverallReady() {
        assertEquals(
            ReadinessLevel.READY,
            state(
                search = TransitFunctionHealth.WORKING,
                route = TransitFunctionHealth.WORKING,
            ).overallLevel,
        )
    }

    @Test fun staleOrFailedPathCannotProduceFalseReady() {
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(
                search = TransitFunctionHealth.DEGRADED,
                route = TransitFunctionHealth.WORKING,
            ).overallLevel,
        )
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(
                search = TransitFunctionHealth.WORKING,
                route = TransitFunctionHealth.FAILED,
            ).overallLevel,
        )
    }

    @Test fun missingCriticalPermissionStillBlocksEvenWhenFunctionsWorked() {
        assertEquals(
            ReadinessLevel.BLOCKED,
            state(
                search = TransitFunctionHealth.WORKING,
                route = TransitFunctionHealth.WORKING,
                locationGranted = false,
            ).overallLevel,
        )
    }
}
