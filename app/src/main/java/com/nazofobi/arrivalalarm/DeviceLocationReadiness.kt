package com.nazofobi.arrivalalarm

import android.location.LocationManager
import android.os.Build

/**
 * A usable tracking location source requires both an enabled device location
 * switch (on Android P+) and a provider that can independently deliver fixes.
 */
internal fun isDeviceLocationEnabledForTracking(
    sdkInt: Int,
    locationEnabled: () -> Boolean,
    enabledProviders: () -> List<String>,
): Boolean = runCatching {
    if (sdkInt >= Build.VERSION_CODES.P && !locationEnabled()) {
        false
    } else {
        ActiveJourneyLocationProviderSelection.choose(enabledProviders()) != null
    }
}.getOrDefault(false)

internal fun isDeviceLocationEnabledForTracking(manager: LocationManager): Boolean {
    val sdkInt = Build.VERSION.SDK_INT
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        isDeviceLocationEnabledForTracking(
            sdkInt = sdkInt,
            locationEnabled = { manager.isLocationEnabled },
            enabledProviders = { manager.getProviders(true) },
        )
    } else {
        isDeviceLocationEnabledForTracking(
            sdkInt = sdkInt,
            locationEnabled = { false },
            enabledProviders = { manager.getProviders(true) },
        )
    }
}
