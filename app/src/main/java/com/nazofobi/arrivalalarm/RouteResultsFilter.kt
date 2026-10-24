package com.nazofobi.arrivalalarm

/**
 * A presentation-level filter of actual provider/static-router candidates.
 *
 * Filtering does not modify the canonical journey selection or fabricate alternatives.
 * Apply before limiting cards so a valid direct route is not hidden behind transfers.
 */
internal data class RouteResultsFilter(
    val directOnly: Boolean = false,
    val fewerTransfersFirst: Boolean = false,
    val maxTransfers: Int? = null,
) {
    fun apply(options: List<RouteOption>): List<RouteOption> {
        val limit = maxTransfers
        val usable = options.filter { option ->
            // Never offer an unnamed or nonphysical itinerary as a selectable
            // arrival target; reject it before route-card limiting.
            option.id.isNotBlank() &&
                option.transfers >= 0 &&
                option.walkingMinutes >= 0 &&
                option.origin.latitude.isFinite() &&
                option.origin.latitude in -90.0..90.0 &&
                option.origin.longitude.isFinite() &&
                option.origin.longitude in -180.0..180.0 &&
                option.destination.latitude.isFinite() &&
                option.destination.latitude in -90.0..90.0 &&
                option.destination.longitude.isFinite() &&
                option.destination.longitude in -180.0..180.0
        }.distinct()
        // The detail view resolves the selected route by ID. If two different
        // itineraries claim that ID, selecting either card could open the other.
        // Exact duplicate rows are harmless, but conflicting IDs fail closed.
        val ambiguousIds = usable.groupingBy { it.id }.eachCount()
            .filterValues { count -> count > 1 }.keys
        val filtered = usable.filter { option ->
            option.id !in ambiguousIds &&
                (!directOnly || option.transfers == 0) &&
                (limit == null || option.transfers <= limit)
        }
        // Keep provider order for equally convenient routes.
        return if (fewerTransfersFirst) {
            filtered.sortedBy { it.transfers }
        } else {
            filtered
        }
    }
}
