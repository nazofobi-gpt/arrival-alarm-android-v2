package com.nazofobi.arrivalalarm

/**
 * Decides whether armed foreground tracking should survive a temporary loss
 * of the Android location provider. A missing provider is recoverable, but
 * revoked runtime grants must disarm the alarm before stopping the service;
 * a finished journey only needs the service to stop.
 */
internal object LocationProviderRecovery {
    enum class Decision { STOP, DISARM_AND_STOP, KEEP, RETRY }

    /** Ignore queued GPS fixes after a trip ends or its arrival was handled. */
    fun acceptsLocationCallback(journeyPhase: JourneyPhase, arrivalHandled: Boolean): Boolean =
        journeyPhase == JourneyPhase.ARMED && !arrivalHandled

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

    /**
     * A denied foreground promotion cannot keep a journey armed: there will
     * be no reliable location updates or arrival notification. Terminal
     * journeys should only tear down the service, never cancel retroactively.
     */
    fun foregroundStartFailureDecision(journeyPhase: JourneyPhase): Decision =
        if (journeyPhase == JourneyPhase.ARMED) Decision.DISARM_AND_STOP else Decision.STOP

    fun decide(
        journeyPhase: JourneyPhase,
        locationRegistered: Boolean,
        registeredProviderEnabled: Boolean,
        hasRuntimeGrants: Boolean,
        locationServicesEnabled: Boolean,
    ): Decision = when {
        journeyPhase != JourneyPhase.ARMED -> Decision.STOP
        !locationServicesEnabled || !hasRuntimeGrants -> Decision.DISARM_AND_STOP
        locationRegistered && registeredProviderEnabled -> Decision.KEEP
        else -> Decision.RETRY
    }
}
