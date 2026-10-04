package com.nazofobi.arrivalalarm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class RouteRealtimeState {
    FRESH_MATCHED,
    FRESH_NO_MATCH,
    STALE,
    UNAVAILABLE,
}

data class RouteRealtimeInfo(
    val state: RouteRealtimeState,
    val sourceLabel: String? = null,
    val updatedAtEpochSeconds: Long? = null,
    val ageSeconds: Long? = null,
    val matchedTripIds: List<String> = emptyList(),
    val cancelledTripIds: List<String> = emptyList(),
    val unavailableReason: String? = null,
)

internal class ProductionRealtimeJourneyOverlay(
    private val fetch: () -> GermanyRealtimeFetchResult,
    private val match: (GermanyRealtimeFetchResult, Long) -> GermanyRealtimeOverlayResult,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val agencyTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Berlin"),
    private val isFrequencyTrip: (String) -> Boolean = { false },
) {
    fun enrich(options: List<RouteOption>): List<RouteOption> {
        if (options.isEmpty()) return options
        val now = nowEpochSeconds()
        val overlay = runCatching { match(fetch(), now) }.getOrElse { error ->
            GermanyRealtimeOverlayResult.Unavailable(
                reason = GermanyRealtimeUnavailableReason.NETWORK,
                detail = error.message,
            )
        }
        return when (overlay) {
            is GermanyRealtimeOverlayResult.Matched -> when (overlay.freshness) {
                GermanyRealtimeOverlayFreshnessState.FRESH ->
                    options.map { option -> applyFresh(option, overlay) }
                GermanyRealtimeOverlayFreshnessState.STALE ->
                    options.map { option ->
                        option.copy(
                            realtime = RouteRealtimeInfo(
                                state = RouteRealtimeState.STALE,
                                sourceLabel = overlay.provenance.source,
                                updatedAtEpochSeconds = overlay.updatedAtEpochSeconds(),
                                ageSeconds = overlay.ageSeconds,
                                matchedTripIds = overlay.tripOverlays
                                    .filterNot { frequencyTrip(it.staticTrip.id) }
                                    .map { it.staticTrip.id }
                                    .filter { it in option.tripIds },
                                cancelledTripIds = emptyList(),
                            ),
                        )
                    }
            }
            is GermanyRealtimeOverlayResult.NoMatch -> options.map { option ->
                option.copy(
                    realtime = RouteRealtimeInfo(
                        state = if (
                            overlay.freshness == GermanyRealtimeOverlayFreshnessState.FRESH
                        ) {
                            RouteRealtimeState.FRESH_NO_MATCH
                        } else {
                            RouteRealtimeState.STALE
                        },
                        sourceLabel = overlay.provenance.source,
                        updatedAtEpochSeconds = overlay.updatedAtEpochSeconds(),
                        ageSeconds = overlay.ageSeconds,
                    ),
                )
            }
            is GermanyRealtimeOverlayResult.Unavailable -> options.map { option ->
                option.copy(
                    realtime = RouteRealtimeInfo(
                        state = RouteRealtimeState.UNAVAILABLE,
                        unavailableReason = buildString {
                            append(overlay.reason.name)
                            overlay.detail?.takeIf { it.isNotBlank() }?.let {
                                append(": ").append(it)
                            }
                        },
                    ),
                )
            }
        }
    }

    private fun applyFresh(
        option: RouteOption,
        overlay: GermanyRealtimeOverlayResult.Matched,
    ): RouteOption {
        val tripOverlays = overlay.tripOverlays.filter {
            it.staticTrip.id in option.tripIds && !frequencyTrip(it.staticTrip.id)
        }
        if (tripOverlays.isEmpty()) {
            return option.copy(
                realtime = RouteRealtimeInfo(
                    state = RouteRealtimeState.FRESH_NO_MATCH,
                    sourceLabel = overlay.provenance.source,
                    updatedAtEpochSeconds = overlay.updatedAtEpochSeconds(),
                    ageSeconds = overlay.ageSeconds,
                ),
            )
        }

        val updates = tripOverlays.flatMap { it.stopUpdates }
        val enrichedStops = option.stops.map { stop ->
            val matching = stop.id?.let { stopId ->
                updates.filter { it.scheduledStopId == stopId }
            }.orEmpty()
            if (matching.isEmpty()) {
                stop
            } else {
                val arrival = matching.firstNotNullOfOrNull { update ->
                    realtimeIso(
                        epochSeconds = update.arrivalTimeEpochSeconds,
                        plannedIso = stop.plannedArrival,
                        delaySeconds = update.arrivalDelaySeconds,
                    )
                }
                val departure = matching.asReversed().firstNotNullOfOrNull { update ->
                    realtimeIso(
                        epochSeconds = update.departureTimeEpochSeconds,
                        plannedIso = stop.plannedDeparture,
                        delaySeconds = update.departureDelaySeconds,
                    )
                }
                stop.copy(
                    arrival = arrival ?: stop.arrival,
                    departure = departure ?: stop.departure,
                    platform = matching.mapNotNull { it.platformCode }.lastOrNull() ?: stop.platform,
                )
            }
        }

        return option.copy(
            stops = enrichedStops,
            realtime = RouteRealtimeInfo(
                state = RouteRealtimeState.FRESH_MATCHED,
                sourceLabel = overlay.provenance.source,
                updatedAtEpochSeconds = overlay.updatedAtEpochSeconds(),
                ageSeconds = overlay.ageSeconds,
                matchedTripIds = tripOverlays.map { it.staticTrip.id }.distinct(),
                cancelledTripIds = tripOverlays.filter { it.cancelled }
                    .map { it.staticTrip.id }
                    .distinct(),
            ),
        )
    }

    private fun frequencyTrip(tripId: String): Boolean =
        runCatching { isFrequencyTrip(tripId) }.getOrDefault(true)

    private fun realtimeIso(
        epochSeconds: Long?,
        plannedIso: String?,
        delaySeconds: Int?,
    ): String? {
        epochSeconds?.let { return iso(it * 1_000L) }
        if (plannedIso == null || delaySeconds == null) return null
        val plannedMillis = parseIsoMillis(plannedIso) ?: return null
        return iso(plannedMillis + delaySeconds * 1_000L)
    }

    private fun parseIsoMillis(raw: String): Long? {
        val normalized = if (
            raw.length >= 6 &&
            raw[raw.length - 3] == ':' &&
            (raw[raw.length - 6] == '+' || raw[raw.length - 6] == '-')
        ) {
            raw.removeRange(raw.length - 3, raw.length - 2)
        } else {
            raw
        }
        return runCatching {
            SimpleDateFormat(ISO_PATTERN, Locale.ROOT).apply {
                isLenient = false
            }.parse(normalized)?.time
        }.getOrNull()
    }

    private fun iso(epochMillis: Long): String {
        val compact = SimpleDateFormat(ISO_PATTERN, Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(Date(epochMillis))
        if (compact.length < 5) return compact
        val offsetStart = compact.length - 5
        return compact.substring(0, offsetStart) +
            compact.substring(offsetStart, offsetStart + 3) + ":" +
            compact.substring(offsetStart + 3)
    }

    private fun GermanyRealtimeOverlayResult.Matched.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds

    private fun GermanyRealtimeOverlayResult.NoMatch.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds

    private companion object {
        const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ssZ"
    }
}
