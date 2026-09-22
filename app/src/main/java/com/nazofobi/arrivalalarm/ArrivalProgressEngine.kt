package com.nazofobi.arrivalalarm

import kotlin.math.*

enum class ArrivalAlertState {
    IDLE,
    ARMED,
    APPROACHING,
    FINAL_APPROACH,
    ARRIVED,
    MISSED_STOP,
}

data class LocationFix(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val speedMetersPerSecond: Double,
)

data class RouteCheckpoint(
    val id: String,
    val name: String,
    val point: GeoPoint,
)

data class ArrivalProgressSnapshot(
    val routeSignature: String?,
    val targetIndex: Int,
    val maxReachedIndex: Int,
    val minDistanceToTargetMeters: Double?,
    val consecutiveInsideFinalRadius: Int,
    val arrived: Boolean,
    val missedStop: Boolean,
)

data class ArrivalProgressResult(
    val state: ArrivalAlertState,
    val thresholdMeters: Double?,
    val currentRouteIndex: Int?,
    val targetIndex: Int?,
    val distanceToTargetMeters: Double?,
    val shouldFireFinalAlarm: Boolean,
    val status: String,
)

/**
 * Pure, deterministic arrival-progress engine.
 *
 * Android location/foreground-service code can feed fixes into this class without
 * owning alarm semantics. The engine intentionally has no permission or service
 * side effects, which keeps route progress and recovery testable on the JVM.
 */
