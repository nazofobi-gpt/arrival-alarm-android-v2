package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProviderRecoveryTest {
    @Test fun armedStartupRetriesWhenLocationIsOnButProviderIsUnavailable() {
        // The Android-wide location switch is ON, but no enabled provider exists yet.
        assertEquals(
            LocationProviderRecovery.Decision.RETRY,
            LocationProviderRecovery.startupDecision(
                JourneyPhase.ARMED,
                hasRuntimeGrants = true,
                locationServicesEnabled = true,
            ),
        )
    }

    @Test fun startupStopsWhenSystemLocationIsDisabledOrPermissionsRevoked() {
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.startupDecision(
                JourneyPhase.ARMED,
                hasRuntimeGrants = true,
                locationServicesEnabled = false,
            ),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.startupDecision(
                JourneyPhase.ARMED,
                hasRuntimeGrants = false,
                locationServicesEnabled = true,
            ),
        )
    }

    @Test fun startupStopsForTerminalJourneys() {
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.startupDecision(
                JourneyPhase.ARRIVED,
                hasRuntimeGrants = true,
                locationServicesEnabled = true,
            ),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.startupDecision(
                JourneyPhase.EMPTY,
                hasRuntimeGrants = true,
                locationServicesEnabled = true,
            ),
        )
    }

    @Test fun providerDropoutKeepsArmedJourneyReadyForRetry() {
        assertEquals(
            LocationProviderRecovery.Decision.RETRY,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = false, registeredProviderEnabled = false, hasRuntimeGrants = true, locationServicesEnabled = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.KEEP,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = true, registeredProviderEnabled = true, hasRuntimeGrants = true, locationServicesEnabled = true),
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
                locationServicesEnabled = true,
            ),
        )
    }

    @Test fun permissionRevocationOrFinishedJourneyStopsTracking() {
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.ARMED, locationRegistered = false, registeredProviderEnabled = false, hasRuntimeGrants = false, locationServicesEnabled = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.ARRIVED, locationRegistered = true, registeredProviderEnabled = true, hasRuntimeGrants = true, locationServicesEnabled = true),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(JourneyPhase.EMPTY, locationRegistered = false, registeredProviderEnabled = false, hasRuntimeGrants = true, locationServicesEnabled = true),
        )
    }
    @Test fun systemWideLocationOffStopsArmedTrackingEvenWhenProviderStillReportsEnabled() {
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(
                journeyPhase = JourneyPhase.ARMED,
                locationRegistered = true,
                registeredProviderEnabled = true,
                hasRuntimeGrants = true,
                locationServicesEnabled = false,
            ),
        )
    }

    @Test fun providerDropoutRetriesOnlyWhileSystemLocationRemainsEnabled() {
        assertEquals(
            LocationProviderRecovery.Decision.RETRY,
            LocationProviderRecovery.decide(
                journeyPhase = JourneyPhase.ARMED,
                locationRegistered = false,
                registeredProviderEnabled = false,
                hasRuntimeGrants = true,
                locationServicesEnabled = true,
            ),
        )
        assertEquals(
            LocationProviderRecovery.Decision.STOP,
            LocationProviderRecovery.decide(
                journeyPhase = JourneyPhase.ARMED,
                locationRegistered = false,
                registeredProviderEnabled = false,
                hasRuntimeGrants = true,
                locationServicesEnabled = false,
            ),
        )
    }
}
