package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProviderRecoveryTest {
    @Test fun armedStartupRetriesWhileGpsIsUnavailable() {
        // No provider has been registered yet: absence of GPS is a recoverable state.
        assertEquals(
            LocationProviderRecovery.Decision.RETRY,
            LocationProviderRecovery.startupDecision(JourneyPhase.ARMED, hasRuntimeGrants = true),
        )
    }

    @Test fun startupNeverSurvivesRevokedGrantsOrTerminalJourney() {
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.startupDecision(JourneyPhase.ARMED, hasRuntimeGrants = false),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.startupDecision(JourneyPhase.ARRIVED, hasRuntimeGrants = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.startupDecision(JourneyPhase.EMPTY, hasRuntimeGrants = true),
        )
    }

    @Test fun providerDropoutKeepsArmedJourneyReadyForRetry() {
        assertEquals(
            LocationProviderRecovery.Decision.RETRY,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = false, registeredProviderEnabled = false, hasRuntimeGrants = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.KEEP,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = true, registeredProviderEnabled = true, hasRuntimeGrants = true),
        )
    }

    @Test fun disabledRegisteredProviderMustRetryInsteadOfKeepingStaleRegistration() {
        assertEquals(
            LocationProviderRecovery.Decision.RETRY,
            LocationProviderRecovery.decide(
                JourneyPhase.ARMED,
                locationRegistered = true,
                registeredProviderEnabled = false,
                hasRuntimeGrants = true,
            ),
        )
    }

    @Test fun permissionRevocationOrFinishedJourneyStopsTracking() {
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = false, registeredProviderEnabled = false, hasRuntimeGrants = false),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.ARRIVED, locationRegistered = true, registeredProviderEnabled = true, hasRuntimeGrants = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.EMPTY, locationRegistered = false, registeredProviderEnabled = false, hasRuntimeGrants = true),
        )
    }
}
