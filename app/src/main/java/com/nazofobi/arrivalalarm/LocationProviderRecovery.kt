package com.nazofobi.arrivalalarm

/**
 * Decides whether armed foreground tracking should survive a temporary loss
 * of the Android location provider. A missing provider is recoverable, but
 * revoked runtime grants or a finished journey must not keep the service alive.
 */
internal object LocationProviderRecovery {
    enum class Decision { STOP, KEEP, RETRY }

    /**
     * Android location FGS startup requires the system-wide location switch ON.
     * With that prerequisite met, a transiently missing enabled provider can be
     * retried while an already-armed journey stays in foreground.
     */
    fun startupDecision(
        journeyPhase: JourneyPhase,
        hasRuntimeGrants: Boolean,
        locationServicesEnabled: Boolean,
    ): Decision =
        decide(
            journeyPhase = journeyPhase,
            locationRegistered = false,
            registeredProviderEnabled = false,
            hasRuntimeGrants = hasRuntimeGrants,
            locationServicesEnabled = locationServicesEnabled,
        )

    fun decide(
        journeyPhase: JourneyPhase,
        locationRegistered: Boolean,
        registeredProviderEnabled: Boolean,
        hasRuntimeGrants: Boolean,
        locationServicesEnabled: Boolean,
    ): Decision = when {
        !locationServicesEnabled || journeyPhase != JourneyPhase.ARMED || !hasRuntimeGrants -> Decision.STOP
        locationRegistered && registeredProviderEnabled -> Decision.KEEP
        else -> Decision.RETRY
    }
}
