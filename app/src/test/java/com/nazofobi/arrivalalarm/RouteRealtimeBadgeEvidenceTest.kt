package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRealtimeBadgeEvidenceTest {
    @Test fun staleUnavailableAndUnmatchedRoutesCannotClaimLiveStopTimes() {
        val untrusted = listOf<RouteRealtimeState?>(
            null,
            RouteRealtimeState.STALE,
            RouteRealtimeState.UNAVAILABLE,
            RouteRealtimeState.FRESH_NO_MATCH,
        )
        for (state in untrusted) {
            assertFalse(routeHasVerifiedRealtimeDelta(false, state, suppliedDelta = true))
            assertFalse(routeHasVerifiedRealtimePlatform(false, state, actualPlatform = "3"))
        }
    }

    @Test fun onlyFreshMatchedOnlineRoutesCanClaimObservedChanges() {
        val fresh = RouteRealtimeState.FRESH_MATCHED
        assertTrue(routeHasVerifiedRealtimeDelta(false, fresh, suppliedDelta = true))
        assertFalse(routeHasVerifiedRealtimeDelta(false, fresh, suppliedDelta = false))
        assertFalse(routeHasVerifiedRealtimeDelta(true, fresh, suppliedDelta = true))

        assertTrue(routeHasVerifiedRealtimePlatform(false, fresh, actualPlatform = "3"))
        assertFalse(routeHasVerifiedRealtimePlatform(false, fresh, actualPlatform = "  "))
        assertFalse(routeHasVerifiedRealtimePlatform(false, fresh, actualPlatform = null))
        assertFalse(routeHasVerifiedRealtimePlatform(true, fresh, actualPlatform = "3"))
    }

    @Test fun unsafeProviderPlatformCannotReceiveFreshRealtimeBadge() {
        val unsafeLabels = listOf(
            "Gleis\n2",
            "Gleis\t2",
            "2\u200B",
            "2\u202E",
            "\u0000",
        )
        for (platform in unsafeLabels) {
            assertFalse(routeHasVerifiedRealtimePlatform(
                routeOffline = false,
                state = RouteRealtimeState.FRESH_MATCHED,
                actualPlatform = platform,
            ))
        }
        assertTrue(routeHasVerifiedRealtimePlatform(
            routeOffline = false,
            state = RouteRealtimeState.FRESH_MATCHED,
            actualPlatform = "Gleis 2",
        ))
    }

    @Test fun realtimePlatformBadgeUsesSameTrustedEvidenceAsRouteBoard() {
        val platforms = listOf<String?>(null, "", " ", "Gleis 4", "2\u200B", "9\n1")
        for (offline in listOf(false, true)) {
            for (platform in platforms) {
                assertTrue(
                    routeHasVerifiedRealtimePlatform(
                        routeOffline = offline,
                        state = RouteRealtimeState.FRESH_MATCHED,
                        actualPlatform = platform,
                    ) == routeHasTrustedRealtimePlatform(offline, platform),
                )
                assertFalse(routeHasVerifiedRealtimePlatform(
                    routeOffline = offline,
                    state = RouteRealtimeState.STALE,
                    actualPlatform = platform,
                ))
            }
        }
    }

    @Test fun cancelledRouteBadgeNeedsFreshMatchedOnlineEvidence() {
        val untrusted = listOf<RouteRealtimeState?>(
            null,
            RouteRealtimeState.STALE,
            RouteRealtimeState.UNAVAILABLE,
            RouteRealtimeState.FRESH_NO_MATCH,
        )
        for (state in untrusted) {
            assertFalse(routeHasVerifiedRealtimeCancellation(false, state, suppliedCancellation = true))
        }

        assertFalse(routeHasVerifiedRealtimeCancellation(true, RouteRealtimeState.FRESH_MATCHED, true))
        assertFalse(routeHasVerifiedRealtimeCancellation(false, RouteRealtimeState.FRESH_MATCHED, false))
        assertTrue(routeHasVerifiedRealtimeCancellation(false, RouteRealtimeState.FRESH_MATCHED, true))
    }
    @Test fun routeDetailShowsOnlyFreshMatchedStopDeviations() {
        val planned = "2026-11-02T18:00:00+01:00"
        val delayed = "2026-11-02T18:05:20+01:00"
        val early = "2026-11-02T17:57:00+01:00"
        assertEquals(5, verifiedStopTimeDeviationMinutes(false, RouteRealtimeState.FRESH_MATCHED, delayed, planned))
        assertEquals(-3, verifiedStopTimeDeviationMinutes(false, RouteRealtimeState.FRESH_MATCHED, early, planned))
        // Equal instants with different offsets cannot invent a delay.
        assertNull(verifiedStopTimeDeviationMinutes(
            false, RouteRealtimeState.FRESH_MATCHED,
            "2026-11-02T17:00:00Z", planned,
        ))
    }

    @Test fun routeDetailNeverShowsStaleOfflineOrUnmatchedDeviation() {
        val planned = "2026-11-02T18:00:00Z"
        val observed = "2026-11-02T18:08:00Z"
        for (state in listOf<RouteRealtimeState?>(
            null, RouteRealtimeState.STALE, RouteRealtimeState.UNAVAILABLE,
            RouteRealtimeState.FRESH_NO_MATCH,
        )) {
            assertNull(verifiedStopTimeDeviationMinutes(false, state, observed, planned))
        }
        assertNull(verifiedStopTimeDeviationMinutes(true, RouteRealtimeState.FRESH_MATCHED, observed, planned))
        assertNull(verifiedStopTimeDeviationMinutes(false, RouteRealtimeState.FRESH_MATCHED, null, planned))
        assertNull(verifiedStopTimeDeviationMinutes(false, RouteRealtimeState.FRESH_MATCHED, observed, null))
    }

    @Test fun malformedImplausibleAndSubminuteStopDeltasAreNotDisplayed() {
        val planned = "2026-11-02T18:00:00Z"
        val fresh = RouteRealtimeState.FRESH_MATCHED
        assertNull(verifiedStopTimeDeviationMinutes(false, fresh, "2026-11-02T18:00:20Z", planned))
        assertNull(verifiedStopTimeDeviationMinutes(false, fresh, "2026-11-04T19:00:00Z", planned))
        assertNull(verifiedStopTimeDeviationMinutes(false, fresh, "not-a-time", planned))
        assertNull(verifiedStopTimeDeviationMinutes(false, fresh, "2026-11-02T18:02:00Z", "-00:00"))
    }

}
