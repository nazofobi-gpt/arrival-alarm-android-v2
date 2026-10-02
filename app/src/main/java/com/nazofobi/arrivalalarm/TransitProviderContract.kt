package com.nazofobi.arrivalalarm

enum class TransitProviderRole {
    LOCAL_STATIC,
    REALTIME,
    ENRICHMENT,
    REGIONAL,
}

enum class TransitProviderCapability {
    STOP_SEARCH,
    LOCATION_SEARCH,
    NEARBY_STOPS,
    STATIC_ROUTING,
    DEPARTURES,
    TRIP_UPDATES,
    SERVICE_ALERTS,
    PLATFORM_UPDATES,
    GEOCODING,
}

enum class TransitProviderHealthState {
    READY,
    DEGRADED,
    UNAVAILABLE,
}

enum class TransitProviderFreshnessState {
    FRESH,
    STALE,
    NOT_APPLICABLE,
    UNKNOWN,
}

data class TransitProviderDescriptor(
    val id: String,
    val label: String,
    val role: TransitProviderRole,
    val capabilities: Set<TransitProviderCapability>,
    val networkRequired: Boolean,
) {
    init {
        require(id.isNotBlank()) { "provider id must not be blank" }
        require(label.isNotBlank()) { "provider label must not be blank" }
        require(capabilities.isNotEmpty()) { "provider capabilities must not be empty" }
    }

    fun supports(capability: TransitProviderCapability): Boolean = capability in capabilities
}

data class TransitProviderProvenance(
    val providerId: String,
    val sourceLabel: String,
    val sourceVersion: String? = null,
    val fetchedAtEpochSeconds: Long? = null,
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(sourceLabel.isNotBlank()) { "sourceLabel must not be blank" }
        require(fetchedAtEpochSeconds == null || fetchedAtEpochSeconds >= 0L) {
            "fetchedAtEpochSeconds must be non-negative"
        }
    }
}

data class TransitProviderHealth(
    val providerId: String,
    val state: TransitProviderHealthState,
    val checkedAtEpochSeconds: Long,
    val detail: String? = null,
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(checkedAtEpochSeconds >= 0L) { "checkedAtEpochSeconds must be non-negative" }
    }
}

data class TransitProviderFreshness(
    val state: TransitProviderFreshnessState,
    val ageSeconds: Long? = null,
    val updatedAtEpochSeconds: Long? = null,
) {
    init {
        require(ageSeconds == null || ageSeconds >= 0L) { "ageSeconds must be non-negative" }
        require(updatedAtEpochSeconds == null || updatedAtEpochSeconds >= 0L) {
            "updatedAtEpochSeconds must be non-negative"
        }
    }

    companion object {
        fun evaluate(
            updatedAtEpochSeconds: Long?,
            nowEpochSeconds: Long,
            staleAfterSeconds: Long,
            applicable: Boolean = true,
        ): TransitProviderFreshness {
            require(nowEpochSeconds >= 0L)
            require(staleAfterSeconds >= 0L)
            if (!applicable) {
                return TransitProviderFreshness(TransitProviderFreshnessState.NOT_APPLICABLE)
            }
            if (updatedAtEpochSeconds == null) {
                return TransitProviderFreshness(TransitProviderFreshnessState.UNKNOWN)
            }
            require(updatedAtEpochSeconds >= 0L)
            val age = (nowEpochSeconds - updatedAtEpochSeconds).coerceAtLeast(0L)
            return TransitProviderFreshness(
                state = if (age > staleAfterSeconds) {
                    TransitProviderFreshnessState.STALE
                } else {
                    TransitProviderFreshnessState.FRESH
                },
                ageSeconds = age,
                updatedAtEpochSeconds = updatedAtEpochSeconds,
            )
        }
    }
}

sealed interface TransitProviderOutcome<out T> {
    val provenance: TransitProviderProvenance?

    data class Results<T>(
        val value: T,
        override val provenance: TransitProviderProvenance,
        val freshness: TransitProviderFreshness = TransitProviderFreshness(
            TransitProviderFreshnessState.NOT_APPLICABLE,
        ),
    ) : TransitProviderOutcome<T>

    data class NoResult(
        override val provenance: TransitProviderProvenance,
        val freshness: TransitProviderFreshness = TransitProviderFreshness(
            TransitProviderFreshnessState.NOT_APPLICABLE,
        ),
    ) : TransitProviderOutcome<Nothing>

    data class Stale<T>(
        val value: T,
        override val provenance: TransitProviderProvenance,
        val freshness: TransitProviderFreshness,
    ) : TransitProviderOutcome<T> {
        init {
            require(freshness.state == TransitProviderFreshnessState.STALE) {
                "Stale outcome requires STALE freshness"
            }
        }
    }

    data class Unavailable(
        val reason: TransitProviderUnavailableReason,
        val retryable: Boolean,
        val detail: String? = null,
        override val provenance: TransitProviderProvenance? = null,
    ) : TransitProviderOutcome<Nothing>
}

enum class TransitProviderUnavailableReason {
    NOT_CONFIGURED,
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    UPSTREAM_ERROR,
    INVALID_RESPONSE,
    LOCAL_DATA_NOT_READY,
}

object ArrivalAlarmTransitProviders {
    val LocalStatic = TransitProviderDescriptor(
        id = "local-static",
        label = "Germany Full GTFS local",
        role = TransitProviderRole.LOCAL_STATIC,
        capabilities = setOf(
            TransitProviderCapability.STOP_SEARCH,
            TransitProviderCapability.NEARBY_STOPS,
            TransitProviderCapability.STATIC_ROUTING,
            TransitProviderCapability.DEPARTURES,
        ),
        networkRequired = false,
    )

    val GermanyRealtime = TransitProviderDescriptor(
        id = "germany-gtfs-rt",
        label = "gtfs.de Germany-wide GTFS-RT",
        role = TransitProviderRole.REALTIME,
        capabilities = setOf(
            TransitProviderCapability.TRIP_UPDATES,
            TransitProviderCapability.SERVICE_ALERTS,
            TransitProviderCapability.PLATFORM_UPDATES,
        ),
        networkRequired = true,
    )

    val DbEnrichment = TransitProviderDescriptor(
        id = "db-transport-rest",
        label = "v6.db.transport.rest",
        role = TransitProviderRole.ENRICHMENT,
        capabilities = setOf(
            TransitProviderCapability.STOP_SEARCH,
            TransitProviderCapability.LOCATION_SEARCH,
            TransitProviderCapability.NEARBY_STOPS,
            TransitProviderCapability.DEPARTURES,
            TransitProviderCapability.GEOCODING,
        ),
        networkRequired = true,
    )
}
