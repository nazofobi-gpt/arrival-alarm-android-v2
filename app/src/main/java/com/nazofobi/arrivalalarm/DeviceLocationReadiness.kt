package com.nazofobi.arrivalalarm

import android.location.LocationManager
import android.os.Build

/**
 * Match the active-journey provider selector on pre-P devices. OEM wrappers may
 * vary the casing of provider names, and an enabled non-passive fallback can
 * still supply location updates when GPS/network are unavailable.
 * Android P+ exposes the user's effective location-services toggle directly.
 */
internal fun hasEnabledDeviceLocationProvider(enabledProviders: Collection<String>): Boolean =
    ActiveJourneyLocationProviderSelection.choose(enabledProviders.toList()) != null

/**
 * One fail-closed location-services policy shared by Settings and active-journey startup.
 * Only query the API available on this OS version; unavailable/erroring services are not READY.
 */
internal fun isDeviceLocationEnabledForTracking(
    sdkInt: Int,
    locationEnabled: () -> Boolean,
    enabledProviders: () -> Collection<String>,
): Boolean = try {
    if (sdkInt >= Build.VERSION_CODES.P) locationEnabled()
    else hasEnabledDeviceLocationProvider(enabledProviders())
} catch (_: RuntimeException) {
    false
}

internal fun isDeviceLocationEnabledForTracking(manager: LocationManager): Boolean {
    // Keep the API-28 call inside an explicit Android-version guard so minSdk-23 lint
    // can prove it is never evaluated on older devices.
    val sdkInt = Build.VERSION.SDK_INT
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        isDeviceLocationEnabledForTracking(
            sdkInt = sdkInt,
            locationEnabled = { manager.isLocationEnabled },
            enabledProviders = { emptyList() },
        )
    } else {
        isDeviceLocationEnabledForTracking(
            sdkInt = sdkInt,
            locationEnabled = { false },
            enabledProviders = { manager.getProviders(true) },
        )
    }
}
