package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportDiagnosticsTest {
    private fun state(data: NationwideDataState) = SettingsReadinessState(
        themeMode = ArrivalThemeMode.SYSTEM,
        locationPermission = true,
        notificationPermission = false,
        locationServicesAvailable = true,
        bluetoothPermission = false,
        guidancePermissionState = GuidancePermissionState.PARTIAL,
        audioRoute = GuidanceAudioRoute.DEVICE,
        dataState = data,
        backgroundJourneyActive = true,
        connectorConfigured = true,
        connectorConnected = false,
    )

    @Test fun untrustedProviderErrorIsNeverIncluded() {
        val sensitive = "precise_location_value_and_private_credential"
        val report = SupportDiagnostics.render(
            state(NationwideDataState.Error("network failed: $sensitive")),
            nowEpochSeconds = 1_700_000_000L,
        )
        assertTrue(report.startsWith("schema=VARIS_SUPPORT_DIAGNOSTICS_V1\n"))
        assertTrue(report.contains("transit_index=error\n"))
        assertTrue(report.contains("routing_probe=not_measured\n"))
        assertFalse(report.contains(sensitive))
    }

    @Test fun feedVersionAndSourceDetailsAreNeverIncluded() {
        val sensitive = "raw_feed_source_version_value"
        val report = SupportDiagnostics.render(
            state(NationwideDataState.Ready(120_000, 1_700_000_000L, sensitive)),
            nowEpochSeconds = 1_700_000_100L,
        )
        assertTrue(report.contains("transit_index=index_present_unverified\n"))
        assertTrue(report.contains("installed_stop_count=120000\n"))
        assertTrue(report.contains("feed_age_bucket=under_24h\n"))
        assertFalse(report.contains(sensitive))
    }

    @Test fun feedFreshnessUsesCoarseBucketsOnly() {
        val saved = state(NationwideDataState.Ready(10, 1_000L, "test"))
        assertTrue(SupportDiagnostics.render(saved, 605_800L).contains("feed_age_bucket=over_7d"))
        assertTrue(SupportDiagnostics.render(saved, 999L).contains("feed_age_bucket=clock_mismatch"))
        assertTrue(SupportDiagnostics.render(
            state(NationwideDataState.Loading("unknown provider details")), 1_000L,
        ).contains("feed_age_bucket=unknown"))
    }

    @Test fun outputHasOnlyFixedReviewedKeys() {
        val report = SupportDiagnostics.render(state(NationwideDataState.Idle), 100L)
        val keys = report.trim().lines().map { it.substringBefore('=') }.toSet()
        assertEquals(setOf(
            "schema", "scope", "location_permission", "location_services_enabled",
            "notification_permission", "bluetooth_permission", "guidance_permission",
            "audio_route", "tracking_armed", "connector_configured", "connector_connected",
            "transit_index", "installed_stop_count", "feed_age_bucket",
            "search_probe", "routing_probe", "realtime_probe",
        ), keys)
    }
}
