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

    @Test fun malformedProviderClocksCannotClaimRealtimeDeviation() {
        assertFalse(routeHasUsableRealtimeDelta("unknown", "08:15"))
        assertFalse(routeHasUsableRealtimeDelta("08:42", "invalid"))
        assertFalse(routeHasUsableRealtimeDelta("25:61", "25:00"))
        // Valid GTFS extended service-day clocks remain usable.
        assertTrue(routeHasUsableRealtimeDelta("24:15", "23:55"))
    }

    @Test fun providerClockPrecisionAloneCannotClaimRealtimeDelay() {
        // Both fields render as 08:15. Formatting or seconds-only variance
        // is not a visible departure-time change.
        assertFalse(routeHasUsableRealtimeDelta("08:15:00", "08:15"))
        assertFalse(routeHasUsableRealtimeDelta("08:15", "08:15:59"))
        assertFalse(routeHasUsableRealtimeDelta("08:15:59", "08:15:00"))
        assertFalse(routeHasUsableRealtimeDelta("24:00:59", "24:00"))

        // A genuine change of displayed minute remains labelled realtime.
        assertTrue(routeHasUsableRealtimeDelta("08:16:00", "08:15:59"))
        assertTrue(routeHasUsableRealtimeDelta("24:01:00", "24:00:59"))
    }

    @Test fun blankRealtimePlatformNeverClaimsARealtimeBadge() {
        for (missing in listOf<String?>(null, "", " ", "\t")) {
            assertFalse(routeHasTrustedRealtimePlatform(routeOffline = false, actualPlatform = missing))
            assertFalse(routeHasTrustedRealtimePlatform(routeOffline = true, actualPlatform = missing))
        }
        assertTrue(routeHasTrustedRealtimePlatform(routeOffline = false, actualPlatform = "2"))
        assertFalse(routeHasTrustedRealtimePlatform(routeOffline = true, actualPlatform = "2"))
    }


    @Test fun invalidObservedClockFallsBackToValidScheduledClock() {
        assertEquals("08:15", routeEvidenceTime("invalid", "08:15", routeOffline = false))
        assertEquals("24:15", routeEvidenceTime("25:61", "24:15", routeOffline = false))
        assertEquals("08:15", routeEvidenceTime("08:99", "08:15", routeOffline = false))
        assertEquals("08:42", routeEvidenceTime("08:42", "not-a-clock", routeOffline = false))
        assertNull(routeEvidenceTime("unknown", "bad", routeOffline = false))
    }

    @Test fun offlineAndBlankScheduledClocksFailClosedWhenNotParseable() {
        assertNull(routeEvidenceTime("08:42", "not-a-clock", routeOffline = true))
        assertNull(routeEvidenceTime("08:42", "25:61", routeOffline = true))
        assertEquals("24:15", routeEvidenceTime("25:61", "24:15", routeOffline = true))
    }

    @Test fun incidentalClockWhitespaceIsNormalizedForDisplay() {
        assertEquals("08:42", routeEvidenceTime(" 08:42 ", "08:15", routeOffline = false))
        assertEquals("08:15", routeEvidenceTime("invalid", " 08:15 ", routeOffline = false))
        assertEquals("24:00:59", routeEvidenceTime("24:00:59", "24:00", routeOffline = false))
        assertEquals(
            "2026-10-25T08:42:00",
            routeEvidenceTime("2026-10-25T08:42:00", "08:15", routeOffline = false),
        )
    }

    @Test fun arbitraryTextBeforeDateTimeSeparatorCannotMasqueradeAsLiveClock() {
        // routeClock extracts text after T; only a structurally valid ISO date
        // may use that form as provider evidence.
        assertEquals("08:15", routeEvidenceTime("garbageT08:42", "08:15", routeOffline = false))
        assertNull(routeEvidenceTime("garbageT08:42", null, routeOffline = false))
        assertFalse(routeHasUsableRealtimeDelta("garbageT08:42", "08:15"))
        assertFalse(routeHasUsableRealtimeDelta("08:42", "garbageT08:15"))

        assertNull(routeEvidenceTime("2026-00-25T08:42", null, routeOffline = false))
        assertNull(routeEvidenceTime("2026-13-25T08:42", null, routeOffline = true))
        assertEquals(
            "2026-10-25T08:42:00",
            routeEvidenceTime("2026-10-25T08:42:00", "08:15", routeOffline = false),
        )
        assertTrue(routeHasUsableRealtimeDelta("2026-10-25T08:42:00", "08:15"))
    }

    @Test fun impossibleGregorianProviderDateFallsBackToSchedule() {
        for (badDate in listOf(
            "2026-02-29T08:42",
            "2026-02-30T08:42",
            "2026-02-31T08:42",
            "2026-04-31T08:42",
            "1900-02-29T08:42",
            "0000-01-01T08:42",
        )) {
            assertEquals("08:15", routeEvidenceTime(badDate, "08:15", routeOffline = false))
            assertNull(routeEvidenceTime(badDate, null, routeOffline = false))
            assertFalse(routeHasUsableRealtimeDelta(badDate, "08:15"))
        }
        assertEquals("2028-02-29T08:42", routeEvidenceTime(
            "2028-02-29T08:42", "08:15", routeOffline = false,
        ))
        assertEquals("2000-02-29T08:42", routeEvidenceTime(
            "2000-02-29T08:42", null, routeOffline = false,
        ))
        assertEquals("2026-04-30T08:42", routeEvidenceTime(
            "2026-04-30T08:42", null, routeOffline = false,
        ))
        assertEquals("24:15", routeEvidenceTime("24:15", null, routeOffline = false))
    }


}
