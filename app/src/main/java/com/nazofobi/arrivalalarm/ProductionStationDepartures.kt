package com.nazofobi.arrivalalarm

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

enum class StationRealtimeState {
    FRESH_MATCHED,
    FRESH_NO_MATCH,
    STALE,
    UNAVAILABLE,
}

data class ProductionStationDeparturesResult(
    val departures: List<Departure>,
    val alerts: List<ServiceAlert> = emptyList(),
    val realtimeState: StationRealtimeState? = null,
    val realtimeSourceLabel: String? = null,
    val updatedAtEpochSeconds: Long? = null,
)

internal class ProductionStationDeparturesPlanner(
    private val index: NationwideTransitIndex,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val agencyTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Berlin"),
    private val maxCandidatesPerServiceDay: Int = 64,
) {
    private val repository = GtfsScheduleRepository(index)

    init {
        require(maxCandidatesPerServiceDay in 1..512)
    }

    fun plan(stopId: String, limit: Int): List<Departure> {
        if (!index.isReady() || stopId.isBlank() || limit <= 0) return emptyList()
        val boundedLimit = limit.coerceIn(1, 64)
        val now = nowMillis()
        val plannedPlatform = repository.stop(stopId)?.platformCode

        return serviceDates(now)
            .flatMap { serviceDate ->
                val earliest = serviceSeconds(now, serviceDate) ?: return@flatMap emptyList()
                repository.candidateTripsAtStop(
                    stopId = stopId,
                    serviceDate = serviceDate,
                    earliestBoardSeconds = earliest,
                    limit = maxCandidatesPerServiceDay,
                ).mapNotNull { candidate ->
                    if (repository.frequenciesForTrip(candidate.trip.id, 1).isNotEmpty()) {
                        return@mapNotNull null
                    }
                    val raw = candidate.departureTime ?: candidate.arrivalTime
                        ?: return@mapNotNull null
                    val scheduled = runCatching {
                        GtfsServiceTime.parse(raw)
                            .resolve(serviceDate, agencyTimeZone)
                            .epochMillis / 1_000L
                    }.getOrNull() ?: return@mapNotNull null
                    if (scheduled < now / 1_000L) return@mapNotNull null
                    val route = repository.route(candidate.trip.routeId)
                    Departure(
                        tripId = candidate.trip.id,
                        line = route?.shortName
                            ?.takeIf { it.isNotBlank() }
                            ?: route?.longName?.takeIf { it.isNotBlank() }
                            ?: candidate.trip.routeId,
                        direction = candidate.trip.headsign
                            ?.takeIf { it.isNotBlank() }
                            ?: route?.longName?.takeIf { it.isNotBlank() }
                            ?: "",
                        scheduledEpochSeconds = scheduled,
                        platform = plannedPlatform,
                        routeId = candidate.trip.routeId,
                    )
                }
            }
            .distinctBy { it.tripId to it.scheduledEpochSeconds }
            .sortedWith(
                compareBy<Departure> { it.scheduledEpochSeconds }
                    .thenBy { it.tripId }
            )
            .take(boundedLimit)
    }

    private fun serviceDates(epochMillis: Long): List<GtfsServiceDate> =
        listOf(
            serviceDate(epochMillis, dayOffset = -1),
            serviceDate(epochMillis),
        )

    private fun serviceDate(epochMillis: Long, dayOffset: Int = 0): GtfsServiceDate {
        val calendar = GregorianCalendar(agencyTimeZone).apply {
            timeInMillis = epochMillis
            add(Calendar.DAY_OF_MONTH, dayOffset)
        }
        return GtfsServiceDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun serviceSeconds(
        epochMillis: Long,
        serviceDate: GtfsServiceDate,
    ): Int? {
        val anchor = GtfsServiceTime.parse("00:00:00")
            .resolve(serviceDate, agencyTimeZone)
            .epochMillis
        val delta = epochMillis - anchor
        if (delta < 0L) return null
        val seconds = delta / 1_000L + if (delta % 1_000L == 0L) 0L else 1L
        return seconds.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }
}

internal class ProductionRealtimeStationOverlay(
    private val fetch: () -> GermanyRealtimeFetchResult,
    private val match: (GermanyRealtimeFetchResult, Long) -> GermanyRealtimeOverlayResult,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    fun enrich(
        stopId: String,
        planned: List<Departure>,
    ): ProductionStationDeparturesResult {
        if (planned.isEmpty()) return ProductionStationDeparturesResult(emptyList())
        val overlay = runCatching {
            match(fetch(), nowEpochSeconds())
        }.getOrElse { error ->
            GermanyRealtimeOverlayResult.Unavailable(
                GermanyRealtimeUnavailableReason.NETWORK,
                error.message,
            )
        }
        return when (overlay) {
            is GermanyRealtimeOverlayResult.Matched -> {
                if (overlay.freshness == GermanyRealtimeOverlayFreshnessState.STALE) {
                    ProductionStationDeparturesResult(
                        departures = planned,
                        realtimeState = StationRealtimeState.STALE,
                        realtimeSourceLabel = overlay.provenance.source,
                        updatedAtEpochSeconds = overlay.updatedAtEpochSeconds(),
                    )
                } else {
                    fresh(stopId, planned, overlay)
                }
            }
            is GermanyRealtimeOverlayResult.NoMatch -> ProductionStationDeparturesResult(
                departures = planned,
                realtimeState = if (
                    overlay.freshness == GermanyRealtimeOverlayFreshnessState.FRESH
                ) {
                    StationRealtimeState.FRESH_NO_MATCH
                } else {
                    StationRealtimeState.STALE
                },
                realtimeSourceLabel = overlay.provenance.source,
                updatedAtEpochSeconds = overlay.updatedAtEpochSeconds(),
            )
            is GermanyRealtimeOverlayResult.Unavailable -> ProductionStationDeparturesResult(
                departures = planned,
                realtimeState = StationRealtimeState.UNAVAILABLE,
            )
        }
    }

    private fun fresh(
        stopId: String,
        planned: List<Departure>,
        overlay: GermanyRealtimeOverlayResult.Matched,
    ): ProductionStationDeparturesResult {
        val byTrip = overlay.tripOverlays.associateBy { it.staticTrip.id }
        var matchedAny = false
        val departures = planned.map { departure ->
            val trip = byTrip[departure.tripId] ?: return@map departure
            val update = trip.stopUpdates.firstOrNull { it.scheduledStopId == stopId }
            matchedAny = true
            departure.copy(
                realtimeEpochSeconds = update?.departureTimeEpochSeconds
                    ?: update?.departureDelaySeconds?.let {
                        departure.scheduledEpochSeconds + it
                    },
                cancelled = trip.cancelled,
                platform = update?.platformCode ?: departure.platform,
            )
        }

        val tripIds = planned.mapTo(linkedSetOf()) { it.tripId }
        val routeIds = planned.mapNotNullTo(linkedSetOf()) { it.routeId }
        val alerts = overlay.serviceAlerts
            .filter { alert ->
                alert.stopIds.contains(stopId) ||
                    alert.tripIds.any { it in tripIds } ||
                    alert.routeIds.any { it in routeIds }
            }
            .map { alert ->
                ServiceAlert(
                    id = alert.entityId,
                    title = alert.header
                        ?: alert.effect
                        ?: alert.cause
                        ?: "Servis duyurusu",
                    detail = alert.description
                        ?: alert.effect
                        ?: alert.cause
                        ?: "",
                )
            }

        return ProductionStationDeparturesResult(
            departures = departures,
            alerts = alerts,
            realtimeState = if (matchedAny || alerts.isNotEmpty()) {
                StationRealtimeState.FRESH_MATCHED
            } else {
                StationRealtimeState.FRESH_NO_MATCH
            },
            realtimeSourceLabel = overlay.provenance.source,
            updatedAtEpochSeconds = overlay.updatedAtEpochSeconds(),
        )
    }

    private fun GermanyRealtimeOverlayResult.Matched.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds

    private fun GermanyRealtimeOverlayResult.NoMatch.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds
}
