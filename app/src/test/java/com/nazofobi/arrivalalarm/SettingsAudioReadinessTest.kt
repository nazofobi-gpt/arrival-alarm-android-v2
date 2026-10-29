package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAudioReadinessTest {
    private fun readiness(
        bluetoothPermission: Boolean,
        guidancePermission: GuidancePermissionState,
        route: GuidanceAudioRoute,
    ) = SettingsReadinessState(
        themeMode = ArrivalThemeMode.SYSTEM,
        locationPermission = true,
        notificationPermission = true,
        locationServicesAvailable = true,
        bluetoothPermission = bluetoothPermission,
        guidancePermissionState = guidancePermission,
        audioRoute = route,
        dataState = NationwideDataState.Ready(10, 1_700_000_000L, "test-feed"),
        backgroundJourneyActive = false,
        connectorConfigured = false,
        connectorConnected = false,
        searchHealth = TransitFunctionHealth.WORKING,
        routingHealth = TransitFunctionHealth.WORKING,
    )

    @Test fun missingOptionalBluetoothPermissionDoesNotBlockDeviceSpeakerReadiness() {
        for (route in listOf(GuidanceAudioRoute.DEVICE, GuidanceAudioRoute.BLUETOOTH_DISCONNECTED)) {
            val state = readiness(false, GuidancePermissionState.PARTIAL, route)
            assertTrue(hasUsableGuidanceAudioPath(state))
            assertEquals(ReadinessLevel.READY, state.overallLevel)
        }
    }

    @Test fun connectedBluetoothRouteWithoutPermissionCannotClaimReady() {
        val state = readiness(
            false, GuidancePermissionState.PARTIAL, GuidanceAudioRoute.BLUETOOTH_CONNECTED
        )
        assertFalse(hasUsableGuidanceAudioPath(state))
        assertEquals(ReadinessLevel.ACTION_NEEDED, state.overallLevel)
    }

    @Test fun deniedCoreGuidanceRemainsNotReadyEvenOnDeviceRoute() {
        val state = readiness(false, GuidancePermissionState.DENIED, GuidanceAudioRoute.DEVICE)
        assertFalse(hasUsableGuidanceAudioPath(state))
        assertEquals(ReadinessLevel.ACTION_NEEDED, state.overallLevel)
    }

    @Test fun connectedBluetoothWithPermissionCanBeReady() {
        val state = readiness(true, GuidancePermissionState.GRANTED, GuidanceAudioRoute.BLUETOOTH_CONNECTED)
        assertEquals(ReadinessLevel.READY, state.overallLevel)
    }

    @Test fun deviceSpeakerFallbackCannotHideSearchOrSystemLocationFailures() {
        val fallback = readiness(false, GuidancePermissionState.PARTIAL, GuidanceAudioRoute.DEVICE)
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            fallback.copy(searchHealth = TransitFunctionHealth.FAILED).overallLevel,
        )
        assertEquals(
            ReadinessLevel.ACTION_NEEDED,
            fallback.copy(routingHealth = TransitFunctionHealth.UNVERIFIED).overallLevel,
        )
        assertEquals(
            ReadinessLevel.BLOCKED,
            fallback.copy(locationServicesAvailable = false).overallLevel,
        )
    }
}
