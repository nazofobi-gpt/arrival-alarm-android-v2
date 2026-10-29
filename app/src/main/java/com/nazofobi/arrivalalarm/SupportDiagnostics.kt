package com.nazofobi.arrivalalarm

import java.util.Locale

/**
 * Produces a locally generated, allowlisted support snapshot without location,
 * journey history, endpoint URLs, raw provider errors or authentication state.
 */
internal object SupportDiagnostics {
    // Reset device/provider clocks (for example 1970) are not feed-age evidence.
    private const val MIN_TRUSTWORTHY_FEED_EPOCH_SECONDS = 946_684_800L

    fun render(state: SettingsReadinessState, nowEpochSeconds: Long): String {
        val ready = state.dataState as? NationwideDataState.Ready
        val dataStatus = when (val data = state.dataState) {
            NationwideDataState.Idle -> "not_installed"
            is NationwideDataState.Loading -> "loading"
            // An empty or invalid index must not appear as installed evidence.
            is NationwideDataState.Ready ->
                if (data.stopCount > 0) "index_present_unverified" else "index_empty"
            is NationwideDataState.Error -> "error"
        }
        val feedAge = when {
            ready == null || ready.stopCount <= 0 ||
                !ready.fetchedAtEpochSeconds.isDisplayableDepartureEpochSeconds() ||
                !nowEpochSeconds.isDisplayableDepartureEpochSeconds() ||
                ready.fetchedAtEpochSeconds < MIN_TRUSTWORTHY_FEED_EPOCH_SECONDS ||
                nowEpochSeconds < MIN_TRUSTWORTHY_FEED_EPOCH_SECONDS -> "unknown"
            nowEpochSeconds < ready.fetchedAtEpochSeconds -> "clock_mismatch"
            nowEpochSeconds - ready.fetchedAtEpochSeconds < 86_400L -> "under_24h"
            nowEpochSeconds - ready.fetchedAtEpochSeconds < 604_800L -> "1_to_7d"
            else -> "over_7d"
        }
        fun flag(value: Boolean): String = if (value) "yes" else "no"
        // Only emit reviewed coarse states; never include query, route or provider detail.
        fun functionProbe(health: TransitFunctionHealth): String = when (health) {
            TransitFunctionHealth.UNVERIFIED -> "not_measured"
            TransitFunctionHealth.WORKING -> "working"
            TransitFunctionHealth.DEGRADED -> "degraded"
            TransitFunctionHealth.FAILED -> "failed"
        }

        return listOf(
            "schema=VARIS_SUPPORT_DIAGNOSTICS_V1",
            "scope=capabilities_only",
            "location_permission=${flag(state.locationPermission)}",
            "location_services_enabled=${flag(state.locationServicesAvailable)}",
            "notification_permission=${flag(state.notificationPermission)}",
            "bluetooth_permission=${flag(state.bluetoothPermission)}",
            "guidance_permission=${state.guidancePermissionState.name.lowercase(Locale.ROOT)}",
            "audio_route=${state.audioRoute.name.lowercase(Locale.ROOT)}",
            "tracking_armed=${flag(state.backgroundJourneyActive)}",
            "connector_configured=${flag(state.connectorConfigured)}",
            "connector_connected=${flag(state.connectorConfigured && state.connectorConnected)}",
            "transit_index=${dataStatus}",
            "installed_stop_count=${ready?.stopCount?.coerceAtLeast(0) ?: "unknown"}",
            "feed_age_bucket=${feedAge}",
            "search_probe=${functionProbe(state.searchHealth)}",
            "routing_probe=${functionProbe(state.routingHealth)}",
            "realtime_probe=not_measured",
        ).joinToString(separator = "\n", postfix = "\n")
    }
}
