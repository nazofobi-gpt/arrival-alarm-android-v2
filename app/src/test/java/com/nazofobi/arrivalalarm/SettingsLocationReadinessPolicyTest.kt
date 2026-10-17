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

    @Test
    fun androidPAndLaterRejectsDisabledLocationToggleEvenWithGpsListed() {
        assertFalse(
            isDeviceLocationEnabledForTracking(
                sdkInt = 28,
                locationEnabled = { false },
                enabledProviders = { listOf(LocationManager.GPS_PROVIDER) },
            )
        )
    }

    @Test
    fun androidPAndLaterUsesSystemToggleWithoutQueryingLegacyProviders() {
        assertTrue(
            isDeviceLocationEnabledForTracking(
                sdkInt = 28,
                locationEnabled = { true },
                enabledProviders = { error("provider list should not be read on P+") },
            )
        )
    }

    @Test
    fun preAndroidPRejectsPassiveOnlyWithoutCallingUnavailableToggle() {
        assertFalse(
            isDeviceLocationEnabledForTracking(
                sdkInt = 27,
                locationEnabled = { error("isLocationEnabled unavailable before P") },
                enabledProviders = { listOf(LocationManager.PASSIVE_PROVIDER) },
            )
        )
    }

    @Test
    fun preAndroidPAcceptsNetworkProviderWithoutCallingUnavailableToggle() {
        assertTrue(
            isDeviceLocationEnabledForTracking(
                sdkInt = 27,
                locationEnabled = { error("isLocationEnabled unavailable before P") },
                enabledProviders = { listOf(LocationManager.NETWORK_PROVIDER) },
            )
        )
    }

    @Test
    fun providerQueryFailureFailsClosed() {
        assertFalse(
            isDeviceLocationEnabledForTracking(
                sdkInt = 27,
                locationEnabled = { true },
                enabledProviders = { throw SecurityException("providers unavailable") },
            )
        )
    }

    @Test
    fun systemToggleQueryFailureFailsClosed() {
        assertFalse(
            isDeviceLocationEnabledForTracking(
                sdkInt = 36,
                locationEnabled = { throw SecurityException("location unavailable") },
                enabledProviders = { listOf(LocationManager.GPS_PROVIDER) },
            )
        )
    }

    @Test(expected = AssertionError::class)
    fun fatalLocationToggleErrorMustReachCaller() {
        isDeviceLocationEnabledForTracking(
            sdkInt = 36,
            locationEnabled = { throw AssertionError("fatal location toggle") },
            enabledProviders = { listOf(LocationManager.GPS_PROVIDER) },
        )
    }

    @Test(expected = AssertionError::class)
    fun fatalLegacyProviderErrorMustReachCaller() {
        isDeviceLocationEnabledForTracking(
            sdkInt = 27,
            locationEnabled = { false },
            enabledProviders = { throw AssertionError("fatal provider query") },
        )
    }
}
