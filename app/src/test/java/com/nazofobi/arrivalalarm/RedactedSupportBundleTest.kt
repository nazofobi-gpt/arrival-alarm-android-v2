package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactedSupportBundleTest {
    private val now = 1_800_000_000L
    private val privateDetail =
        "Authorization: Bearer private-token; location=52.123,8.456; /secret/endpoint"

    private fun state() = SettingsReadinessState(
        themeMode = ArrivalThemeMode.SYSTEM,
        locationPermission = true,
        notificationPermission = false,
        locationServicesAvailable = true,
        bluetoothPermission = false,
        guidancePermissionState = GuidancePermissionState.PARTIAL,
        audioRoute = GuidanceAudioRoute.DEVICE,
        dataState = NationwideDataState.Error(privateDetail),
        backgroundJourneyActive = false,
        connectorConfigured = true,
        connectorConnected = false,
    )

    private fun sample(
        providerId: String = ArrivalAlarmTransitProviders.GermanyRealtime.id,
        checkedAt: Long = now,
        state: TransitProviderHealthState = TransitProviderHealthState.READY,
        latency: Long? = 300L,
    ) = ProviderProbeSample(
        health = TransitProviderHealth(
            providerId = providerId,
            state = state,
            checkedAtEpochSeconds = checkedAt,
            detail = privateDetail,
        ),
        latencyMillis = latency,
    )

    private fun String.fields(): Map<String, String> =
        trimEnd('\n').lines().associate { line ->
            assertEquals("One delimiter per field", 1, line.count { it == '=' })
            line.substringBefore('=') to line.substringAfter('=')
        }

    @Test fun bundleIsBoundedAllowlistedAndDoesNotLeakPrivateDetails() {
        val report = RedactedSupportBundle.render(
            state(), now, listOf(sample()),
        )
        val fields = report.fields()
        assertTrue(report.endsWith("\n"))
        assertEquals(25, report.trimEnd('\n').lines().size)
        assertEquals(25, fields.size)
        assertEquals("VARIS_REDACTED_SUPPORT_BUNDLE_V1", fields["schema"])
        assertEquals("local_capabilities_and_provider_health_only", fields["scope"])
        assertEquals("VARIS_SUPPORT_DIAGNOSTICS_V1", fields["support_schema"])
        assertEquals("error", fields["support_transit_index"])
        assertEquals("ready", fields["provider_realtime_state"])
        assertEquals("250ms_to_1s", fields["provider_realtime_latency_bucket"])
        assertEquals("not_measured", fields["provider_local_static_state"])
        assertEquals("not_measured", fields["provider_enrichment_state"])
        assertFalse(report.contains(privateDetail))
        assertFalse(report.contains("private-token"))
        assertFalse(report.contains("52.123"))
        assertFalse(report.contains("/secret/endpoint"))
        assertFalse(report.contains(now.toString()))
    }

    @Test fun staleFutureUnknownAndDuplicateProbesFailClosed() {
        val samples = listOf(
            sample(checkedAt = now - 86_401L),
            sample(checkedAt = now + 31L),
            sample(checkedAt = 0L),
            sample(checkedAt = Long.MAX_VALUE),
        )
        for (candidate in samples) {
            val fields = RedactedSupportBundle.render(
                state(), now, listOf(candidate),
            ).fields()
            assertEquals("not_measured", fields["provider_realtime_state"])
            assertEquals("not_measured", fields["provider_realtime_latency_bucket"])
        }
        val unknown = RedactedSupportBundle.render(
            state(), now, listOf(sample(providerId = privateDetail)),
        )
        assertEquals("not_measured", unknown.fields()["provider_realtime_state"])
        assertFalse(unknown.contains(privateDetail))

        val duplicate = RedactedSupportBundle.render(
            state(), now, listOf(
                sample(state = TransitProviderHealthState.READY),
                sample(state = TransitProviderHealthState.UNAVAILABLE),
            ),
        ).fields()
        assertEquals("not_measured", duplicate["provider_realtime_state"])
        assertEquals("not_measured", duplicate["provider_realtime_latency_bucket"])
    }

    @Test fun oversizedSampleSetCannotSelectApparentHealthyProvider() {
        val oversized = List(17) { sample() }
        val fields = RedactedSupportBundle.render(state(), now, oversized).fields()
        assertEquals("not_measured", fields["provider_realtime_state"])
        assertEquals("not_measured", fields["provider_realtime_latency_bucket"])
        assertEquals(25, fields.size)
    }

    @Test fun providerSlotsRemainIndependentAndDoNotClaimAppReadiness() {
        val report = RedactedSupportBundle.render(
            state(), now, listOf(
                sample(
                    providerId = ArrivalAlarmTransitProviders.LocalStatic.id,
                    state = TransitProviderHealthState.READY,
                    latency = 50L,
                ),
                sample(
                    state = TransitProviderHealthState.DEGRADED,
                    latency = 5_000L,
                ),
                sample(
                    providerId = ArrivalAlarmTransitProviders.DbEnrichment.id,
                    state = TransitProviderHealthState.UNAVAILABLE,
                    latency = null,
                ),
            ),
        )
        val fields = report.fields()
        assertEquals("ready", fields["provider_local_static_state"])
        assertEquals("under_250ms", fields["provider_local_static_latency_bucket"])
        assertEquals("degraded", fields["provider_realtime_state"])
        assertEquals("over_5s", fields["provider_realtime_latency_bucket"])
        assertEquals("unavailable", fields["provider_enrichment_state"])
        assertEquals("not_measured", fields["provider_enrichment_latency_bucket"])
        assertEquals("no", fields["support_connector_connected"])
        assertFalse(report.contains(privateDetail))
    }

    @Test fun userInitiatedShareWithoutProviderSamplesKeepsHealthUnverified() {
        // Settings does not issue active probes just to prepare a share payload.
        val report = RedactedSupportBundle.render(state(), now)
        val fields = report.fields()
        assertEquals("VARIS_REDACTED_SUPPORT_BUNDLE_V1", fields["schema"])
        assertEquals(25, fields.size)
        for (slot in listOf("local_static", "realtime", "enrichment")) {
            assertEquals("not_measured", fields["provider_${slot}_state"])
            assertEquals("not_measured", fields["provider_${slot}_latency_bucket"])
        }
        assertEquals("no", fields["support_connector_connected"])
        assertFalse(report.contains(privateDetail))
        assertFalse(report.contains("private-token"))
    }

    @Test fun providerProjectionRejectsMalformedOrExpandedSerializerOutput() {
        val approved = listOf(
            "schema=VARIS_PROVIDER_PROBE_V1",
            "scope=provider_health_only",
            "provider_role=realtime",
            "provider_state=ready",
            "latency_bucket=under_250ms",
        )
        fun project(lines: List<String>): Pair<String, String> =
            RedactedSupportBundle.reviewedProviderFields(
                lines.joinToString("\n", postfix = "\n"), "realtime",
            )
        val fallback = "not_measured" to "not_measured"
        assertEquals("ready" to "under_250ms", project(approved))
        assertEquals(fallback, project(approved + "endpoint=private-token"))
        assertEquals(fallback, project(approved + "provider_state=ready"))
        assertEquals(fallback, project(approved.reversed()))
        assertEquals(fallback, project(approved.map {
            if (it == "provider_state=ready") "provider_state=private-token" else it
        }))
        assertEquals(fallback, project(approved.map {
            if (it == "latency_bucket=under_250ms") "latency_bucket=52.123,8.456" else it
        }))
        assertEquals(fallback, project(approved.map {
            if (it == "provider_role=realtime") "provider_role=enrichment" else it
        }))
        assertEquals(fallback, project(approved.map {
            if (it == "scope=provider_health_only") "scope=private" else it
        }))
        assertEquals(fallback, project(approved.map {
            if (it == "provider_state=ready") "provider_state=ready=private" else it
        }))
    }

    @Test fun unmeasuredProviderHealthCannotCarryMeasuredLatency() {
        // A syntactically valid serializer field must not imply a real measurement
        // when the provider itself has no verified health evidence.
        val malformed = listOf(
            "schema=VARIS_PROVIDER_PROBE_V1",
            "scope=provider_health_only",
            "provider_role=realtime",
            "provider_state=not_measured",
            "latency_bucket=under_250ms",
        ).joinToString("\n", postfix = "\n")

        assertEquals(
            "not_measured" to "not_measured",
            RedactedSupportBundle.reviewedProviderFields(malformed, "realtime"),
        )
    }

    @Test fun oversizedSerializerInputsFailClosedBeforeFieldProjection() {
        val approvedProvider = listOf(
            "schema=VARIS_PROVIDER_PROBE_V1",
            "scope=provider_health_only",
            "provider_role=realtime",
            "provider_state=ready",
            "latency_bucket=under_250ms",
        ).joinToString("\n", postfix = "\n")
        assertTrue(approvedProvider.length < 512)
        assertEquals(
            "ready" to "under_250ms",
            RedactedSupportBundle.reviewedProviderFields(approvedProvider, "realtime"),
        )
        assertEquals(
            "not_measured" to "not_measured",
            RedactedSupportBundle.reviewedProviderFields(
                approvedProvider + "a".repeat(4096), "realtime",
            ),
        )

        val approvedCapabilities = SupportDiagnostics.render(state(), now)
        assertTrue(approvedCapabilities.length < 2048)
        assertEquals(17, RedactedSupportBundle.reviewedCapabilityFields(approvedCapabilities).size)
        assertEquals(
            listOf("support_snapshot=not_measured"),
            RedactedSupportBundle.reviewedCapabilityFields(
                approvedCapabilities + "a".repeat(4096),
            ),
        )
    }

    @Test fun capabilityProjectionRejectsAsciiSafeSecretsAndInvalidTypedValues() {
        val approved = SupportDiagnostics.render(state(), now)
        val fallback = listOf("support_snapshot=not_measured")
        val substitutions = listOf(
            "location_permission=yes" to "location_permission=private_token",
            "location_services_enabled=yes" to "location_services_enabled=possibly",
            "notification_permission=no" to "notification_permission=access_granted",
            "bluetooth_permission=no" to "bluetooth_permission=private_token",
            "guidance_permission=partial" to "guidance_permission=private_token",
            "audio_route=device" to "audio_route=secret_device",
            "tracking_armed=no" to "tracking_armed=private_token",
            "connector_configured=yes" to "connector_configured=unknown",
            "connector_connected=no" to "connector_connected=private_token",
            "transit_index=error" to "transit_index=private_endpoint",
            "installed_stop_count=unknown" to "installed_stop_count=private_token",
            "installed_stop_count=unknown" to "installed_stop_count=999999999999999999",
            "installed_stop_count=unknown" to "installed_stop_count=00001",
            "feed_age_bucket=unknown" to "feed_age_bucket=private_timestamp",
            "search_probe=not_measured" to "search_probe=private_token",
            "routing_probe=not_measured" to "routing_probe=private_token",
            "realtime_probe=not_measured" to "realtime_probe=private_token",
        )
        for ((before, after) in substitutions) {
            assertTrue("Test setup must target an existing field: $before", approved.contains(before))
            val malformed = approved.replaceFirst(before, after)
            assertEquals("Unsafe field: $after", fallback,
                RedactedSupportBundle.reviewedCapabilityFields(malformed))
        }
        // The independent export validation must also retain all current legitimate fields.
        assertEquals(17, RedactedSupportBundle.reviewedCapabilityFields(approved).size)
    }

    @Test fun capabilityProjectionRejectsSchemaAndScopeDrift() {
        val approved = SupportDiagnostics.render(state(), now)
        val reviewed = RedactedSupportBundle.reviewedCapabilityFields(approved)
        assertEquals(17, reviewed.size)
        assertEquals("support_schema=VARIS_SUPPORT_DIAGNOSTICS_V1", reviewed.first())
        assertEquals("support_scope=capabilities_only", reviewed[1])

        val fallback = listOf("support_snapshot=not_measured")
        val invalid = listOf(
            approved.replaceFirst(
                "schema=VARIS_SUPPORT_DIAGNOSTICS_V1", "schema=VARIS_SUPPORT_DIAGNOSTICS_V2",
            ),
            approved.replaceFirst("scope=capabilities_only", "scope=private"),
            approved.replaceFirst("location_permission=", "location_access="),
            approved.replaceFirst("location_permission=yes", "location_permission=yes=private-token"),
            approved + "unreviewed_field=private-token\n",
            approved.replaceFirst(
                "scope=capabilities_only\n", "scope=capabilities_only\nscope=private\n",
            ),
        )
        for ((index, candidate) in invalid.withIndex()) {
            assertEquals("invalid capability shape #$index", fallback,
                RedactedSupportBundle.reviewedCapabilityFields(candidate))
        }
    }
}
