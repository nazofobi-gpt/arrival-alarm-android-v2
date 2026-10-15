package com.nazofobi.arrivalalarm

import kotlin.math.*
import java.security.MessageDigest

data class LocationFix(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val speedMps: Double,
    val realtimeDeviationSeconds: Int = 0,
    val epochMillis: Long = 0L,
)

data class ArmedRoute(
    val routeId: String,
    val stops: List<GeoPoint>,
    val targetIndex: Int,
) {
    init {
        require(routeId.isNotBlank())
        require(stops.isNotEmpty())
        require(targetIndex in stops.indices)
        // Malformed static GTFS stop coordinates must never enter the armed
        // alarm engine; NaN distances could otherwise suppress arrival/missed events.
        require(stops.all { stop ->
            stop.latitude.isFinite() && stop.latitude in -90.0..90.0 &&
                stop.longitude.isFinite() && stop.longitude in -180.0..180.0
        }) { "Route contains invalid stop coordinates" }
    }

    // Route IDs may remain stable while the selected alarm target or stop
    // sequence changes. Persist a compact identity for the *armed* geometry.
    val persistenceFingerprint: String by lazy {
        val canonical = buildString {
            append(routeId).append('|').append(targetIndex)
            stops.forEach { point ->
                append('|').append(point.latitude.toBits())
                append(',').append(point.longitude.toBits())
            }
        }
        MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }
}

enum class ArrivalAlertPhase { IDLE, TRACKING, APPROACHING, IMMINENT, ARRIVED, MISSED }

data class ArrivalProgressState(
    val routeId: String? = null,
    val armed: Boolean = false,
    val phase: ArrivalAlertPhase = ArrivalAlertPhase.IDLE,
    val lastMatchedStopIndex: Int = 0,
    val progressFraction: Double = 0.0,
    val distanceToTargetMeters: Double? = null,
    val adaptiveThresholdMeters: Double? = null,
    val consecutiveInsideThreshold: Int = 0,
    val consecutivePastTarget: Int = 0,
    val arrivalEventCount: Int = 0,
    val routeFingerprint: String? = null,
)

interface ArrivalProgressStateStore {
    fun load(): ArrivalProgressState?
    fun save(state: ArrivalProgressState)
}

