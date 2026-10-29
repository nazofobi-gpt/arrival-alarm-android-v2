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
    // Small device/provider clock differences must not invalidate a fresh check.
    private const val MAX_PROVIDER_FUTURE_SKEW_SECONDS = 30L
    // An unset device clock near 1970 can agree with an equally bogus provider timestamp.
    // Real provider measurements for this Android app cannot predate the year 2000.
    private const val MIN_TRUSTWORTHY_HEALTH_EPOCH_SECONDS = 946_684_800L

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
        // A device with an unset clock cannot attest to a recent provider check.
        // Compare positive epochs by subtraction to avoid Long overflow near its limit.
        // Reject physically impossible device/provider epochs even when they agree.
        // A matching corrupted clock must never authenticate provider health.
        val validCheckTime = nowEpochSeconds >= MIN_TRUSTWORTHY_HEALTH_EPOCH_SECONDS &&
            checkedAt >= MIN_TRUSTWORTHY_HEALTH_EPOCH_SECONDS &&
            nowEpochSeconds.isDisplayableDepartureEpochSeconds() &&
            checkedAt.isDisplayableDepartureEpochSeconds() && when {
            checkedAt >= nowEpochSeconds ->
                checkedAt - nowEpochSeconds <= MAX_PROVIDER_FUTURE_SKEW_SECONDS
            else ->
                nowEpochSeconds - checkedAt <= MAX_HEALTH_CHECK_AGE_SECONDS
        }
        val recognizedProvider = providerRole != "unknown" && validCheckTime
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
