package com.nazofobi.arrivalalarm

/**
 * Bounded frame admission policy for Screen Assist.
 * Keeps at most one frame in flight, rate-limits capture, and adapts image size
 * to degraded network conditions without retaining raw frames.
 */
class ScreenAssistFramePolicy(
    private val normalIntervalMs: Long = 1_000,
    private val degradedIntervalMs: Long = 2_500,
    private val normalMaxEdgePx: Int = 1280,
    private val degradedMaxEdgePx: Int = 768,
) {
    data class Decision(
        val send: Boolean,
        val maxEdgePx: Int,
        val jpegQuality: Int,
        val reason: String,
    )

    private var lastAcceptedAtMs: Long? = null
    private var frameInFlight = false

    fun decide(nowMs: Long, networkDegraded: Boolean): Decision {
        if (frameInFlight) return Decision(false, edge(networkDegraded), quality(networkDegraded), "backpressure")
        val interval = if (networkDegraded) degradedIntervalMs else normalIntervalMs
        val last = lastAcceptedAtMs
        if (last != null && nowMs - last < interval) {
            return Decision(false, edge(networkDegraded), quality(networkDegraded), "rate_limited")
        }
        frameInFlight = true
        lastAcceptedAtMs = nowMs
        return Decision(true, edge(networkDegraded), quality(networkDegraded), "accepted")
    }

    fun onFrameFinished() {
        frameInFlight = false
    }

    fun reset() {
        frameInFlight = false
        lastAcceptedAtMs = null
    }

    private fun edge(degraded: Boolean) = if (degraded) degradedMaxEdgePx else normalMaxEdgePx
    private fun quality(degraded: Boolean) = if (degraded) 60 else 75
}
