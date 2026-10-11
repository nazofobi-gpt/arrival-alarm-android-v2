package com.nazofobi.arrivalalarm

import java.util.concurrent.atomic.AtomicLong

/**
 * Rejects late callbacks from an older request, including after a selection is cleared.
 * This guards UI state; it does not cancel the underlying provider request.
 */
internal class LatestRequestGate {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun isCurrent(token: Long): Boolean = token == generation.get()
}
