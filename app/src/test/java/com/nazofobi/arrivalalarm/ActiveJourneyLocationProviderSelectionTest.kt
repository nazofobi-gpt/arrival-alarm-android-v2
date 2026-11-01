package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveJourneyLocationProviderSelectionTest {
    @Test fun gpsIsPreferredOverOtherEnabledProviders() {
        assertEquals("gps", ActiveJourneyLocationProviderSelection.choose(
            listOf("passive", "fused", "network", "gps"),
        ))
    }

    @Test fun networkIsPreferredWhenGpsIsUnavailable() {
        assertEquals("network", ActiveJourneyLocationProviderSelection.choose(
            listOf("passive", "fused", "network"),
        ))
    }

    @Test fun passiveOnlyCannotArmBackgroundLocationTracking() {
        assertNull(ActiveJourneyLocationProviderSelection.choose(listOf("passive")))
        assertNull(ActiveJourneyLocationProviderSelection.choose(emptyList()))
        assertNull(ActiveJourneyLocationProviderSelection.choose(listOf("", "passive")))
    }

    @Test fun nonPassiveFallbackRemainsAvailable() {
        assertEquals("fused", ActiveJourneyLocationProviderSelection.choose(
            listOf("passive", "fused"),
        ))
    }

    @Test fun rankingIgnoresOEMWhitespaceAndCasingButPreservesProviderName() {
        assertEquals(" GPS ", ActiveJourneyLocationProviderSelection.choose(
            listOf(" passive ", " Network ", " GPS "),
        ))
    }

    @Test fun normalizedPassiveProviderIsStillRejected() {
        assertNull(ActiveJourneyLocationProviderSelection.choose(listOf(" PASSIVE ")))
    }
}
