package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProviderRecoveryTest {
    @Test fun providerDropoutKeepsArmedJourneyReadyForRetry() {
        assertEquals(
            LocationProviderRecovery.Decision.RETRY,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = false, hasRuntimeGrants = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.KEEP,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = true, hasRuntimeGrants = true),
        )
    }

    @Test fun permissionRevocationOrFinishedJourneyStopsTracking() {
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = false, hasRuntimeGrants = false),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.ARRIVED, locationRegistered = true, hasRuntimeGrants = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.EMPTY, locationRegistered = false, hasRuntimeGrants = true),
        )
    }
}
