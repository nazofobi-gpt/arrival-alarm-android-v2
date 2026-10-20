package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderProbeDiagnosticsTest {
    @Test fun unknownProviderIsNeverReportedAsHealthy() {
        val summary = ProviderProbeDiagnostics.render(null, null)
        assertEquals("not_measured", summary.value("provider_state"))
        assertEquals("not_measured", summary.value("latency_bucket"))
        assertEquals("unknown", summary.value("provider_role"))
    }

    @Test fun providerDetailsAreNotIncluded() {
        val hidden = "confidential-value"
        val health = TransitProviderHealth(
            providerId = "unknown-$hidden",
            state = TransitProviderHealthState.UNAVAILABLE,
            checkedAtEpochSeconds = 123456L,
            detail = hidden,
        )
        val summary = ProviderProbeDiagnostics.render(health, 500L)
        assertEquals("not_measured", summary.value("provider_state"))
        assertEquals("not_measured", summary.value("latency_bucket"))
        assertFalse(summary.contains(hidden))
        assertFalse(summary.contains("123456"))
    }

    @Test fun orphanLatencyIsNotAttributedToUnknownProvider() {
        val summary = ProviderProbeDiagnostics.render(null, 50L)
        assertEquals("unknown", summary.value("provider_role"))
        assertEquals("not_measured", summary.value("provider_state"))
        assertEquals("not_measured", summary.value("latency_bucket"))
    }

    @Test fun unknownProviderCannotBeReportedReadyEvenWithLatency() {
        val health = TransitProviderHealth(
            providerId = "unrecognized-provider",
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = 765432L,
            detail = "private-endpoint",
        )
        val summary = ProviderProbeDiagnostics.render(health, 10L)
        assertEquals("unknown", summary.value("provider_role"))
        assertEquals("not_measured", summary.value("provider_state"))
        assertEquals("not_measured", summary.value("latency_bucket"))
        assertFalse(summary.contains("private-endpoint"))
        assertFalse(summary.contains("765432"))
    }

    @Test fun knownProviderRetainsAllowlistedCoarseMeasurement() {
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = (System.currentTimeMillis() / 1_000L) - 30L,
            detail = "private-provider-detail",
        )
        val summary = ProviderProbeDiagnostics.render(health, 250L)
        assertEquals("local_static", summary.value("provider_role"))
        assertEquals("ready", summary.value("provider_state"))
        assertEquals("250ms_to_1s", summary.value("latency_bucket"))
        assertFalse(summary.contains("private-provider-detail"))
        assertFalse(summary.contains("987654"))
    }

    @Test fun zeroEpochCheckDoesNotBecomeVerifiedProviderHealth() {
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = 0L,
            detail = "sensitive-provider-error",
        )
        val summary = ProviderProbeDiagnostics.render(health, 25L)
        assertEquals("realtime", summary.value("provider_role"))
        assertEquals("not_measured", summary.value("provider_state"))
        assertEquals("not_measured", summary.value("latency_bucket"))
        assertFalse(summary.contains("sensitive-provider-error"))
    }

    @Test fun futureDatedCheckCannotClaimMeasuredHealthOrLatency() {
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = Long.MAX_VALUE,
            detail = "private-error-or-endpoint",
        )
        val summary = ProviderProbeDiagnostics.render(health, 25L)
        assertEquals("realtime", summary.value("provider_role"))
        assertEquals("not_measured", summary.value("provider_state"))
        assertEquals("not_measured", summary.value("latency_bucket"))
        assertFalse(summary.contains("private-error-or-endpoint"))
    }

    @Test fun recentHealthCheckWithinClockToleranceIsStillMeasured() {
        val now = System.currentTimeMillis() / 1_000L
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = now + 15L,
            detail = null,
        )
        val summary = ProviderProbeDiagnostics.render(health, 249L)
        assertEquals("ready", summary.value("provider_state"))
        assertEquals("under_250ms", summary.value("latency_bucket"))
    }

    @Test fun nonzeroCheckAllowsReportedStateWithoutInferringAppReadiness() {
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.DbEnrichment.id,
            state = TransitProviderHealthState.DEGRADED,
            checkedAtEpochSeconds = (System.currentTimeMillis() / 1_000L) - 60L,
            detail = "private-diagnostic",
        )
        val summary = ProviderProbeDiagnostics.render(health, 1000L)
        assertEquals("enrichment", summary.value("provider_role"))
        assertEquals("degraded", summary.value("provider_state"))
        assertEquals("1s_to_5s", summary.value("latency_bucket"))
        assertFalse(summary.contains("private-diagnostic"))
        assertFalse(summary.contains("checked_at"))
    }


    @Test fun expiredHealthCheckCannotClaimProviderReadyOrMeasuredLatency() {
        val now = System.currentTimeMillis() / 1_000L
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = now - 86_401L,
            detail = "private-health-error",
        )
        val summary = ProviderProbeDiagnostics.render(health, 300L)
        assertEquals("realtime", summary.value("provider_role"))
        assertEquals("not_measured", summary.value("provider_state"))
        assertEquals("not_measured", summary.value("latency_bucket"))
        assertFalse(summary.contains("private-health-error"))
    }

    @Test fun recentPastHealthCheckCanStillReportCoarseHealth() {
        val now = System.currentTimeMillis() / 1_000L
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
            state = TransitProviderHealthState.DEGRADED,
            checkedAtEpochSeconds = now - 80_000L,
        )
        val summary = ProviderProbeDiagnostics.render(health, 250L)
        assertEquals("degraded", summary.value("provider_state"))
        assertEquals("250ms_to_1s", summary.value("latency_bucket"))
    }


    @Test fun exact24HourBoundaryRemainsMeasuredButOlderEvidenceExpires() {
        val now = 1_800_000_000L
        val timely = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = now - 86_400L,
        )
        val expired = timely.copy(checkedAtEpochSeconds = now - 86_401L)
        val validSummary = ProviderProbeDiagnostics.renderAt(timely, 100L, now)
        val expiredSummary = ProviderProbeDiagnostics.renderAt(expired, 100L, now)

        assertEquals("ready", validSummary.value("provider_state"))
        assertEquals("under_250ms", validSummary.value("latency_bucket"))
        assertEquals("not_measured", expiredSummary.value("provider_state"))
        assertEquals("not_measured", expiredSummary.value("latency_bucket"))
    }

    @Test fun providerFutureClockSkewIsInclusiveAt30SecondsOnly() {
        val now = 1_800_000_000L
        val withinSkew = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
            state = TransitProviderHealthState.DEGRADED,
            checkedAtEpochSeconds = now + 30L,
        )
        val pastTolerance = withinSkew.copy(checkedAtEpochSeconds = now + 31L)

        val validSummary = ProviderProbeDiagnostics.renderAt(withinSkew, 600L, now)
        val invalidSummary = ProviderProbeDiagnostics.renderAt(pastTolerance, 600L, now)
        assertEquals("degraded", validSummary.value("provider_state"))
        assertEquals("250ms_to_1s", validSummary.value("latency_bucket"))
        assertEquals("not_measured", invalidSummary.value("provider_state"))
        assertEquals("not_measured", invalidSummary.value("latency_bucket"))
    }

    private fun String.value(key: String): String =
        lineSequence().first { it.startsWith("$key=") }.substringAfter('=')
}
