package com.nazofobi.arrivalalarm

/**
 * Adapter from the connector contract to the app's existing journey state machine.
 * It owns only connector-facing metadata; authoritative journey phase/start/destination/alarm
 * transitions remain in [JourneyController].
 */
class JourneyConnectorActionPort(
    private val controller: JourneyController,
    private val routeResolver: (String) -> RouteOption? = { null },
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val staleAfterSeconds: Long = 120,
    private val progressTracker: RouteStopProgressTracker = RouteStopProgressTracker(),
    private val onAlarmArmed: () -> Boolean = { true },
    private val onAlarmCancelled: () -> Unit = {},
) : ConnectorActionPort {
    private var stateVersion = 1L
    private var sourceUpdatedAtEpochSeconds = nowEpochSeconds()
    private var origin: ConnectorStopState? = controller.state.start?.let {
        ConnectorStopState(name = "Başlangıç", latitude = it.latitude, longitude = it.longitude)
    }
    private var destination: ConnectorStopState? = controller.state.destination?.let {
        ConnectorStopState(name = "Varış", latitude = it.latitude, longitude = it.longitude)
    }
    private var selectedRoute: RouteOption? = null
    private var activeTrip: ConnectorTripState? = null

    @Synchronized
    override fun readSnapshot(): ArrivalAlarmConnectorSnapshot {
        val now = nowEpochSeconds()
        refreshTimedTripProgress(now)
        // Reading the local controller is an authoritative device-side observation.
        // Refresh freshness without changing stateVersion so an idle-but-connected app
        // can still accept a version-matched remote command.
        sourceUpdatedAtEpochSeconds = maxOf(sourceUpdatedAtEpochSeconds, now)
        val state = controller.state
        val trip = activeTrip?.copy(
            origin = activeTrip?.origin ?: origin,
            destination = activeTrip?.destination ?: destination,
        )
        return ArrivalAlarmConnectorSnapshot(
            stateVersion = stateVersion,
            capturedAtEpochSeconds = now,
            sourceUpdatedAtEpochSeconds = sourceUpdatedAtEpochSeconds,
            staleAfterSeconds = staleAfterSeconds,
            source = "arrival-alarm-domain",
            journeyPhase = state.phase,
            activeTrip = trip,
            distanceToDestinationMeters = state.distanceMeters,
            alarmArmed = state.phase == JourneyPhase.ARMED,
        )
    }

    @Synchronized
    override fun setOrigin(point: MapPoint): ConnectorActionOutcome {
        controller.selectStart(GeoPoint(point.latitude, point.longitude))
        origin = point.toConnectorStop()
        destination = null
        selectedRoute = null
        activeTrip = null
        markChanged()
        return ConnectorActionOutcome(true, "Başlangıç güncellendi")
    }

    @Synchronized
    override fun setDestination(point: MapPoint): ConnectorActionOutcome {
        if (controller.state.start == null) {
            return ConnectorActionOutcome(false, "Önce başlangıç seçilmeli")
        }
        controller.selectDestination(GeoPoint(point.latitude, point.longitude))
        if (controller.state.phase != JourneyPhase.DESTINATION_SELECTED) {
            return ConnectorActionOutcome(false, controller.state.error ?: "Varış güncellenemedi")
        }
        destination = point.toConnectorStop()
        selectedRoute = null
        activeTrip = null
        markChanged()
        return ConnectorActionOutcome(true, "Varış güncellendi")
    }

    @Synchronized
    override fun selectJourney(routeId: String): ConnectorActionOutcome {
        val route = routeResolver(routeId)
            ?: return ConnectorActionOutcome(false, "Rota artık mevcut değil; güncel rota seçilmeli")
        val currentOrigin = origin ?: route.origin.toConnectorStop()
        val currentDestination = destination ?: route.destination.toConnectorStop()
        selectedRoute = route
        activeTrip = ConnectorTripState(
            journeyId = route.id,
            tripId = route.tripIds.firstOrNull() ?: route.id,
            routeId = route.id,
            line = route.line,
            direction = route.direction,
            origin = currentOrigin,
            destination = currentDestination,
            progressSource = if (route.stops.isNotEmpty()) "transport.rest-stopovers" else null,
            progressUpdatedAtEpochSeconds = route.sourceUpdatedAtEpochSeconds,
            scheduledArrivalEpochSeconds = route.stops.lastOrNull()
                ?.plannedArrival.toTransitEpochSecondsOrNull(),
            estimatedArrivalEpochSeconds = route.stops.lastOrNull()?.let { stop ->
                stop.arrival.toTransitEpochSecondsOrNull()
                    ?: stop.plannedArrival.toTransitEpochSecondsOrNull()
            },
        )
        origin = currentOrigin
        destination = currentDestination
        refreshTimedTripProgress(nowEpochSeconds())
        markChanged()
        return ConnectorActionOutcome(true, "Sefer seçildi")
    }

    @Synchronized
    override fun setBoardingStop(stop: ConnectorStopState): ConnectorActionOutcome {
        val trip = activeTrip ?: return ConnectorActionOutcome(false, "Önce sefer seçilmeli")
        activeTrip = trip.copy(boardingStop = stop)
        markChanged()
        return ConnectorActionOutcome(true, "Biniş durağı güncellendi")
    }

    @Synchronized
    override fun armArrivalAlarm(): ConnectorActionOutcome {
        controller.arm()
        if (controller.state.phase != JourneyPhase.ARMED) {
            return ConnectorActionOutcome(false, controller.state.error ?: "Alarm kurulamadı")
        }
        if (!onAlarmArmed()) {
            controller.cancelAlarm()
            return ConnectorActionOutcome(
                false,
                "Arka plan konum takibi başlatılamadı; alarm kurulmadı",
            )
        }
        markChanged()
        return ConnectorActionOutcome(true, "Varış alarmı kuruldu")
    }

    @Synchronized
    override fun cancelArrivalAlarm(): ConnectorActionOutcome {
        if (controller.state.phase != JourneyPhase.ARMED && controller.state.phase != JourneyPhase.ARRIVED) {
            return ConnectorActionOutcome(false, "Kurulu varış alarmı yok")
        }
        controller.cancelAlarm()
        if (controller.state.phase != JourneyPhase.DESTINATION_SELECTED) {
            return ConnectorActionOutcome(false, controller.state.error ?: "Alarm iptal edilemedi")
        }
        onAlarmCancelled()
        markChanged()
        return ConnectorActionOutcome(true, "Varış alarmı iptal edildi")
    }

    @Synchronized
    fun updateDistanceToDestination(distanceMeters: Double): ConnectorActionOutcome {
        if (!distanceMeters.isFinite() || distanceMeters < 0.0) {
            return ConnectorActionOutcome(false, "Geçersiz hedef mesafesi")
        }
        val before = controller.state
        controller.onDistanceChanged(distanceMeters)
        val after = controller.state
        if (after == before) {
            return ConnectorActionOutcome(false, "Aktif varış alarmı yok")
        }
        markChanged()
        return ConnectorActionOutcome(
            true,
            if (after.phase == JourneyPhase.ARRIVED) "Varış eşiğine ulaşıldı" else "Hedef mesafesi güncellendi",
        )
    }

    @Synchronized
    fun updateTripProgress(
        previousStop: ConnectorStopState?,
        currentStop: ConnectorStopState?,
        nextStop: ConnectorStopState?,
    ): ConnectorActionOutcome {
        val trip = activeTrip ?: return ConnectorActionOutcome(false, "Aktif sefer yok")
        activeTrip = trip.copy(
            previousStop = previousStop,
            currentStop = currentStop,
            nextStop = nextStop,
        )
        markChanged()
        return ConnectorActionOutcome(true, "Sefer ilerlemesi güncellendi")
    }

    private fun refreshTimedTripProgress(nowEpochSeconds: Long) {
        val route = selectedRoute ?: return
        val trip = activeTrip ?: return
        val progress = progressTracker.resolve(route.stops, nowEpochSeconds)
        if (!progress.resolvedByProviderTime) return

        val updated = trip.copy(
            previousStop = progress.previous?.toConnectorStop(),
            currentStop = progress.current?.toConnectorStop(),
            nextStop = progress.next?.toConnectorStop(),
            timingBasis = progress.timingBasis.name,
            progressSource = "transport.rest-stopovers",
            progressUpdatedAtEpochSeconds = route.sourceUpdatedAtEpochSeconds,
        )
        if (updated != trip) {
            activeTrip = updated
            stateVersion += 1
            sourceUpdatedAtEpochSeconds = nowEpochSeconds
        }
    }

    private fun markChanged() {
        stateVersion += 1
        sourceUpdatedAtEpochSeconds = nowEpochSeconds()
    }

    private fun MapPoint.toConnectorStop() = ConnectorStopState(
        name = label,
        latitude = latitude,
        longitude = longitude,
    )

    private fun RouteStop.toConnectorStop() = ConnectorStopState(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
    )
}
