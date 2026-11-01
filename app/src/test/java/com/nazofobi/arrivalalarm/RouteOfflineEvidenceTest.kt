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

    @Test fun isoCalendarDayShiftWithSameVisibleClockCountsAsChangedDeparture() {
        // Two validated ISO local dates are distinct provider schedule evidence.
        // Route cards render only HH:mm, so comparing the clock alone misses this.
        assertTrue(routeHasUsableRealtimeDelta(
            "2026-10-26T08:15:00", "2026-10-25T08:15:00"))
        assertTrue(routeHasUsableRealtimeDelta(
            "2026-10-25T08:15", "2026-10-26T08:15:59"))
        assertFalse(routeHasUsableRealtimeDelta(
            "2026-10-25T08:15:59", "2026-10-25T08:15:00"))

        // An undated GTFS clock cannot establish a calendar-day difference.
        assertFalse(routeHasUsableRealtimeDelta("2026-10-26T08:15", "08:15"))
        // Invalid calendar dates cannot create verified realtime evidence.
        assertFalse(routeHasUsableRealtimeDelta(
            "2026-02-30T08:15", "2026-02-28T08:15"))
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

    @Test fun unsafeProviderPlatformCharactersAreNotDisplayedOrBadged() {
        val unsafePlatforms = listOf(
            "Gleis\n2",
            "Gleis\t2",
            "2\u200B", // zero-width format character
            "2\u202E", // bidirectional override
            "\u0000", // control character
        )
        for (unsafe in unsafePlatforms) {
            assertEquals("Gleis 1", routeEvidencePlatform(unsafe, "Gleis 1", routeOffline = false))
            assertNull(routeEvidencePlatform(unsafe, null, routeOffline = false))
            assertFalse(routeHasTrustedRealtimePlatform(routeOffline = false, actualPlatform = unsafe))
            assertNull(routeEvidencePlatform("Gleis 2", unsafe, routeOffline = true))
        }
        assertEquals("Gleis 2", routeEvidencePlatform("Gleis 2", "Gleis 1", routeOffline = false))
        assertTrue(routeHasTrustedRealtimePlatform(routeOffline = false, actualPlatform = "Gleis 2"))
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

    @Test fun isoLocalClockRejectsNonCivilHoursButKeepsGtfsExtendedServiceDay() {
        for (malformedIso in listOf(
            "2026-10-25T24:15",
            "2026-10-25T25:15:00",
            "2026-10-25T99:42",
        )) {
            assertEquals("08:15", routeEvidenceTime(malformedIso, "08:15", routeOffline = false))
            assertNull(routeEvidenceTime(malformedIso, null, routeOffline = false))
            assertFalse(routeHasUsableRealtimeDelta(malformedIso, "08:15"))
        }
        assertEquals(
            "2026-10-25T23:59:00",
            routeEvidenceTime("2026-10-25T23:59:00", "08:15", routeOffline = false),
        )
        assertEquals("24:15", routeEvidenceTime("24:15", null, routeOffline = false))
        assertEquals("25:15:00", routeEvidenceTime(null, "25:15:00", routeOffline = true))
    }



    @Test fun routeSummaryHeaderRequiresValidatedRealtimeOrScheduledClock() {
        // Route-card endpoints use the same clock policy as the stop sequence.
        assertEquals("08:42", routeSummaryEvidenceClock("08:42:59", "08:15", routeOffline = false))
        assertEquals("08:15", routeSummaryEvidenceClock("garbageT08:42", "08:15", routeOffline = false))
        assertEquals("08:15", routeSummaryEvidenceClock("2026-02-30T08:42", "08:15", routeOffline = false))
        assertEquals("08:15", routeSummaryEvidenceClock("25:61", "08:15", routeOffline = false))
        assertEquals("08:15", routeSummaryEvidenceClock("  ", " 08:15 ", routeOffline = false))
        assertNull(routeSummaryEvidenceClock("invalid", "unknown", routeOffline = false))

        // Cached live clocks must not mask the scheduled endpoint offline.
        assertEquals("08:15", routeSummaryEvidenceClock("08:42", "08:15", routeOffline = true))
        assertNull(routeSummaryEvidenceClock("08:42", null, routeOffline = true))
        assertNull(routeSummaryEvidenceClock("08:42", "25:61", routeOffline = true))

        // Legitimate calendar and GTFS service-day clocks remain visible.
        assertEquals("08:42", routeSummaryEvidenceClock("2028-02-29T08:42", "08:15", routeOffline = false))
        assertEquals("25:40", routeSummaryEvidenceClock(" 25:40:03 ", "08:15", routeOffline = false))
    }
}
