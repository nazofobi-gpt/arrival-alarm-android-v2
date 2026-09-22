package com.nazofobi.arrivalalarm

interface ArrivalProgressSnapshotStore {
    fun load(): ArrivalProgressSnapshot?
    fun save(snapshot: ArrivalProgressSnapshot)
    fun clear()
}

interface LocationFixSource {
    fun start(listener: (LocationFix) -> Unit)
    fun stop()
}

/**
 * Bridges the pure arrival engine to app lifecycle without owning Android
 * permission/foreground-service policy. That policy remains in the platform layer.
 */
class ArrivalTripController(
    private val engine: ArrivalProgressEngine,
    private val snapshotStore: ArrivalProgressSnapshotStore,
    private val output: ArrivalAlertOutput,
) {
    private var session: ArrivalTrackingSession? = null

    fun arm(checkpoints: List<RouteCheckpoint>, targetStopId: String): ArrivalProgressResult {
        val result = engine.arm(checkpoints, targetStopId, snapshotStore.load())
        snapshotStore.save(engine.snapshot())
        session = ArrivalTrackingSession(engine, snapshotStore::save, output)
        return result
    }

    fun onLocation(fix: LocationFix): ArrivalProgressResult =
        session?.onLocation(fix)
            ?: ArrivalProgressResult(
                state = ArrivalAlertState.IDLE,
                thresholdMeters = null,
                currentRouteIndex = null,
                targetIndex = null,
                distanceToTargetMeters = null,
                shouldFireFinalAlarm = false,
                status = "Alarm kurulmadan konum işlenmedi",
            )

    fun clear() {
        snapshotStore.clear()
        session = null
    }
}

/** Idempotent lifecycle boundary: repeated start/stop calls never duplicate a subscription. */
class ArrivalLocationLifecycle(
    private val source: LocationFixSource,
    private val controller: ArrivalTripController,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        source.start(controller::onLocation)
    }

    fun stop() {
        if (!started) return
        started = false
        source.stop()
    }
}

/**
 * Converts the canonical static/realtime itinerary to progress checkpoints through
 * an explicit geometry resolver. Realtime overlays do not rewrite route geometry.
 */
fun TransitJourney.toArrivalCheckpoints(
    resolvePoint: (TransitStop) -> GeoPoint?,
): List<RouteCheckpoint> =
    legs.asSequence()
        .flatMap { it.stops.asSequence() }
        .distinctBy { it.id }
        .mapNotNull { stop ->
            resolvePoint(stop)?.let { point -> RouteCheckpoint(stop.id, stop.name, point) }
        }
        .toList()
