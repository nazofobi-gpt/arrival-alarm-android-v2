package com.nazofobi.arrivalalarm

/**
 * Tracks one UI request lane so late asynchronous callbacks cannot replace newer state.
 *
 * A request is valid only until another request starts or the owning selection is reset.
 * Provider work may still finish, but its callback must not mutate the visible UI.
 */
internal class LatestRequestGate {
    private var generation = 0L

    @Synchronized
    fun begin(): Long {
        generation += 1
        return generation
    }

    @Synchronized
    fun invalidate() {
        generation += 1
    }

    @Synchronized
    fun accepts(requestGeneration: Long): Boolean = requestGeneration == generation
}
