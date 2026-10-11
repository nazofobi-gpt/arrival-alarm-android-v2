package com.nazofobi.arrivalalarm

/**
 * Decides whether armed foreground tracking should survive a temporary loss
 * of the Android location provider. A missing provider is recoverable, but
 * revoked runtime grants or a finished journey must not keep the service alive.
 */
internal object LocationProviderRecovery {
    enum class Decision { STOP, KEEP, RETRY }

    fun decide(
        journeyPhase: JourneyPhase,
        locationRegistered: Boolean,
        hasRuntimeGrants: Boolean,
    ): Decision = when {
        journeyPhase != JourneyPhase.ARMED || !hasRuntimeGrants -> Decision.STOP
        locationRegistered -> Decision.KEEP
        else -> Decision.RETRY
    }
}
