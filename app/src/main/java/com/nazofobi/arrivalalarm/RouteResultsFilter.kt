package com.nazofobi.arrivalalarm

/**
 * A presentation-level filter of actual provider/static-router candidates.
 *
 * Filtering does not modify the canonical journey selection or fabricate alternatives.
 * Apply before limiting cards so a valid direct route is not hidden behind transfers.
 */
internal data class RouteResultsFilter(
    val directOnly: Boolean = false,
) {
    fun apply(options: List<RouteOption>): List<RouteOption> =
        if (directOnly) options.filter { it.transfers == 0 } else options
}
