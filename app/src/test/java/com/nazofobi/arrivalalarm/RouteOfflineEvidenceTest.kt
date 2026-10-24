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

    @Test fun offlineBlankScheduledFieldsStayUnknownInsteadOfDisplayingEmptyEvidence() {
        for (missing in listOf("", " ", "\t")) {
            assertNull(routeEvidenceTime("08:42", missing, routeOffline = true))
            assertNull(routeEvidencePlatform("3", missing, routeOffline = true))
        }
    }

    @Test fun blankRealtimeFieldsFallBackOnlyToUsableSchedule() {
        for (missing in listOf("", " ", "\t")) {
            assertEquals("08:15", routeEvidenceTime(missing, "08:15", routeOffline = false))
            assertEquals("1", routeEvidencePlatform(missing, "1", routeOffline = false))
            assertNull(routeEvidenceTime(missing, " ", routeOffline = false))
            assertNull(routeEvidencePlatform(missing, "", routeOffline = false))
        }
        // A scheduled value can be shown offline, not a cached realtime-only value.
        assertEquals("08:15", routeEvidenceTime(" ", "08:15", routeOffline = true))
        assertEquals("1", routeEvidencePlatform("", "1", routeOffline = true))
    }

    @Test fun missingProviderClocksCannotProduceLiveDeviationBadges() {
        for (missing in listOf<String?>(null, "", " ", "\t")) {
            assertFalse(routeHasUsableRealtimeDelta(missing, "08:15"))
            assertFalse(routeHasUsableRealtimeDelta("08:42", missing))
            assertFalse(routeHasTrustedRealtime(routeOffline = false,
                suppliedRealtime = routeHasUsableRealtimeDelta(missing, "08:15")))
        }
        assertFalse(routeHasUsableRealtimeDelta("08:15", "08:15"))
        assertTrue(routeHasUsableRealtimeDelta("08:42", "08:15"))
        assertFalse(routeHasTrustedRealtime(routeOffline = true,
            suppliedRealtime = routeHasUsableRealtimeDelta("08:42", "08:15")))
    }
    @Test fun incidentalClockWhitespaceDoesNotClaimRealtimeDelay() {
        assertFalse(routeHasUsableRealtimeDelta(" 08:15 ", "\t08:15"))
        assertTrue(routeHasUsableRealtimeDelta("08:16 ", " 08:15"))
    }

    @Test fun blankRealtimePlatformNeverClaimsARealtimeBadge() {
        for (missing in listOf<String?>(null, "", " ", "\t")) {
            assertFalse(routeHasTrustedRealtimePlatform(routeOffline = false, actualPlatform = missing))
            assertFalse(routeHasTrustedRealtimePlatform(routeOffline = true, actualPlatform = missing))
        }
        assertTrue(routeHasTrustedRealtimePlatform(routeOffline = false, actualPlatform = "2"))
        assertFalse(routeHasTrustedRealtimePlatform(routeOffline = true, actualPlatform = "2"))
    }

}
