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
}
