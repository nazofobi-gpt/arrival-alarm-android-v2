package com.nazofobi.arrivalalarm

/** Platform-independent evidence from a device's cached location provider. */
internal data class DeviceLocationCandidate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val fixElapsedRealtimeNanos: Long,
    val observedAtMillis: Long,
    val provider: String,
)

/**
 * A cached fix may seed the current-location UI, but an old, invalid, or
 * future-dated fix must never be presented as the user's current position.
 * Use Android's monotonic elapsed-realtime clock, not changeable wall time.
 *
 * Coarse-location accuracy is intentionally allowed: arrival alarm decisions
 * apply their own stricter horizontal-accuracy gate.
 */
internal object DeviceLastKnownFixPolicy {
    const val MAX_FIX_AGE_MILLIS = 5 * 60_000L
    private const val NANOS_PER_MILLI = 1_000_000L

    fun select(
        candidates: Iterable<DeviceLocationCandidate>,
        nowElapsedRealtimeNanos: Long,
    ): DeviceLocationCandidate? {
        if (nowElapsedRealtimeNanos <= 0L) return null
        val maxAgeNanos = MAX_FIX_AGE_MILLIS * NANOS_PER_MILLI
        return candidates.filter { candidate ->
            val accuracy = candidate.accuracyMeters
            candidate.latitude.isFinite() && candidate.latitude in -90.0..90.0 &&
                candidate.longitude.isFinite() && candidate.longitude in -180.0..180.0 &&
                accuracy != null && accuracy.isFinite() && accuracy >= 0f &&
                candidate.observedAtMillis > 0L &&
                candidate.fixElapsedRealtimeNanos > 0L &&
                candidate.fixElapsedRealtimeNanos <= nowElapsedRealtimeNanos &&
                nowElapsedRealtimeNanos - candidate.fixElapsedRealtimeNanos <= maxAgeNanos
        }.maxWithOrNull(
            compareBy<DeviceLocationCandidate> { it.fixElapsedRealtimeNanos }
                .thenBy { -it.accuracyMeters!! }
        )
    }
}
