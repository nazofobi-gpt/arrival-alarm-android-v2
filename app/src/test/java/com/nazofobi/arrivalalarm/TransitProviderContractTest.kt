package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitProviderContractTest {
    @Test
    fun localStaticIsExplicitNetworkIndependentSourceOfTruth() {
        val local = ArrivalAlarmTransitProviders.LocalStatic

        assertEquals(TransitProviderRole.LOCAL_STATIC, local.role)
        assertFalse(local.networkRequired)
        assertTrue(local.supports(TransitProviderCapability.STATIC_ROUTING))
        assertTrue(local.supports(TransitProviderCapability.STOP_SEARCH))
        assertFalse(local.supports(TransitProviderCapability.TRIP_UPDATES))
    }

    @Test
    fun resultNoResultUnavailableAndStaleAreDistinct() {
        val provenance = TransitProviderProvenance(
            providerId = "local-static",
            sourceLabel = "Germany Full",
            sourceVersion = "v1",
            fetchedAtEpochSeconds = 100,
        )
        val fresh = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 100,
            nowEpochSeconds = 120,
            staleAfterSeconds = 60,
        )
        val stale = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 100,
            nowEpochSeconds = 200,
            staleAfterSeconds = 60,
        )

        val results: TransitProviderOutcome<List<String>> =
            TransitProviderOutcome.Results(listOf("A"), provenance, fresh)
        val noResult: TransitProviderOutcome<List<String>> =
            TransitProviderOutcome.NoResult(provenance, fresh)
        val unavailable: TransitProviderOutcome<List<String>> =
            TransitProviderOutcome.Unavailable(
                reason = TransitProviderUnavailableReason.TIMEOUT,
                retryable = true,
            )
        val staleResult: TransitProviderOutcome<List<String>> =
            TransitProviderOutcome.Stale(listOf("A"), provenance, stale)

        assertTrue(results is TransitProviderOutcome.Results)
        assertTrue(noResult is TransitProviderOutcome.NoResult)
        assertTrue(unavailable is TransitProviderOutcome.Unavailable)
        assertTrue(staleResult is TransitProviderOutcome.Stale)
    }

    @Test
    fun freshnessIsDeterministicAndClockIsInjectedByCaller() {
        val fresh = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 1_000,
            nowEpochSeconds = 1_060,
            staleAfterSeconds = 60,
        )
        val stale = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 1_000,
            nowEpochSeconds = 1_061,
            staleAfterSeconds = 60,
        )
        val unknown = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = null,
            nowEpochSeconds = 1_061,
            staleAfterSeconds = 60,
        )
        val notApplicable = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = null,
            nowEpochSeconds = 1_061,
            staleAfterSeconds = 60,
            applicable = false,
        )

        assertEquals(TransitProviderFreshnessState.FRESH, fresh.state)
        assertEquals(60L, fresh.ageSeconds)
        assertEquals(TransitProviderFreshnessState.STALE, stale.state)
        assertEquals(61L, stale.ageSeconds)
        assertEquals(TransitProviderFreshnessState.UNKNOWN, unknown.state)
        assertNull(unknown.ageSeconds)
        assertEquals(TransitProviderFreshnessState.NOT_APPLICABLE, notApplicable.state)
    }

    @Test
    fun futureProviderTimestampWithinAllowedSkewRemainsFresh() {
        val result = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 1_030,
            nowEpochSeconds = 1_000,
            staleAfterSeconds = 0,
        )

        assertEquals(TransitProviderFreshnessState.FRESH, result.state)
        assertEquals(0L, result.ageSeconds)
        assertEquals(1_030L, result.updatedAtEpochSeconds)
    }

    @Test
    fun futureProviderTimestampBeyondSkewIsUntrusted() {
        for (futureOffset in listOf(31L, 3_600L, 86_400L)) {
            val result = TransitProviderFreshness.evaluate(
                updatedAtEpochSeconds = 1_000L + futureOffset,
                nowEpochSeconds = 1_000L,
                staleAfterSeconds = 3_600L,
            )

            assertEquals(TransitProviderFreshnessState.UNKNOWN, result.state)
            assertNull(result.ageSeconds)
            assertNull(result.updatedAtEpochSeconds)
        }
    }

    @Test
    fun nonApplicableProviderDoesNotBecomeUnknownBecauseOfFutureTimestamp() {
        val result = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 9_999,
            nowEpochSeconds = 1_000,
            staleAfterSeconds = 60,
            applicable = false,
        )

        assertEquals(TransitProviderFreshnessState.NOT_APPLICABLE, result.state)
        assertNull(result.ageSeconds)
    }

    @Test
    fun providerHealthAndCapabilitiesRemainQueryable() {
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.DEGRADED,
            checkedAtEpochSeconds = 2_000,
            detail = "stale feed",
        )

        assertEquals("germany-gtfs-rt", health.providerId)
        assertEquals(TransitProviderHealthState.DEGRADED, health.state)
        assertTrue(
            ArrivalAlarmTransitProviders.GermanyRealtime.supports(
                TransitProviderCapability.SERVICE_ALERTS,
            )
        )
        assertFalse(
            ArrivalAlarmTransitProviders.GermanyRealtime.supports(
                TransitProviderCapability.STATIC_ROUTING,
            )
        )
    }

    @Test
    fun providerFreshnessAcceptsOnlyBoundedPositiveClockSkew() {
        val withinSkew = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 1_030,
            nowEpochSeconds = 1_000,
            staleAfterSeconds = 60,
        )
        val outsideSkew = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = 1_031,
            nowEpochSeconds = 1_000,
            staleAfterSeconds = 60,
        )

        assertEquals(TransitProviderFreshnessState.FRESH, withinSkew.state)
        assertEquals(0L, withinSkew.ageSeconds)
        assertEquals(1_030L, withinSkew.updatedAtEpochSeconds)
        assertEquals(TransitProviderFreshnessState.UNKNOWN, outsideSkew.state)
        assertNull(outsideSkew.ageSeconds)
        assertNull(outsideSkew.updatedAtEpochSeconds)
    }

    @Test
    fun farFutureProviderTimestampCannotMasqueradeAsFresh() {
        val invalid = TransitProviderFreshness.evaluate(
            updatedAtEpochSeconds = Long.MAX_VALUE,
            nowEpochSeconds = 0L,
            staleAfterSeconds = 60,
        )

        assertEquals(TransitProviderFreshnessState.UNKNOWN, invalid.state)
        assertNull(invalid.ageSeconds)
        assertNull(invalid.updatedAtEpochSeconds)
    }
}
