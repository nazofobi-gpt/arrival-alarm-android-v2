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
) {
    fun apply(options: List<RouteOption>): List<RouteOption> {
        val filtered = if (directOnly) options.filter { it.transfers == 0 } else options
        // Keep provider order for equally convenient routes. Invalid negative counts
        // must not outrank known transfer counts in the opt-in ranking.
        return if (fewerTransfersFirst) {
            filtered.sortedBy { it.transfers.takeIf { count -> count >= 0 } ?: Int.MAX_VALUE }
        } else {
            filtered
        }
    }
}
