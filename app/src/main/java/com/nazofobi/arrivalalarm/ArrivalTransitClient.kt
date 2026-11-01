package com.nazofobi.arrivalalarm

/** Bounded provider seam for exercising actual ArrivalAlarmApp callback ordering. */
interface ArrivalTransitClient {
    fun currentState(): NationwideDataState
    fun ensureNationwideIndex(callback: (NationwideDataState) -> Unit)
    fun searchLocationsResolved(
        query: String,
        limit: Int = 20,
        callback: (NationwideLookupResolution<TransitLocationResult>) -> Unit,
    )
    fun nearbyStopsResolved(
        latitude: Double,
        longitude: Double,
        limit: Int = 8,
        callback: (NationwideLookupResolution<NearbyStop>) -> Unit,
    )
    fun stationDepartures(
        stop: CatalogStop,
        limit: Int = 8,
        callback: (StationDeparturesSnapshot) -> Unit,
    )
    fun journeyOptions(
        origin: MapPoint,
        destination: MapPoint,
        limit: Int = 3,
        callback: (List<RouteOption>, String) -> Unit,
    )
    /**
     * Resolve one explicitly selected GTFS trip. The default fails closed for
     * providers that do not implement this contract; it never falls back to
     * general journey search and silently chooses a different departure.
     */
    fun selectedJourneyOptions(
        constraint: SelectedTripConstraint,
        callback: (SelectedTripPlanningOutcome) -> Unit,
    ) {
        callback(
            SelectedTripPlanningOutcome.Conflict(
                SelectedTripConflict(SelectedTripConflictReason.UNSUPPORTED)
            )
        )
    }
    fun close()
}
