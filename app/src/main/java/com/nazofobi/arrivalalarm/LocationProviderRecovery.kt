package com.nazofobi.arrivalalarm

/**
 * Decides whether armed foreground tracking should survive a temporary loss
 * of the Android location provider. A missing provider is recoverable, but
 * revoked runtime grants or a finished journey must not keep the service alive.
 */
internal object LocationProviderRecovery {
    enum class Decision { STOP, KEEP, RETRY }

    /** An already-armed journey may enter foreground recovery before GPS comes back. */
    fun startupDecision(journeyPhase: JourneyPhase, hasRuntimeGrants: Boolean): Decision =
        decide(
            journeyPhase = journeyPhase,
            locationRegistered = false,
            registeredProviderEnabled = false,
            hasRuntimeGrants = hasRuntimeGrants,
        )

    fun decide(
        journeyPhase: JourneyPhase,
        locationRegistered: Boolean,
        registeredProviderEnabled: Boolean,
        hasRuntimeGrants: Boolean,
    ): Decision = when {
        journeyPhase != JourneyPhase.ARMED || !hasRuntimeGrants -> Decision.STOP
        locationRegistered && registeredProviderEnabled -> Decision.KEEP
        else -> Decision.RETRY
    }
}
