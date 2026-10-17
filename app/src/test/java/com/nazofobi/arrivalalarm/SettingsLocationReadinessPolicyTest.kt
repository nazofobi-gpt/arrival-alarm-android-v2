package com.nazofobi.arrivalalarm

import android.location.LocationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLocationReadinessPolicyTest {
    @Test
    fun passiveOnlyDoesNotMeanLocationServicesAreAvailable() {
        assertFalse(hasEnabledDeviceLocationProvider(listOf(LocationManager.PASSIVE_PROVIDER)))
    }

    @Test
    fun emptyProviderListIsNotAvailable() {
        assertFalse(hasEnabledDeviceLocationProvider(emptyList()))
    }

    @Test
    fun gpsProviderCanSupportTracking() {
        assertTrue(hasEnabledDeviceLocationProvider(listOf(LocationManager.GPS_PROVIDER)))
    }

    @Test
    fun networkProviderCanSupportTrackingWithoutGps() {
        assertTrue(
            hasEnabledDeviceLocationProvider(
                listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER)
            )
        )
    }
}
