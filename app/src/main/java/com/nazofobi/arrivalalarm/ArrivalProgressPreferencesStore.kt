package com.nazofobi.arrivalalarm

import android.content.SharedPreferences

class SharedPreferencesArrivalProgressStore(
    private val preferences: SharedPreferences,
) : ArrivalProgressStateStore {
    override fun load(): ArrivalProgressState? {
        if (!preferences.contains(KEY_ROUTE_ID)) return null
        val routeId = preferences.getString(KEY_ROUTE_ID, null) ?: return null
        val phase = runCatching {
            ArrivalAlertPhase.valueOf(preferences.getString(KEY_PHASE, ArrivalAlertPhase.IDLE.name)!!)
        }.getOrDefault(ArrivalAlertPhase.IDLE)
        return ArrivalProgressState(
            routeId = routeId,
            armed = preferences.getBoolean(KEY_ARMED, false),
            phase = phase,
            lastMatchedStopIndex = preferences.getInt(KEY_LAST_INDEX, 0),
            progressFraction = preferences.getFloat(KEY_PROGRESS, 0f).toDouble(),
            distanceToTargetMeters = preferences.getFloat(KEY_DISTANCE, Float.NaN)
                .takeUnless { it.isNaN() }?.toDouble(),
            adaptiveThresholdMeters = preferences.getFloat(KEY_THRESHOLD, Float.NaN)
                .takeUnless { it.isNaN() }?.toDouble(),
            consecutiveInsideThreshold = preferences.getInt(KEY_INSIDE_COUNT, 0),
            consecutivePastTarget = preferences.getInt(KEY_PAST_COUNT, 0),
            arrivalEventCount = preferences.getInt(KEY_EVENT_COUNT, 0),
        )
    }

    override fun save(state: ArrivalProgressState) {
        preferences.edit()
            .putString(KEY_ROUTE_ID, state.routeId)
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
