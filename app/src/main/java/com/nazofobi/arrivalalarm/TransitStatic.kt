package com.nazofobi.arrivalalarm

/** Immutable, offline-first transit model for the bounded Bremen/VBN vertical slice. */
data class TransitStop(val id: String, val name: String)
data class TransitLeg(val line: String, val direction: String, val stops: List<TransitStop>)
data class TransitJourney(val start: TransitStop, val destination: TransitStop, val legs: List<TransitLeg>)

enum class TransitLoadState { READY, EMPTY, ERROR, DEGRADED }

data class TransitUiState(
    val loadState: TransitLoadState = TransitLoadState.READY,
    val query: String = "",
    val matches: List<TransitStop> = emptyList(),
    val journey: TransitJourney? = null,
    val message: String? = null,
)

interface StaticTransitRepository {
    fun searchStops(query: String): List<TransitStop>
    fun plan(startId: String, destinationId: String): TransitJourney?
}

class TransitController(private val repository: StaticTransitRepository) {
    var state: TransitUiState = TransitUiState()
        private set

    fun search(query: String) {
        val matches = repository.searchStops(query)
        state = TransitUiState(
            loadState = if (matches.isEmpty()) TransitLoadState.EMPTY else TransitLoadState.READY,
            query = query,
            matches = matches,
            message = if (matches.isEmpty()) "Durak bulunamadı" else null,
        )
    }

    fun plan(startId: String, destinationId: String) {
        val journey = repository.plan(startId, destinationId)
        state = if (journey == null) {
            state.copy(loadState = TransitLoadState.DEGRADED, journey = null, message = "Statik rota bulunamadı")
        } else {
            state.copy(loadState = TransitLoadState.READY, journey = journey, message = "Çevrimdışı statik rota")
        }
    }
}
