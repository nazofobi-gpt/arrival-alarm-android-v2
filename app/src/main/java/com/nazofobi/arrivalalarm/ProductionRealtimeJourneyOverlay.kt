package com.nazofobi.arrivalalarm

import java.text.ParsePosition
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
        // Degrade on ordinary provider failures, but never swallow JVM fatal Errors.
        // Exception messages may contain provider URLs, credentials or user coordinates.
        val overlay = try {
            // A cancelled journey must not start a new provider request.
            // Preserve interruption so the caller can end the work promptly.
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Realtime lookup cancelled before dispatch")
            }
            val response = fetch()
            // A request can be cancelled while fetch() is completing. Never
            // run matching against a result that the caller has abandoned.
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Realtime lookup cancelled after fetch")
            }
            match(response, now)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) {
            GermanyRealtimeOverlayResult.Unavailable(
                reason = GermanyRealtimeUnavailableReason.NETWORK,
                detail = null,
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
                        // Only the typed reason is safe to expose to route/UI consumers.
                        unavailableReason = overlay.reason.name,
                    ),
                )
            }
        }
    }

    private fun applyFresh(
        option: RouteOption,
        overlay: GermanyRealtimeOverlayResult.Matched,
    ): RouteOption {
        // Provider updates are unordered. A transfer stop belongs to the
        // arriving leg first and the departing leg last in the selected journey.
        val legOrder = option.tripIds.withIndex().associate { it.value to it.index }
        val tripOverlays = overlay.tripOverlays
            .filter { it.staticTrip.id in legOrder && !frequencyTrip(it.staticTrip.id) }
            .sortedBy { legOrder[it.staticTrip.id] }
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

        // A stop_id is not a stop visit: loop trips can visit it twice with
        // different stop_sequence values. RouteStop has no sequence/leg binding,
        // so an ambiguous update must not overwrite the static time/platform.
        // Keep distinct arriving/departing leg updates at a single transfer stop.
        val ambiguousStopIds = mutableSetOf<String>()
        fun markRepeatedIds(ids: List<String>) {
            ids.groupingBy { it }.eachCount().forEach { (id, count) ->
                if (count > 1) ambiguousStopIds += id
            }
        }
        markRepeatedIds(option.stops.mapNotNull { it.id })
        tripOverlays.forEach { trip ->
            markRepeatedIds(trip.stopUpdates.map { it.scheduledStopId })
        }

        val updates = tripOverlays.flatMap { it.stopUpdates }
        val enrichedStops = option.stops.map { stop ->
            val matching = stop.id?.takeUnless { it in ambiguousStopIds }?.let { stopId ->
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
        try {
            isFrequencyTrip(tripId)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) {
            // If frequency status cannot be verified, omit potentially misbound RT.
            true
        }

    private fun realtimeIso(
        epochSeconds: Long?,
        plannedIso: String?,
        delaySeconds: Int?,
    ): String? {
        // A GTFS-RT Unix timestamp must be positive and safe to convert to millis.
        // Otherwise use the scheduled time plus delay instead of a bogus date.
        epochSeconds?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
            ?.let { return iso(it * 1_000L) }
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
            // SimpleDateFormat.parse(String) accepts a valid prefix and ignores
            // trailing garbage, which can invent a realtime ETA from bad data.
            val position = ParsePosition(0)
            val parsed = SimpleDateFormat(ISO_PATTERN, Locale.ROOT).apply {
                isLenient = false
            }.parse(normalized, position)
            parsed?.takeIf { position.index == normalized.length }?.time
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
