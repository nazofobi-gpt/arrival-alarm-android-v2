package com.nazofobi.arrivalalarm

import kotlin.math.*

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
)

interface ArrivalProgressStateStore {
    fun load(): ArrivalProgressState?
    fun save(state: ArrivalProgressState)
}

class ArrivalProgressController(
    private val stateStore: ArrivalProgressStateStore? = null,
) {
    var state: ArrivalProgressState = stateStore?.load() ?: ArrivalProgressState()
        private set

    private var route: ArmedRoute? = null

    fun arm(route: ArmedRoute) {
        this.route = route
        val restored = state.takeIf { it.routeId == route.routeId && it.armed }
        setState(
            restored ?: ArrivalProgressState(
                routeId = route.routeId,
                armed = true,
                phase = ArrivalAlertPhase.TRACKING,
            )
        )
    }

    fun disarm() {
        route = null
        setState(ArrivalProgressState())
    }

    fun onLocation(fix: LocationFix): ArrivalProgressState {
        val activeRoute = route
        if (activeRoute == null || !state.armed || state.routeId != activeRoute.routeId) return state
        if (state.phase == ArrivalAlertPhase.ARRIVED) return state
        // Ignore missing/implausible sensor evidence rather than counting it toward
        // the two-fix arrival debounce. A multi-kilometre accuracy circle is not
        // proof that the rider reached the target, even when its centre is nearby.
        if (!fix.point.latitude.isFinite() || fix.point.latitude !in -90.0..90.0 ||
            !fix.point.longitude.isFinite() || fix.point.longitude !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() ||
            fix.accuracyMeters !in 0.0..ArrivalLocationQuality.MAX_TRACKING_ACCURACY_METERS ||
            !fix.speedMps.isFinite() || fix.speedMps < 0.0
        ) return state

        val nearestIndex = activeRoute.stops.indices.minBy { index ->
            distanceMeters(fix.point, activeRoute.stops[index])
        }
        val monotonicIndex = max(state.lastMatchedStopIndex, nearestIndex)
        val denominator = max(1, activeRoute.stops.lastIndex)
        val progress = max(state.progressFraction, monotonicIndex.toDouble() / denominator.toDouble())
        val target = activeRoute.stops[activeRoute.targetIndex]
        val targetDistance = distanceMeters(fix.point, target)
        val threshold = adaptiveThresholdMeters(fix)

        val insideCount = when {
            targetDistance <= threshold -> state.consecutiveInsideThreshold + 1
            targetDistance >= threshold * 1.20 -> 0
            else -> state.consecutiveInsideThreshold
        }

        val clearlyPast = monotonicIndex > activeRoute.targetIndex && targetDistance > threshold * 1.20
        val pastCount = if (clearlyPast) state.consecutivePastTarget + 1 else 0

        val nextPhase = when {
            insideCount >= 2 -> ArrivalAlertPhase.ARRIVED
            pastCount >= 2 -> ArrivalAlertPhase.MISSED
            targetDistance <= threshold * 1.8 -> ArrivalAlertPhase.IMMINENT
            targetDistance <= max(650.0, threshold * 4.0) -> ArrivalAlertPhase.APPROACHING
            else -> ArrivalAlertPhase.TRACKING
        }

        val eventCount = if (nextPhase == ArrivalAlertPhase.ARRIVED && state.phase != ArrivalAlertPhase.ARRIVED) {
            state.arrivalEventCount + 1
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
        // Promote before abs: abs(Int.MIN_VALUE) would otherwise overflow.
        val realtimeContribution = abs(fix.realtimeDeviationSeconds.toLong())
            .coerceAtMost(300L).toDouble() * 0.08
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
