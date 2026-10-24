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
        val filtered = options.filter { option ->
            // A negative transfer count is malformed provider evidence, not a
            // valid route with fewer changes. Never display it as "-1 transfers",
            // even when all routes are shown without an explicit limit.
            option.transfers >= 0 &&
                // Missing/corrupt walking costs cannot be offered as a real
                // negative-minute itinerary, even with every chip disabled.
                option.walkingMinutes >= 0 &&
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
