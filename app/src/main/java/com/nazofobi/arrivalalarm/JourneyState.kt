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

class JourneyController(private val arrivalRadiusMeters: Double = 250.0) {
    var state: JourneyUiState = JourneyUiState()
        private set

    fun selectStart(point: GeoPoint) {
        state = JourneyUiState(phase = JourneyPhase.START_SELECTED, start = point)
    }

    fun selectDestination(point: GeoPoint) {
        val start = state.start ?: run {
            state = state.copy(error = "Önce başlangıç seçilmeli")
            return
        }
        state = state.copy(phase = JourneyPhase.DESTINATION_SELECTED, start = start, destination = point, error = null)
    }

    fun arm() {
        if (state.start == null || state.destination == null) {
            state = state.copy(error = "Başlangıç ve hedef gerekli")
            return
        }
        state = state.copy(phase = JourneyPhase.ARMED, error = null)
    }

    fun onDistanceChanged(distanceMeters: Double) {
        if (state.phase != JourneyPhase.ARMED) return
        state = state.copy(
            phase = if (distanceMeters <= arrivalRadiusMeters) JourneyPhase.ARRIVED else JourneyPhase.ARMED,
            distanceMeters = distanceMeters,
            error = null,
        )
    }
}
