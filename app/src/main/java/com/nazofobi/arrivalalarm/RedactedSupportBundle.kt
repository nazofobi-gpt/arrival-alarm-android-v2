package com.nazofobi.arrivalalarm

/**
 * One bounded provider observation. Raw provider details are never copied to
 * the support bundle; only ProviderProbeDiagnostics' reviewed buckets are used.
 */
internal data class ProviderProbeSample(
    val health: TransitProviderHealth,
    val latencyMillis: Long?,
)

/**
 * Composes existing privacy-reviewed local diagnostics and provider health into
 * one deterministic, offline-only support payload. This is not a network probe,
 * crash reporter or proof of real-device/provider acceptance.
 */
internal object RedactedSupportBundle {
    private const val MAX_PROVIDER_SAMPLES = 16
    // Reject oversized serializer regressions before splitting or allocating field lists.
    private const val MAX_PROVIDER_SUMMARY_CHARS = 512
    private const val MAX_CAPABILITY_SUMMARY_CHARS = 2048
    private val safeValue = Regex("[A-Za-z0-9_]+")
    private val binaryValues = setOf("yes", "no")
    // A character-only regex also accepts ASCII secrets such as "private_token".
    // The export boundary must independently review the *value of each key*.
    private val capabilityValues = mapOf(
        "location_permission" to binaryValues,
        "location_services_enabled" to binaryValues,
        "notification_permission" to binaryValues,
        "bluetooth_permission" to binaryValues,
        "guidance_permission" to setOf("denied", "partial", "granted"),
        "audio_route" to setOf("device", "bluetooth_connected", "bluetooth_disconnected"),
        "tracking_armed" to binaryValues,
        "connector_configured" to binaryValues,
        "connector_connected" to binaryValues,
        "transit_index" to setOf(
            "not_installed", "loading", "index_present_unverified", "index_empty", "error",
        ),
        "feed_age_bucket" to setOf("unknown", "clock_mismatch", "under_24h", "1_to_7d", "over_7d"),
        "search_probe" to setOf("not_measured", "working", "degraded", "failed"),
        "routing_probe" to setOf("not_measured", "working", "degraded", "failed"),
        "realtime_probe" to setOf("not_measured"),
    )
    private fun reviewedCapabilityValue(key: String, value: String): Boolean = when (key) {
        "schema" -> value == "VARIS_SUPPORT_DIAGNOSTICS_V1"
        "scope" -> value == "capabilities_only"
        "installed_stop_count" ->
            value == "unknown" || (value.toIntOrNull()?.let { it >= 0 && it.toString() == value } == true)
        else -> capabilityValues[key]?.contains(value) == true
    }
    private val capabilityKeys = listOf(
        "schema", "scope", "location_permission", "location_services_enabled",
        "notification_permission", "bluetooth_permission", "guidance_permission",
        "audio_route", "tracking_armed", "connector_configured", "connector_connected",
        "transit_index", "installed_stop_count", "feed_age_bucket",
        "search_probe", "routing_probe", "realtime_probe",
    )
    private val providerSlots = listOf(
        "local_static" to ArrivalAlarmTransitProviders.LocalStatic.id,
        "realtime" to ArrivalAlarmTransitProviders.GermanyRealtime.id,
        "enrichment" to ArrivalAlarmTransitProviders.DbEnrichment.id,
    )

    // Keep the user-share boundary independent of the lower-level probe serializer.
    // Never export arbitrary values, extra keys, wrong roles or malformed lines.
    internal fun reviewedProviderFields(
        summary: String,
        expectedRole: String,
    ): Pair<String, String> {
        if (summary.length > MAX_PROVIDER_SUMMARY_CHARS) return "not_measured" to "not_measured"
        val requiredKeys = listOf(
            "schema", "scope", "provider_role", "provider_state", "latency_bucket",
        )
        val pairs = summary.trimEnd('\n').lines().map { line ->
            if (line.count { it == '=' } != 1) null
            else line.substringBefore('=') to line.substringAfter('=')
        }
        val fallback = "not_measured" to "not_measured"
        if (pairs.size != requiredKeys.size || pairs.any { it == null } ||
            pairs.mapNotNull { it?.first } != requiredKeys
        ) return fallback
        val fields = pairs.filterNotNull().toMap()
        val allowedStates = setOf("ready", "degraded", "unavailable", "not_measured")
        val allowedLatencies = setOf(
            "under_250ms", "250ms_to_1s", "1s_to_5s", "over_5s", "not_measured",
        )
        val state = fields["provider_state"] ?: return fallback
        val latency = fields["latency_bucket"] ?: return fallback
        if (fields["schema"] != "VARIS_PROVIDER_PROBE_V1" ||
            fields["scope"] != "provider_health_only" ||
            fields["provider_role"] != expectedRole ||
            state !in allowedStates || latency !in allowedLatencies ||
            (state == "not_measured" && latency != "not_measured")
        ) return fallback
        return state to latency
    }

    // A schema/scope change must not silently cross the user-share boundary.
    // Keep this independent of SupportDiagnostics' own serializer implementation.
    internal fun reviewedCapabilityFields(summary: String): List<String> {
        if (summary.length > MAX_CAPABILITY_SUMMARY_CHARS) {
            return listOf("support_snapshot=not_measured")
        }
        val pairs = summary.trimEnd('\n')
            .lines()
            .map { line ->
                if (line.count { it == '=' } != 1) null
                else line.substringBefore('=') to line.substringAfter('=')
            }
        val validCapabilities = pairs.size == capabilityKeys.size &&
            pairs.all {
                it != null && safeValue.matches(it.second) &&
                    reviewedCapabilityValue(it.first, it.second)
            } &&
            pairs.mapNotNull { it?.first } == capabilityKeys &&
            pairs[0]?.second == "VARIS_SUPPORT_DIAGNOSTICS_V1" &&
            pairs[1]?.second == "capabilities_only"
        return if (validCapabilities) {
            pairs.filterNotNull().map { (key, value) -> "support_${key}=${value}" }
        } else {
            listOf("support_snapshot=not_measured")
        }
    }

    fun render(
        state: SettingsReadinessState,
        nowEpochSeconds: Long,
        providerSamples: List<ProviderProbeSample> = emptyList(),
    ): String {
        val capabilityFields = reviewedCapabilityFields(
            SupportDiagnostics.render(state, nowEpochSeconds),
        )

        // Never pick a favorable health result out of conflicting duplicates,
        // and never iterate or serialize an unbounded untrusted sample set.
        val safeSamples = providerSamples.takeIf { it.size <= MAX_PROVIDER_SAMPLES }.orEmpty()
        val providerFields = providerSlots.flatMap { (slot, providerId) ->
            val matching = safeSamples.filter { it.health.providerId == providerId }
            val measured = matching.singleOrNull()
            val summary = ProviderProbeDiagnostics.renderAt(
                measured?.health, measured?.latencyMillis, nowEpochSeconds,
            )
            val (stateValue, latencyValue) = reviewedProviderFields(summary, slot)
            listOf(
                "provider_${slot}_state=$stateValue",
                "provider_${slot}_latency_bucket=$latencyValue",
            )
        }
        return (listOf(
            "schema=VARIS_REDACTED_SUPPORT_BUNDLE_V1",
            "scope=local_capabilities_and_provider_health_only",
        ) + capabilityFields + providerFields).joinToString("\n", postfix = "\n")
    }
}
