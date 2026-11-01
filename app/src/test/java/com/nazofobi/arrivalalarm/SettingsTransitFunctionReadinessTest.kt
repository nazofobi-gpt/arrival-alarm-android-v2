package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTransitFunctionReadinessTest {
    private fun state(
        search: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
        routing: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
        locationGranted: Boolean = true,
        count: Int = 100,
    ): SettingsReadinessState = SettingsReadinessState(
        themeMode = ArrivalThemeMode.SYSTEM,
        locationPermission = locationGranted,
        notificationPermission = true,
        locationServicesAvailable = true,
        bluetoothPermission = true,
        guidancePermissionState = GuidancePermissionState.GRANTED,
        audioRoute = GuidanceAudioRoute.DEVICE,
        dataState = NationwideDataState.Ready(count, 123L, "test-feed"),
        backgroundJourneyActive = false,
        connectorConfigured = false,
        connectorConnected = false,
        searchHealth = search,
        routingHealth = routing,
    )

    @Test fun downloadedIndexAloneDoesNotProveFunctionalReadiness() {
        assertEquals(ReadinessLevel.ACTION_NEEDED, state().overallLevel)
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(search = TransitFunctionHealth.WORKING).overallLevel,
        )
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(routing = TransitFunctionHealth.WORKING).overallLevel,
        )
    }

    @Test fun positiveObservedResultsAreNeededOnBothPaths() {
        assertEquals(
            ReadinessLevel.READY,
            state(
                search = TransitFunctionHealth.WORKING,
                routing = TransitFunctionHealth.WORKING,
            ).overallLevel,
        )
    }

    @Test fun staleOrFailedOperationsCannotClaimReady() {
        for (health in listOf(
            TransitFunctionHealth.UNVERIFIED,
            TransitFunctionHealth.DEGRADED,
            TransitFunctionHealth.FAILED,
        )) {
            assertEquals(
                ReadinessLevel.ACTION_NEEDED,
                state(search = health, routing = TransitFunctionHealth.WORKING).overallLevel,
            )
            assertEquals(
                ReadinessLevel.ACTION_NEEDED,
                state(search = TransitFunctionHealth.WORKING, routing = health).overallLevel,
            )
        }
    }

    @Test fun permissionsAndIndexStillTakePrecedenceOverSuccessfulOperations() {
        assertEquals(
            ReadinessLevel.BLOCKED,
            state(
                search = TransitFunctionHealth.WORKING,
                routing = TransitFunctionHealth.WORKING,
                locationGranted = false,
            ).overallLevel,
        )
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            state(
                search = TransitFunctionHealth.WORKING,
                routing = TransitFunctionHealth.WORKING,
                count = 0,
            ).overallLevel,
        )
    }
}
