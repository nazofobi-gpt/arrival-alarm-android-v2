package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** Production device-location adapter. Never fabricates coordinates when permission/provider data is absent. */
class AndroidDeviceLocation(private val context: Context) {
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val observedAtMillis: Long,
        val provider: String,
    )

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun lastKnownFix(): Fix? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            .maxWithOrNull(compareBy<Location> { it.time }.thenBy { -it.accuracy })
            ?.let { location ->
                Fix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    observedAtMillis = location.time,
                    provider = location.provider ?: "unknown",
                )
            }
    }
}
