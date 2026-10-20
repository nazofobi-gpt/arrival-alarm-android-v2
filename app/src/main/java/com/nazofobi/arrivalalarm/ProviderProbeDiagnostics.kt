package com.nazofobi.arrivalalarm

/**
 * Allowlisted provider-health evidence for locally shared diagnostics.
 *
 * This is a serializer, not an active network probe. It deliberately ignores
 * provider error detail, raw provider IDs, exact timestamps and endpoints.
 * An unavailable provider does not imply that the app itself is unready.
 */
internal object ProviderProbeDiagnostics {
    // Old provider outcomes must not be presented as current health evidence.
    private const val MAX_HEALTH_CHECK_AGE_SECONDS = 86_400L

    fun render(health: TransitProviderHealth?, latencyMillis: Long?): String =
        renderAt(health, latencyMillis, System.currentTimeMillis() / 1_000L)

    // Separate wall-clock acquisition from classification for deterministic boundary tests.
    internal fun renderAt(
        health: TransitProviderHealth?,
        latencyMillis: Long?,
        nowEpochSeconds: Long,
    ): String {
        val providerRole = when (health?.providerId) {
            ArrivalAlarmTransitProviders.LocalStatic.id -> "local_static"
            ArrivalAlarmTransitProviders.GermanyRealtime.id -> "realtime"
            ArrivalAlarmTransitProviders.DbEnrichment.id -> "enrichment"
            else -> "unknown"
        }
        // A recognized provider without a real check cannot provide health evidence.
        val checkedAt = health?.checkedAtEpochSeconds ?: 0L
        // Provider health is not evidence if its check time is absent or
        // impossibly far in the future. Allow minor device clock skew.
        val recognizedProvider = providerRole != "unknown" &&
            checkedAt > 0L &&
            checkedAt <= nowEpochSeconds + MAX_PROVIDER_FUTURE_SKEW_SECONDS &&
            (checkedAt >= nowEpochSeconds ||
                nowEpochSeconds - checkedAt <= MAX_HEALTH_CHECK_AGE_SECONDS)
        val providerState = if (!recognizedProvider) "not_measured" else when (health?.state) {
            TransitProviderHealthState.READY -> "ready"
            TransitProviderHealthState.DEGRADED -> "degraded"
            TransitProviderHealthState.UNAVAILABLE -> "unavailable"
            null -> "not_measured"
        }
        val latencyBucket = when {
            !recognizedProvider || latencyMillis == null || latencyMillis < 0L -> "not_measured"
            latencyMillis < 250L -> "under_250ms"
            latencyMillis < 1_000L -> "250ms_to_1s"
            latencyMillis < 5_000L -> "1s_to_5s"
            else -> "over_5s"
        }

        return listOf(
            "schema=VARIS_PROVIDER_PROBE_V1",
            "scope=provider_health_only",
            "provider_role=$providerRole",
            "provider_state=$providerState",
            "latency_bucket=$latencyBucket",
        ).joinToString(separator = "\n", postfix = "\n")
    }
}
