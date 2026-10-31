package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user-shareable provider probe is a strict allowlist, not a provider log.
 * Matrix tests deliberately pass hostile detail alongside valid measurements.
 */
class ProviderProbePrivacyContractTest {
    private val now = 1_800_000_000L
    private val approvedKeys = setOf(
        "schema", "scope", "provider_role", "provider_state", "latency_bucket",
    )

    @Test fun knownProviderAndHealthMatrixCannotExposeRawDetails() {
        val roles = listOf(
            ArrivalAlarmTransitProviders.LocalStatic.id to "local_static",
            ArrivalAlarmTransitProviders.GermanyRealtime.id to "realtime",
            ArrivalAlarmTransitProviders.DbEnrichment.id to "enrichment",
        )
        val states = listOf(
            TransitProviderHealthState.READY to "ready",
            TransitProviderHealthState.DEGRADED to "degraded",
            TransitProviderHealthState.UNAVAILABLE to "unavailable",
        )
        val secret = "Authorization: Bearer private-token; location=52.123,8.456; endpoint=/private"
        for ((providerId, expectedRole) in roles) {
            for ((state, expectedState) in states) {
                val health = TransitProviderHealth(
                    providerId = providerId,
                    state = state,
                    checkedAtEpochSeconds = now,
                    detail = secret,
                )
                val summary = ProviderProbeDiagnostics.renderAt(health, 500L, now)
                val fields = summary.parseFields()
                assertEquals(approvedKeys, fields.keys)
                assertEquals(5, summary.trimEnd('\n').lines().size)
                assertEquals("VARIS_PROVIDER_PROBE_V1", fields["schema"])
                assertEquals("provider_health_only", fields["scope"])
                assertEquals(expectedRole, fields["provider_role"])
                assertEquals(expectedState, fields["provider_state"])
                assertEquals("250ms_to_1s", fields["latency_bucket"])
                assertFalse(summary.contains(secret))
                assertFalse(summary.contains("52.123"))
                assertFalse(summary.contains(now.toString()))
                assertTrue(summary.endsWith("\n"))
            }
        }
    }

    @Test fun latencyThresholdsUseOnlyReviewedBuckets() {
        val health = TransitProviderHealth(
            providerId = ArrivalAlarmTransitProviders.GermanyRealtime.id,
            state = TransitProviderHealthState.READY,
            checkedAtEpochSeconds = now,
            detail = "private-latency-trace",
        )
        val samples = listOf(
            -1L to "not_measured",
            0L to "under_250ms",
            249L to "under_250ms",
            250L to "250ms_to_1s",
            999L to "250ms_to_1s",
            1_000L to "1s_to_5s",
            4_999L to "1s_to_5s",
            5_000L to "over_5s",
            Long.MAX_VALUE to "over_5s",
        )
        for ((latency, expected) in samples) {
            val summary = ProviderProbeDiagnostics.renderAt(health, latency, now)
            assertEquals("latency=$latency", expected, summary.parseFields()["latency_bucket"])
            assertFalse(summary.contains("private-latency-trace"))
        }
        assertEquals(
            "not_measured",
            ProviderProbeDiagnostics.renderAt(health, null, now).parseFields()["latency_bucket"],
        )
    }

    @Test fun staleAndFutureMeasurementsCannotReportHealthOrLatency() {
        for (checkedAt in listOf(now - 86_401L, now + 31L, 0L, Long.MAX_VALUE)) {
            val health = TransitProviderHealth(
                providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
                state = TransitProviderHealthState.READY,
                checkedAtEpochSeconds = checkedAt,
                detail = "token=private-secret",
            )
            val summary = ProviderProbeDiagnostics.renderAt(health, 10L, now)
            val fields = summary.parseFields()
            assertEquals("local_static", fields["provider_role"])
            assertEquals("not_measured", fields["provider_state"])
            assertEquals("not_measured", fields["latency_bucket"])
            assertEquals(approvedKeys, fields.keys)
            assertFalse(summary.contains("private-secret"))
        }
    }

    private fun String.parseFields(): Map<String, String> =
        trimEnd('\n').lines().associate { line ->
            assertEquals("Expected exactly one key/value delimiter", 1, line.count { it == '=' })
            line.substringBefore('=') to line.substringAfter('=')
        }
}
