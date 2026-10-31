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
    private val safeValue = Regex("[A-Za-z0-9_]+")
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

    fun render(
        state: SettingsReadinessState,
        nowEpochSeconds: Long,
        providerSamples: List<ProviderProbeSample> = emptyList(),
    ): String {
        // Strictly preserve the existing capability allowlist. If a later
        // serializer changes shape, fail closed rather than copying new fields.
        val pairs = SupportDiagnostics.render(state, nowEpochSeconds)
            .trimEnd('\n')
            .lines()
            .map { line ->
                if (line.count { it == '=' } != 1) null
                else line.substringBefore('=') to line.substringAfter('=')
            }
        val validCapabilities = pairs.size == capabilityKeys.size &&
            pairs.all { it != null && safeValue.matches(it.second) } &&
            pairs.mapNotNull { it?.first } == capabilityKeys
        val capabilityFields = if (validCapabilities) {
            pairs.filterNotNull().map { (key, value) -> "support_${key}=${value}" }
        } else {
            listOf("support_snapshot=not_measured")
        }

        // Never pick a favorable health result out of conflicting duplicates,
        // and never iterate or serialize an unbounded untrusted sample set.
        val safeSamples = providerSamples.takeIf { it.size <= MAX_PROVIDER_SAMPLES }.orEmpty()
        val providerFields = providerSlots.flatMap { (slot, providerId) ->
            val matching = safeSamples.filter { it.health.providerId == providerId }
            val measured = matching.singleOrNull()
            val summary = ProviderProbeDiagnostics.renderAt(
                measured?.health, measured?.latencyMillis, nowEpochSeconds,
            )
            val values = summary.trimEnd('\n').lines().associate {
                it.substringBefore('=') to it.substringAfter('=')
            }
            listOf(
                "provider_${slot}_state=${values["provider_state"] ?: "not_measured"}",
                "provider_${slot}_latency_bucket=${values["latency_bucket"] ?: "not_measured"}",
            )
        }
        return (listOf(
            "schema=VARIS_REDACTED_SUPPORT_BUNDLE_V1",
            "scope=local_capabilities_and_provider_health_only",
        ) + capabilityFields + providerFields).joinToString("\n", postfix = "\n")
    }
}
