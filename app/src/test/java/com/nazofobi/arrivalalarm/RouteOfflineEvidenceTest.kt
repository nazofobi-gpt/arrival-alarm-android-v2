package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteOfflineEvidenceTest {
    @Test fun offlineRouteUsesScheduledStopTimesNotCachedLiveDelays() {
        assertEquals("08:15", routeEvidenceTime("08:42", "08:15", routeOffline = true))
        assertEquals("08:42", routeEvidenceTime("08:42", "08:15", routeOffline = false))
        assertEquals("08:15", routeEvidenceTime(null, "08:15", routeOffline = false))
    }

    @Test fun offlineRouteWithNoScheduledStopTimeMustShowUnknown() {
        assertNull(routeEvidenceTime("08:42", null, routeOffline = true))
        assertNull(routeEvidenceTime(null, null, routeOffline = true))
        assertEquals("08:42", routeEvidenceTime("08:42", null, routeOffline = false))
    }

    @Test fun offlineRouteUsesOnlyScheduledPlatformNotCachedPlatformChange() {
        assertEquals("1", routeEvidencePlatform("3", "1", routeOffline = true))
        assertNull(routeEvidencePlatform("3", null, routeOffline = true))
        assertEquals("3", routeEvidencePlatform("3", "1", routeOffline = false))
        assertEquals("1", routeEvidencePlatform(null, "1", routeOffline = false))
    }

    @Test fun cachedRealtimeLabelsCannotClaimCurrentProviderEvidence() {
        assertFalse(routeHasTrustedRealtime(routeOffline = true, suppliedRealtime = true))
        assertFalse(routeHasTrustedRealtime(routeOffline = true, suppliedRealtime = false))
        assertFalse(routeHasTrustedRealtime(routeOffline = false, suppliedRealtime = false))
        assertTrue(routeHasTrustedRealtime(routeOffline = false, suppliedRealtime = true))
    }
}
