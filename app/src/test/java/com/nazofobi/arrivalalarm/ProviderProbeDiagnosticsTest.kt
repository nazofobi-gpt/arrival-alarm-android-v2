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
        val expired = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = now - 86_401L,
        )
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
        val pastTolerance = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
            state = TransitProviderHealthState.DEGRADED,
            checkedAtEpochSeconds = now + 31L,
        )

        val validSummary = ProviderProbeDiagnostics.renderAt(withinSkew, 600L, now)
        val invalidSummary = ProviderProbeDiagnostics.renderAt(pastTolerance, 600L, now)
        assertEquals("degraded", validSummary.value("provider_state"))
        assertEquals("250ms_to_1s", validSummary.value("latency_bucket"))
        assertEquals("not_measured", invalidSummary.value("provider_state"))
        assertEquals("not_measured", invalidSummary.value("latency_bucket"))
    }


    @Test fun invalidDeviceClockDoesNotAuthenticateProviderMeasurement() {
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = 10L,
        )
        for (invalidNow in listOf(0L, -1L)) {
            val summary = ProviderProbeDiagnostics.renderAt(health, 125L, invalidNow)
            assertEquals("not_measured", summary.value("provider_state"))
            assertEquals("not_measured", summary.value("latency_bucket"))
        }
    }

    @Test fun impossibleMatchingClocksNeverAttestProviderHealth() {
        val samples = listOf(
            Long.MAX_VALUE to Long.MAX_VALUE,
            (Long.MAX_VALUE - 5L) to Long.MAX_VALUE,
            (MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS + 1L) to
                (MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS + 1L),
            (MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS - 10L) to
                (MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS + 1L),
        )
        for ((deviceEpoch, providerEpoch) in samples) {
            val health = TransitProviderHealth(
                providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
                state = TransitProviderHealthState.READY,
                checkedAtEpochSeconds = providerEpoch,
            )
            val summary = ProviderProbeDiagnostics.renderAt(health, 1_000L, deviceEpoch)
            assertEquals("not_measured", summary.value("provider_state"))
            assertEquals("not_measured", summary.value("latency_bucket"))
        }
    }

    @Test fun latestDisplayableClockBoundaryKeepsThirtySecondSkewTolerance() {
        val now = MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS - 31L
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
            state = TransitProviderHealthState.DEGRADED,
            checkedAtEpochSeconds = MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS - 1L,
        )
        val summary = ProviderProbeDiagnostics.renderAt(health, 1_000L, now)
        assertEquals("degraded", summary.value("provider_state"))
        assertEquals("1s_to_5s", summary.value("latency_bucket"))
    }

    private fun String.value(key: String): String =
        lineSequence().first { it.startsWith("$key=") }.substringAfter('=')
}
