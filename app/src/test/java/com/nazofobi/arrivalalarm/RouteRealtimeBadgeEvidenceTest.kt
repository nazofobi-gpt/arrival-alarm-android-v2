package com.nazofobi.arrivalalarm

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
}