class ArrivalProgressController(
    private val stateStore: ArrivalProgressStateStore? = null,
) {
    var state: ArrivalProgressState = try {
        stateStore?.load() ?: ArrivalProgressState()
    } catch (_: RuntimeException) {
        // Legacy/wrong-typed persisted values must not crash journey restoration.
        ArrivalProgressState()
    }
        private set

    private var route: ArmedRoute? = null
    private var latestAcceptedFixEpochMillis: Long = 0L

    fun arm(route: ArmedRoute) {
        this.route = route
        latestAcceptedFixEpochMillis = 0L
        val restored = state.takeIf {
            it.routeId == route.routeId && it.armed &&
                it.routeFingerprint == route.persistenceFingerprint &&
                it.lastMatchedStopIndex in route.stops.indices &&
                it.progressFraction.isFinite() && it.progressFraction in 0.0..1.0 &&
                it.arrivalEventCount >= 0 &&
                (it.phase != ArrivalAlertPhase.ARRIVED || it.arrivalEventCount > 0)
        }
        setState(
            // A restored debounce counter has no persisted GPS timestamp
            // watermark, so it cannot prove a second independent fix.
            restored?.copy(
                consecutiveInsideThreshold = 0,
                consecutivePastTarget = 0,
            ) ?: ArrivalProgressState(
                routeId = route.routeId,
                armed = true,
                phase = ArrivalAlertPhase.TRACKING,
                routeFingerprint = route.persistenceFingerprint,
            )
        )
    }

    fun disarm() {
        route = null
        latestAcceptedFixEpochMillis = 0L
        setState(ArrivalProgressState())
    }

    fun onLocation(fix: LocationFix): ArrivalProgressState {
        val activeRoute = route
        if (activeRoute == null || !state.armed || state.routeId != activeRoute.routeId) return state
        if (state.phase == ArrivalAlertPhase.ARRIVED) return state

        // Never infer stop progression or arrival from malformed or low-confidence GPS.
        // An unreliable callback also breaks a pending two-fix arrival/missed debounce.
        val point = fix.point
        if (!point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0.0 ||
            fix.accuracyMeters > ArrivalLocationQuality.MAX_TRACKING_ACCURACY_METERS ||
            !fix.speedMps.isFinite() || fix.speedMps < 0.0 ||
            fix.epochMillis < 0L
        ) {
            if (state.consecutiveInsideThreshold != 0 || state.consecutivePastTarget != 0) {
                setState(state.copy(consecutiveInsideThreshold = 0, consecutivePastTarget = 0))
            }
            return state
        }

        // A callback replay or older timestamp is not a second independent GPS fix.
        // Zero means timestamp unavailable; retain the legacy path for those providers.
        if (fix.epochMillis > 0L) {
            if (fix.epochMillis <= latestAcceptedFixEpochMillis) return state
            latestAcceptedFixEpochMillis = fix.epochMillis
        }

        val nearestIndex = activeRoute.stops.indices.minBy { index ->
            distanceMeters(fix.point, activeRoute.stops[index])
        }
        val monotonicIndex = max(state.lastMatchedStopIndex, nearestIndex)
        val denominator = max(1, activeRoute.stops.lastIndex)
        val progress = max(state.progressFraction, monotonicIndex.toDouble() / denominator.toDouble())
        val target = activeRoute.stops[activeRoute.targetIndex]
        val targetDistance = distanceMeters(fix.point, target)
        val threshold = adaptiveThresholdMeters(fix)

        // The entire horizontal uncertainty circle must be inside the arrival radius.
        val upperDistance = targetDistance + fix.accuracyMeters
        // The debounce needs at most one preceding credible fix. A legacy or
        // corrupted persisted counter must not overflow or trigger an arrival
        // from a single new GPS callback.
        val previousInside = state.consecutiveInsideThreshold.takeIf { it in 0..1 } ?: 0
        // A valid but unconfirmed fix must break the two-confirmation streak,
        // including fixes in the uncertainty band near the arrival threshold.
        val insideCount = if (upperDistance <= threshold) {
            previousInside + 1
        } else {
            0
        }

        // Missed-stop evidence must likewise be outside the radius with confidence.
        val clearlyPast = monotonicIndex > activeRoute.targetIndex &&
            targetDistance - fix.accuracyMeters > threshold * 1.20
        val previousPast = state.consecutivePastTarget.takeIf { it in 0..1 } ?: 0
        val pastCount = if (clearlyPast) previousPast + 1 else 0

        val nextPhase = when {
            insideCount >= 2 -> ArrivalAlertPhase.ARRIVED
            pastCount >= 2 -> ArrivalAlertPhase.MISSED
            targetDistance <= threshold * 1.8 -> ArrivalAlertPhase.IMMINENT
            targetDistance <= max(650.0, threshold * 4.0) -> ArrivalAlertPhase.APPROACHING
            else -> ArrivalAlertPhase.TRACKING
        }

        val eventCount = if (nextPhase == ArrivalAlertPhase.ARRIVED && state.phase != ArrivalAlertPhase.ARRIVED) {
            // Event count is persisted as Int; do not wrap to a negative value.
            if (state.arrivalEventCount == Int.MAX_VALUE) Int.MAX_VALUE else state.arrivalEventCount + 1
        } else {
            state.arrivalEventCount
        }

        setState(
            state.copy(
                phase = nextPhase,
                lastMatchedStopIndex = monotonicIndex,
                progressFraction = progress,
                distanceToTargetMeters = targetDistance,
                adaptiveThresholdMeters = threshold,
                consecutiveInsideThreshold = insideCount,
                consecutivePastTarget = pastCount,
                arrivalEventCount = eventCount,
            )
        )
        return state
    }

    fun adaptiveThresholdMeters(fix: LocationFix): Double {
        val accuracyContribution = fix.accuracyMeters.coerceIn(0.0, 100.0) * 0.80
        val speedContribution = fix.speedMps.coerceIn(0.0, 35.0) * 4.0
        val realtimeContribution = abs(fix.realtimeDeviationSeconds.toLong())
            .coerceAtMost(300L) * 0.08
        return (120.0 + accuracyContribution + speedContribution + realtimeContribution)
            .coerceIn(120.0, 320.0)
    }

    private fun setState(newState: ArrivalProgressState) {
        state = newState
        stateStore?.save(newState)
    }

    companion object {
        fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val earthRadius = 6_371_000.0
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
            return 2.0 * earthRadius * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
