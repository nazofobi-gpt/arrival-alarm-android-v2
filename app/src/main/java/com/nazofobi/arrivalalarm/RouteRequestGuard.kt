package com.nazofobi.arrivalalarm

/**
 * Only the most recently requested route may mutate the visible journey.
 * A changed origin invalidates in-flight callbacks even if no replacement route starts.
 *
 * This gate deliberately does not cancel network work; late callbacks are ignored instead.
 */
internal class RouteRequestGuard {
    private var generation = 0L

    fun begin(): Long = ++generation

    fun invalidate() {
        ++generation
    }

    fun accepts(requestGeneration: Long): Boolean =
        requestGeneration > 0L && requestGeneration == generation
}
