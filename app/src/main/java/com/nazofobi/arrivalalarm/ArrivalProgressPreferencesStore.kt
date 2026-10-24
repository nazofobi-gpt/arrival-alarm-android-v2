package com.nazofobi.arrivalalarm

import android.content.SharedPreferences

class SharedPreferencesArrivalProgressStore(
    private val preferences: SharedPreferences,
) : ArrivalProgressStateStore {
    override fun load(): ArrivalProgressState? {
        return try {
            // Preferences can contain legacy, corrupted, or wrong-typed values. Never
            // allow those values to crash process recreation or seed phantom progress.
            if (!preferences.contains(KEY_ROUTE_ID)) return null
            val routeId = preferences.getString(KEY_ROUTE_ID, null)
                ?.takeIf { it.isNotBlank() } ?: return null
            val phaseName = preferences.getString(KEY_PHASE, ArrivalAlertPhase.IDLE.name)
                ?: return null
            val phase = enumValues<ArrivalAlertPhase>().firstOrNull { it.name == phaseName }
                ?: return null
            val armed = preferences.getBoolean(KEY_ARMED, false)
            // Legacy states have no identity; the controller must rearm fresh.
            val routeFingerprint = preferences.getString(KEY_ROUTE_FINGERPRINT, null)
            val lastIndex = preferences.getInt(KEY_LAST_INDEX, 0)
            val progress = preferences.getFloat(KEY_PROGRESS, 0f).toDouble()
            val distance = preferences.getFloat(KEY_DISTANCE, Float.NaN)
                .takeUnless { it.isNaN() }?.toDouble()
            val threshold = preferences.getFloat(KEY_THRESHOLD, Float.NaN)
                .takeUnless { it.isNaN() }?.toDouble()
            val insideCount = preferences.getInt(KEY_INSIDE_COUNT, 0)
            val pastCount = preferences.getInt(KEY_PAST_COUNT, 0)
            val eventCount = preferences.getInt(KEY_EVENT_COUNT, 0)

            if (lastIndex < 0 || !progress.isFinite() || progress !in 0.0..1.0 ||
                (distance != null && (!distance.isFinite() || distance < 0.0)) ||
                (threshold != null && (!threshold.isFinite() || threshold <= 0.0)) ||
                insideCount < 0 || pastCount < 0 || eventCount < 0 ||
                (armed && phase == ArrivalAlertPhase.IDLE) ||
                (!armed && phase != ArrivalAlertPhase.IDLE)
            ) return null

            ArrivalProgressState(
                routeId = routeId,
                armed = armed,
                phase = phase,
                lastMatchedStopIndex = lastIndex,
                progressFraction = progress,
                distanceToTargetMeters = distance,
                adaptiveThresholdMeters = threshold,
                consecutiveInsideThreshold = insideCount,
                consecutivePastTarget = pastCount,
                arrivalEventCount = eventCount,
                routeFingerprint = routeFingerprint,
            )
        } catch (_: RuntimeException) {
            // Wrong-typed preferences are recoverable; fatal JVM errors must surface.
            null
        }
    }

    override fun save(state: ArrivalProgressState) {
        preferences.edit()
            .putString(KEY_ROUTE_ID, state.routeId)
            .putString(KEY_ROUTE_FINGERPRINT, state.routeFingerprint)
            .putBoolean(KEY_ARMED, state.armed)
            .putString(KEY_PHASE, state.phase.name)
            .putInt(KEY_LAST_INDEX, state.lastMatchedStopIndex)
            .putFloat(KEY_PROGRESS, state.progressFraction.toFloat())
            .putFloat(KEY_DISTANCE, state.distanceToTargetMeters?.toFloat() ?: Float.NaN)
            .putFloat(KEY_THRESHOLD, state.adaptiveThresholdMeters?.toFloat() ?: Float.NaN)
            .putInt(KEY_INSIDE_COUNT, state.consecutiveInsideThreshold)
            .putInt(KEY_PAST_COUNT, state.consecutivePastTarget)
            .putInt(KEY_EVENT_COUNT, state.arrivalEventCount)
            .apply()
    }

    companion object {
        private const val KEY_ROUTE_ID = "route_id"
        private const val KEY_ROUTE_FINGERPRINT = "route_fingerprint"
        private const val KEY_ARMED = "armed"
        private const val KEY_PHASE = "phase"
        private const val KEY_LAST_INDEX = "last_index"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_INSIDE_COUNT = "inside_count"
        private const val KEY_PAST_COUNT = "past_count"
        private const val KEY_EVENT_COUNT = "arrival_event_count"
    }
}
