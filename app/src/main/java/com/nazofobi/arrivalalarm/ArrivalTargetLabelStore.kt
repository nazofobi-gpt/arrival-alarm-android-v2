package com.nazofobi.arrivalalarm

import android.content.Context

/**
 * Optional local display label for a user-validated arrival destination.
 *
 * Destination coordinates in JourneyController remain authoritative. This cache
 * never arms or changes an alarm and never restores a label for another target.
 */
internal class ArrivalTargetLabelStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "arrival_target_label_v1", Context.MODE_PRIVATE,
    )

    fun save(point: MapPoint): Boolean {
        val name = point.label.trim()
        if (!validCoordinates(point.latitude, point.longitude) || !safeName(name)) {
            clear()
            return false
        }
        return try {
            preferences.edit()
                .putString(NAME, name)
                .putLong(LAT, point.latitude.toBits())
                .putLong(LON, point.longitude.toBits())
                .commit()
        } catch (_: RuntimeException) {
            false
        }
    }

    fun labelFor(target: GeoPoint?): String? {
        if (target == null || !validCoordinates(target.latitude, target.longitude)) return null
        return try {
            if (!preferences.contains(NAME) || !preferences.contains(LAT) ||
                !preferences.contains(LON) ||
                preferences.getLong(LAT, 0L) != target.latitude.toBits() ||
                preferences.getLong(LON, 0L) != target.longitude.toBits()
            ) return null
            preferences.getString(NAME, null)?.takeIf(::safeName)
        } catch (_: RuntimeException) {
            null
        }
    }

    fun clear() {
        try {
            preferences.edit().clear().commit()
        } catch (_: RuntimeException) {
            // Display labels are optional; clearing failure cannot affect the alarm.
        }
    }

    private fun safeName(value: String): Boolean =
        value.isNotBlank() && value.length <= 100 &&
            value.none { char ->
                Character.isISOControl(char) ||
                    char.code in 0x202A..0x202E ||
                    char.code in 0x2066..0x2069
            }

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0

    private companion object {
        const val NAME = "name"
        const val LAT = "latitude_bits"
        const val LON = "longitude_bits"
    }
}
