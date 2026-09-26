package com.nazofobi.arrivalalarm

data class GeoPoint(val latitude: Double, val longitude: Double)

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
    private val arrivalRadiusMeters: Double = 250.0,
    private val stateStore: JourneyStateStore? = null,
) {
    @Volatile
    var state: JourneyUiState = stateStore?.load() ?: JourneyUiState()
        private set

    private fun update(newState: JourneyUiState) {
        state = newState
        stateStore?.save(newState)
    }

    fun selectStart(point: GeoPoint) {
        update(JourneyUiState(phase = JourneyPhase.START_SELECTED, start = point))
    }

    fun selectDestination(point: GeoPoint) {
        val start = state.start ?: run {
            update(state.copy(error = "Önce başlangıç seçilmeli"))
            return
        }
        update(state.copy(phase = JourneyPhase.DESTINATION_SELECTED, start = start, destination = point, error = null))
    }

    fun arm() {
        if (state.start == null || state.destination == null) {
            update(state.copy(error = "Başlangıç ve hedef gerekli"))
            return
        }
        update(state.copy(phase = JourneyPhase.ARMED, error = null))
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

    fun onDistanceChanged(distanceMeters: Double) {
        if (state.phase != JourneyPhase.ARMED) return
        update(
            state.copy(
                phase = if (distanceMeters <= arrivalRadiusMeters) JourneyPhase.ARRIVED else JourneyPhase.ARMED,
                distanceMeters = distanceMeters,
                error = null,
            )
        )
    }
}
