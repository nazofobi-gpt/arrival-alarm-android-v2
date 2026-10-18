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
        assertEquals("unavailable", summary.value("provider_state"))
        assertEquals("250ms_to_1s", summary.value("latency_bucket"))
        assertFalse(summary.contains(hidden))
        assertFalse(summary.contains("123456"))
    }

    private fun String.value(key: String): String =
        lineSequence().first { it.startsWith("$key=") }.substringAfter('=')
}
