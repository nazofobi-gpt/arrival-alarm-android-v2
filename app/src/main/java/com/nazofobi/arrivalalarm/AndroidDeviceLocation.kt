package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
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
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = try {
            manager.getProviders(true)
        } catch (_: SecurityException) {
            return null
        }
        val candidates = providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)?.let { location ->
                    DeviceLocationCandidate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                        fixElapsedRealtimeNanos = location.elapsedRealtimeNanos,
                        observedAtMillis = location.time,
                        provider = location.provider ?: provider,
                    )
                }
            } catch (_: SecurityException) {
                null // Permission may have been revoked during the provider read.
            } catch (_: IllegalArgumentException) {
                null // A provider may disappear between enumeration and lookup.
            }
        }
        val best = DeviceLastKnownFixPolicy.select(candidates, SystemClock.elapsedRealtimeNanos())
            ?: return null
        return Fix(
            latitude = best.latitude,
            longitude = best.longitude,
            accuracyMeters = best.accuracyMeters!!,
            observedAtMillis = best.observedAtMillis,
            provider = best.provider,
        )
    }
}
