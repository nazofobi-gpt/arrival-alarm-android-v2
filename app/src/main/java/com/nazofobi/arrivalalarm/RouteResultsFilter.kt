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
            (!directOnly || option.transfers == 0) &&
                (limit == null || (option.transfers >= 0 && option.transfers <= limit))
        }
        // Keep provider order for equally convenient routes. Invalid negative counts
        // must not outrank known transfer counts in the opt-in ranking.
        return if (fewerTransfersFirst) {
            filtered.sortedBy { it.transfers.takeIf { count -> count >= 0 } ?: Int.MAX_VALUE }
        } else {
            filtered
        }
    }
}
