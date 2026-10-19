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
            checkedAtEpochSeconds = 987654L,
            detail = "private-provider-detail",
        )
        val summary = ProviderProbeDiagnostics.render(health, 250L)
        assertEquals("local_static", summary.value("provider_role"))
        assertEquals("ready", summary.value("provider_state"))
        assertEquals("250ms_to_1s", summary.value("latency_bucket"))
        assertFalse(summary.contains("private-provider-detail"))
        assertFalse(summary.contains("987654"))
    }

    private fun String.value(key: String): String =
        lineSequence().first { it.startsWith("$key=") }.substringAfter('=')
}