class ArrivalProgressEngine(
    private val baseRadiusMeters: Double = 140.0,
    private val minRadiusMeters: Double = 100.0,
    private val maxRadiusMeters: Double = 420.0,
    private val approachMultiplier: Double = 3.0,
    private val finalConfirmations: Int = 2,
    private val overshootDistanceGrowthMeters: Double = 120.0,
) {
    private var route: List<RouteCheckpoint> = emptyList()
    private var targetIndex: Int = -1
    private var maxReachedIndex: Int = -1
    private var minDistanceToTargetMeters: Double? = null
    private var consecutiveInsideFinalRadius: Int = 0
    private var arrived: Boolean = false
    private var missedStop: Boolean = false
    private var routeSignature: String? = null

    fun arm(
        checkpoints: List<RouteCheckpoint>,
        targetStopId: String,
        restored: ArrivalProgressSnapshot? = null,
    ): ArrivalProgressResult {
        val resolvedTarget = checkpoints.indexOfFirst { it.id == targetStopId }
        if (checkpoints.isEmpty() || resolvedTarget < 0) {
            clear()
            return idle("Rota/hedef yok; alarm kurulmadı")
        }

        route = checkpoints
        targetIndex = resolvedTarget
        routeSignature = checkpoints.joinToString("|") { it.id }

        if (restored != null && restored.routeSignature == routeSignature && restored.targetIndex == targetIndex) {
            maxReachedIndex = restored.maxReachedIndex.coerceIn(-1, checkpoints.lastIndex)
            minDistanceToTargetMeters = restored.minDistanceToTargetMeters
            consecutiveInsideFinalRadius = restored.consecutiveInsideFinalRadius.coerceAtLeast(0)
            arrived = restored.arrived
            missedStop = restored.missedStop
        } else {
            maxReachedIndex = -1
            minDistanceToTargetMeters = null
            consecutiveInsideFinalRadius = 0
            arrived = false
            missedStop = false
        }

        return ArrivalProgressResult(
            state = when {
                arrived -> ArrivalAlertState.ARRIVED
                missedStop -> ArrivalAlertState.MISSED_STOP
                else -> ArrivalAlertState.ARMED
            },
            thresholdMeters = null,
            currentRouteIndex = maxReachedIndex.takeIf { it >= 0 },
            targetIndex = targetIndex,
            distanceToTargetMeters = minDistanceToTargetMeters,
            shouldFireFinalAlarm = false,
            status = if (arrived) "Varış daha önce doğrulandı" else "Varış takibi hazır",
        )
    }

    fun update(fix: LocationFix): ArrivalProgressResult {
        if (route.isEmpty() || targetIndex !in route.indices) {
            return idle("Aktif rota yok")
        }

        val nearestIndex = route.indices.minByOrNull { index ->
            haversineMeters(fix.point, route[index].point)
        } ?: return idle("Aktif rota yok")
        maxReachedIndex = max(maxReachedIndex, nearestIndex)

        val target = route[targetIndex]
        val distance = haversineMeters(fix.point, target.point)
        val threshold = adaptiveThreshold(fix)
        val previousMin = minDistanceToTargetMeters
        minDistanceToTargetMeters = previousMin?.let { min(it, distance) } ?: distance

        if (arrived) {
            return result(
                ArrivalAlertState.ARRIVED,
                threshold,
                distance,
                fire = false,
                status = "Varış alarmı daha önce üretildi",
            )
        }

        if (!missedStop && maxReachedIndex > targetIndex) {
            missedStop = true
        }
        if (
            !missedStop &&
            previousMin != null &&
            previousMin <= threshold * 1.5 &&
            distance >= previousMin + overshootDistanceGrowthMeters &&
            distance > threshold
        ) {
            missedStop = true
        }
        if (missedStop) {
            consecutiveInsideFinalRadius = 0
            return result(
                ArrivalAlertState.MISSED_STOP,
                threshold,
                distance,
                fire = false,
                status = "Hedef geçilmiş olabilir; güvenli yeniden planlama gerekli",
            )
        }

        if (distance <= threshold) {
            consecutiveInsideFinalRadius += 1
            if (consecutiveInsideFinalRadius >= finalConfirmations) {
                arrived = true
                return result(
                    ArrivalAlertState.ARRIVED,
                    threshold,
                    distance,
                    fire = true,
                    status = "Varış doğrulandı",
                )
            }
            return result(
                ArrivalAlertState.FINAL_APPROACH,
                threshold,
                distance,
                fire = false,
                status = "Varış yarıçapı içinde; doğrulama bekleniyor",
            )
        }

        consecutiveInsideFinalRadius = 0
        val state = if (distance <= threshold * approachMultiplier) {
            ArrivalAlertState.APPROACHING
        } else {
            ArrivalAlertState.ARMED
        }
        return result(
            state,
            threshold,
            distance,
            fire = false,
            status = if (state == ArrivalAlertState.APPROACHING) "Hedefe yaklaşılıyor" else "Rota izleniyor",
        )
    }

    fun snapshot(): ArrivalProgressSnapshot = ArrivalProgressSnapshot(
        routeSignature = routeSignature,
        targetIndex = targetIndex,
        maxReachedIndex = maxReachedIndex,
        minDistanceToTargetMeters = minDistanceToTargetMeters,
        consecutiveInsideFinalRadius = consecutiveInsideFinalRadius,
        arrived = arrived,
        missedStop = missedStop,
    )

    private fun adaptiveThreshold(fix: LocationFix): Double {
        val accuracy = fix.accuracyMeters.coerceAtLeast(0.0)
        val speed = fix.speedMetersPerSecond.coerceAtLeast(0.0)
        return (baseRadiusMeters + accuracy * 1.2 + speed * 6.0)
            .coerceIn(minRadiusMeters, maxRadiusMeters)
    }

    private fun result(
        state: ArrivalAlertState,
        threshold: Double,
        distance: Double,
        fire: Boolean,
        status: String,
    ) = ArrivalProgressResult(
        state = state,
        thresholdMeters = threshold,
        currentRouteIndex = maxReachedIndex,
        targetIndex = targetIndex,
        distanceToTargetMeters = distance,
        shouldFireFinalAlarm = fire,
        status = status,
    )

    private fun idle(status: String) = ArrivalProgressResult(
        state = ArrivalAlertState.IDLE,
        thresholdMeters = null,
        currentRouteIndex = null,
        targetIndex = null,
        distanceToTargetMeters = null,
        shouldFireFinalAlarm = false,
        status = status,
    )

    private fun clear() {
        route = emptyList()
        targetIndex = -1
        maxReachedIndex = -1
        minDistanceToTargetMeters = null
        consecutiveInsideFinalRadius = 0
        arrived = false
        missedStop = false
        routeSignature = null
    }
}

internal fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * earthRadiusMeters * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
