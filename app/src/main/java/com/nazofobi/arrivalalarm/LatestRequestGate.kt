package com.nazofobi.arrivalalarm

import java.util.concurrent.atomic.AtomicLong

/**
 * Rejects late callbacks from an older request, including after a selection is cleared.
 * This guards UI state; it does not cancel the underlying provider request.
 */
internal class LatestRequestGate {
    private val generation = AtomicLong(0L)

    @Synchronized fun begin(): Long = generation.incrementAndGet()

    @Synchronized fun invalidate() {
        generation.incrementAndGet()
    }

    // Zero is the unissued initial generation, never a valid request ticket.
    // Negative tokens also fail closed if an extreme counter wrap occurs.
    fun isCurrent(token: Long): Boolean = token > 0L && token == generation.get()

    /** Runs the callback atomically against a simultaneous selection change. */
    @Synchronized fun commitIfCurrent(token: Long, update: () -> Unit): Boolean {
        if (!isCurrent(token)) return false
        update()
        return true
    }
}
