package com.nazofobi.arrivalalarm

/**
 * Fail-closed evidence check for arrival decisions made from Android location callbacks.
 *
 * A reported distance is not proof of arrival when the fix is missing horizontal
 * accuracy, is stale, or its uncertainty circle still extends beyond the arrival
 * radius. The last usable distance may be displayed while the alarm stays armed.
 */
data class ArrivalLocationEvidence(
    val distanceMeters: Double,
    val horizontalAccuracyMeters: Double?,
    val ageMillis: Long?,
)

object ArrivalLocationQuality {
    const val DEFAULT_ARRIVAL_RADIUS_METERS = 250.0
    const val MAX_TRACKING_ACCURACY_METERS = 150.0
    const val MAX_FIX_AGE_MILLIS = 30_000L

    fun isUsableForTracking(evidence: ArrivalLocationEvidence): Boolean {
        val accuracy = evidence.horizontalAccuracyMeters ?: return false
        val age = evidence.ageMillis ?: return false
        return evidence.distanceMeters.isFinite() && evidence.distanceMeters >= 0.0 &&
            accuracy.isFinite() && accuracy >= 0.0 &&
            accuracy <= MAX_TRACKING_ACCURACY_METERS &&
            age in 0L..MAX_FIX_AGE_MILLIS
    }

    fun confirmsArrival(
        evidence: ArrivalLocationEvidence,
        radiusMeters: Double = DEFAULT_ARRIVAL_RADIUS_METERS,
    ): Boolean {
        if (!radiusMeters.isFinite() || radiusMeters <= 0.0) return false
        if (!isUsableForTracking(evidence)) return false
        return evidence.distanceMeters + evidence.horizontalAccuracyMeters!! <= radiusMeters
    }
}
