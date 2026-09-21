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

/**
 * Deterministic redistributable fixture authored in this repository.
 * Names model a Bremen/VBN-like journey; this is not claimed to be a production GTFS feed.
 */
class FixtureStaticTransitRepository : StaticTransitRepository {
    private val hbf = TransitStop("fixture-bremen-hbf", "Bremen Hbf")
    private val amBrill = TransitStop("fixture-am-brill", "Am Brill")
    private val airport = TransitStop("fixture-airport", "Flughafen Bremen")
    private val stops = listOf(hbf, amBrill, airport)

    override fun searchStops(query: String): List<TransitStop> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        return stops.filter { it.name.contains(normalized, ignoreCase = true) }
    }

    override fun plan(startId: String, destinationId: String): TransitJourney? {
        val start = stops.firstOrNull { it.id == startId } ?: return null
        val destination = stops.firstOrNull { it.id == destinationId } ?: return null
        if (start == destination) return null
        val ordered = when {
            start == hbf && destination == airport -> listOf(hbf, amBrill, airport)
            start == airport && destination == hbf -> listOf(airport, amBrill, hbf)
            else -> listOf(start, destination)
        }
        return TransitJourney(start, destination, listOf(TransitLeg("Fixture 6", destination.name, ordered)))
    }
}

class TransitController(private val repository: StaticTransitRepository = FixtureStaticTransitRepository()) {
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

    fun planFromFixtureStart(destinationId: String) {
        val journey = repository.plan("fixture-bremen-hbf", destinationId)
        state = if (journey == null) {
            state.copy(loadState = TransitLoadState.DEGRADED, journey = null, message = "Statik rota bulunamadı")
        } else {
            state.copy(loadState = TransitLoadState.READY, journey = journey, message = "Çevrimdışı statik rota")
        }
    }
}
