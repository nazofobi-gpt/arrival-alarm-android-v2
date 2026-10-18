package com.nazofobi.arrivalalarm

import java.util.Locale

/**
 * Produces a locally generated, allowlisted support snapshot without location,
 * journey history, endpoint URLs, raw provider errors or authentication state.
 */
internal object SupportDiagnostics {
    fun render(state: SettingsReadinessState, nowEpochSeconds: Long): String {
        val ready = state.dataState as? NationwideDataState.Ready
        val dataStatus = when (state.dataState) {
            NationwideDataState.Idle -> "not_installed"
            is NationwideDataState.Loading -> "loading"
            is NationwideDataState.Ready -> "index_present_unverified"
            is NationwideDataState.Error -> "error"
        }
        val feedAge = when {
            ready == null || ready.fetchedAtEpochSeconds <= 0L || nowEpochSeconds <= 0L -> "unknown"
            nowEpochSeconds < ready.fetchedAtEpochSeconds -> "clock_mismatch"
            nowEpochSeconds - ready.fetchedAtEpochSeconds < 86_400L -> "under_24h"
            nowEpochSeconds - ready.fetchedAtEpochSeconds < 604_800L -> "1_to_7d"
            else -> "over_7d"
        }
        fun flag(value: Boolean): String = if (value) "yes" else "no"

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
            "connector_connected=${flag(state.connectorConnected)}",
            "transit_index=${dataStatus}",
            "installed_stop_count=${ready?.stopCount?.coerceAtLeast(0) ?: "unknown"}",
            "feed_age_bucket=${feedAge}",
            "search_probe=not_measured",
            "routing_probe=not_measured",
            "realtime_probe=not_measured",
        ).joinToString(separator = "\n", postfix = "\n")
    }
}
