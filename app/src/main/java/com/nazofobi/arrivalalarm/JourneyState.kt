package com.nazofobi.arrivalalarm

data class GeoPoint(val latitude: Double, val longitude: Double)

private fun GeoPoint.isValidCoordinate(): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0

enum class JourneyPhase { EMPTY, START_SELECTED, DESTINATION_SELECTED, ARMED, ARRIVED }

data class JourneyUiState(
    val phase: JourneyPhase = JourneyPhase.EMPTY,
    val start: GeoPoint? = null,
    val destination: GeoPoint? = null,
    val distanceMeters: Double? = null,
    val error: String? = null,
)

/** Minimal persistence boundary so journey state can survive Activity/process recreation. */
interface JourneyStateStore {
    fun load(): JourneyUiState?
    fun save(state: JourneyUiState)
}

class JourneyController(
    private val arrivalRadiusMeters: Double = ArrivalLocationQuality.DEFAULT_ARRIVAL_RADIUS_METERS,
    private val stateStore: JourneyStateStore? = null,
) {
    init {
        require(arrivalRadiusMeters.isFinite() && arrivalRadiusMeters > 0.0) {
            "Arrival radius must be a finite positive distance"
        }
    }

    @Volatile
    var state: JourneyUiState = runCatching { stateStore?.load() }.getOrNull()?.takeIf { saved ->
        (saved.distanceMeters == null ||
            (saved.distanceMeters.isFinite() && saved.distanceMeters >= 0.0)) &&
            when (saved.phase) {
            JourneyPhase.EMPTY -> saved.start == null && saved.destination == null
            JourneyPhase.START_SELECTED -> saved.start?.isValidCoordinate() == true && saved.destination == null
            JourneyPhase.DESTINATION_SELECTED, JourneyPhase.ARMED, JourneyPhase.ARRIVED ->
                saved.start?.isValidCoordinate() == true && saved.destination?.isValidCoordinate() == true
        }
    } ?: JourneyUiState()
        private set

    private fun update(newState: JourneyUiState) {
        state = newState
        try {
            stateStore?.save(newState)
        } catch (_: RuntimeException) {
            // Keep the current journey usable if local persistence becomes unavailable.
        }
    }

    fun selectStart(point: GeoPoint) {
        if (!point.isValidCoordinate()) {
            update(state.copy(error = "Geçersiz başlangıç konumu"))
            return
        }
        update(JourneyUiState(phase = JourneyPhase.START_SELECTED, start = point))
    }

    fun selectDestination(point: GeoPoint) {
        val start = state.start ?: run {
            update(state.copy(error = "Önce başlangıç seçilmeli"))
            return
        }
        if (!point.isValidCoordinate()) {
            update(state.copy(error = "Geçersiz hedef konumu"))
            return
        }
        // The previous GPS distance belongs to the old destination and must not survive reselection.
        update(state.copy(phase = JourneyPhase.DESTINATION_SELECTED, start = start, destination = point, distanceMeters = null, error = null))
    }

    fun arm() {
        if (state.start == null || state.destination == null) {
            update(state.copy(error = "Başlangıç ve hedef gerekli"))
            return
        }
        if (state.start?.isValidCoordinate() != true || state.destination?.isValidCoordinate() != true) {
            update(state.copy(error = "Geçerli başlangıç ve hedef gerekli"))
            return
        }
        // An earlier arrival distance is not evidence for the newly armed alarm.
        val distance = if (state.phase == JourneyPhase.ARRIVED) null else state.distanceMeters
        update(state.copy(phase = JourneyPhase.ARMED, distanceMeters = distance, error = null))
    }

    fun cancelAlarm() {
        if (state.start == null || state.destination == null) {
            update(state.copy(error = "İptal edilecek kurulu alarm yok"))
            return
        }
        update(
            state.copy(
                phase = JourneyPhase.DESTINATION_SELECTED,
                distanceMeters = null,
                error = null,
            )
        )
    }

    fun onDistanceChanged(distanceMeters: Double, allowArrival: Boolean = true) {
        if (state.phase != JourneyPhase.ARMED || !distanceMeters.isFinite() || distanceMeters < 0.0) return
        update(
            state.copy(
                phase = if (allowArrival && distanceMeters <= arrivalRadiusMeters) JourneyPhase.ARRIVED else JourneyPhase.ARMED,
                distanceMeters = distanceMeters,
                error = null,
            )
        )
    }
}
