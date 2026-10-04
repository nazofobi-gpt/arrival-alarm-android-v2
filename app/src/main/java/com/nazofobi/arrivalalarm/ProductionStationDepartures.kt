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
                ).flatMap { candidate ->
                    departuresForCandidate(
                        candidate = candidate,
                        serviceDate = serviceDate,
                        earliestBoardSeconds = earliest,
                        nowEpochSeconds = now / 1_000L,
                        plannedPlatform = plannedPlatform,
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

    private data class ParsedFrequency(
        val startSeconds: Int,
        val endSeconds: Int,
        val headwaySeconds: Int,
    )

    private fun departuresForCandidate(
        candidate: GtfsScheduleTripCandidate,
        serviceDate: GtfsServiceDate,
        earliestBoardSeconds: Int,
        nowEpochSeconds: Long,
        plannedPlatform: String?,
    ): List<Departure> {
        val rawBoard = candidate.departureTime ?: candidate.arrivalTime ?: return emptyList()
        val boardSeconds = parseServiceSeconds(rawBoard) ?: return emptyList()
        val route = repository.route(candidate.trip.routeId)
        val line = route?.shortName
            ?.takeIf { it.isNotBlank() }
            ?: route?.longName?.takeIf { it.isNotBlank() }
            ?: candidate.trip.routeId
        val direction = candidate.trip.headsign
            ?.takeIf { it.isNotBlank() }
            ?: route?.longName?.takeIf { it.isNotBlank() }
            ?: ""

        val frequencies = repository.frequenciesForTrip(
            candidate.trip.id,
            MAX_FREQUENCY_ROWS_PER_TRIP,
        )
        if (frequencies.isEmpty()) {
            if (boardSeconds < earliestBoardSeconds) return emptyList()
            val scheduled = serviceEpochSeconds(serviceDate, boardSeconds) ?: return emptyList()
            if (scheduled < nowEpochSeconds) return emptyList()
            return listOf(
                departure(
                    candidate = candidate,
                    line = line,
                    direction = direction,
                    scheduledEpochSeconds = scheduled,
                    plannedPlatform = plannedPlatform,
                ),
            )
        }

        val firstStop = repository.stopTimesForTrip(candidate.trip.id, 1).firstOrNull()
            ?: return emptyList()
        val templateStart = parseServiceSeconds(
            firstStop.departureTime ?: firstStop.arrivalTime ?: return emptyList()
        ) ?: return emptyList()
        val boardOffset = boardSeconds - templateStart
        if (boardOffset < 0) return emptyList()

        val parsed = frequencies.map { row ->
            if (row.exactTimes != 1 || row.headwaySeconds <= 0) return emptyList()
            val start = parseServiceSeconds(row.startTime) ?: return emptyList()
            val end = parseServiceSeconds(row.endTime) ?: return emptyList()
            if (start >= end) return emptyList()
            ParsedFrequency(start, end, row.headwaySeconds)
        }.sortedBy { it.startSeconds }
        if (parsed.zipWithNext().any { (left, right) ->
                right.startSeconds < left.endSeconds
            }
        ) {
            return emptyList()
        }

        return buildList {
            for (frequency in parsed) {
                if (size >= MAX_FREQUENCY_DEPARTURES_PER_CANDIDATE) break
                val windowStart = frequency.startSeconds + boardOffset
                val windowEnd = frequency.endSeconds + boardOffset
                if (earliestBoardSeconds >= windowEnd) continue

                var board = windowStart
                if (board < earliestBoardSeconds) {
                    val delta = earliestBoardSeconds - board
                    board += (
                        (delta + frequency.headwaySeconds - 1) /
                            frequency.headwaySeconds
                        ) * frequency.headwaySeconds
                }
                while (
                    board < windowEnd &&
                    size < MAX_FREQUENCY_DEPARTURES_PER_CANDIDATE
                ) {
                    val scheduled = serviceEpochSeconds(serviceDate, board)
                    if (scheduled != null && scheduled >= nowEpochSeconds) {
                        add(
                            departure(
                                candidate = candidate,
                                line = line,
                                direction = direction,
                                scheduledEpochSeconds = scheduled,
                                plannedPlatform = plannedPlatform,
                            ),
                        )
                    }
                    board += frequency.headwaySeconds
                }
            }
        }
    }

    private fun departure(
        candidate: GtfsScheduleTripCandidate,
        line: String,
        direction: String,
        scheduledEpochSeconds: Long,
        plannedPlatform: String?,
    ) = Departure(
        tripId = candidate.trip.id,
        line = line,
        direction = direction,
        scheduledEpochSeconds = scheduledEpochSeconds,
        platform = plannedPlatform,
        routeId = candidate.trip.routeId,
    )

    private fun parseServiceSeconds(raw: String): Int? =
        runCatching { GtfsServiceTime.parse(raw).secondsFromServiceDayStart }.getOrNull()

    private fun serviceEpochSeconds(
        serviceDate: GtfsServiceDate,
        serviceSeconds: Int,
    ): Long? = runCatching {
        GtfsServiceTime.parse(
            "%02d:%02d:%02d".format(
                serviceSeconds / 3_600,
                (serviceSeconds % 3_600) / 60,
                serviceSeconds % 60,
            ),
        ).resolve(serviceDate, agencyTimeZone).epochMillis / 1_000L
    }.getOrNull()

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

    private companion object {
        const val MAX_FREQUENCY_ROWS_PER_TRIP = 64
        const val MAX_FREQUENCY_DEPARTURES_PER_CANDIDATE = 64
    }
}

internal class ProductionRealtimeStationOverlay(
    private val fetch: () -> GermanyRealtimeFetchResult,
    private val match: (GermanyRealtimeFetchResult, Long) -> GermanyRealtimeOverlayResult,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val isFrequencyTrip: (String) -> Boolean = { false },
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
        val byTrip = overlay.tripOverlays
            .filterNot { frequencyTrip(it.staticTrip.id) }
            .associateBy { it.staticTrip.id }
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

        val alerts = overlay.serviceAlerts
            .filter { alert ->
                alert.selectors.any { selector ->
                    selectorMatchesStationBoard(selector, stopId, planned)
                }
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

    private fun selectorMatchesStationBoard(
        selector: GermanyRealtimeAlertSelector,
        stopId: String,
        planned: List<Departure>,
    ): Boolean {
        if (selector.validity != GermanyRealtimeAlertSelectorValidity.VALID) return false
        if (selector.stopId != null && selector.stopId != stopId) return false

        val tripSelector = selector.trip
        if (tripSelector != null) {
            val tripId = tripSelector.tripId ?: return false
            return planned.any { departure ->
                departure.tripId == tripId &&
                    (selector.routeId == null || departure.routeId == selector.routeId)
            }
        }

        // A direction-scoped selector cannot be proven from the board projection alone.
        // Fail closed instead of broadening it to every departure on the route.
        if (selector.directionId != null) return false

        if (selector.routeId != null) {
            return planned.any { it.routeId == selector.routeId }
        }

        // Stop-only selectors are exact at this point. Agency/type-only (or mixed
        // stop+agency/type) selectors need static route metadata that the board
        // projection does not carry, so they deliberately fail closed.
        return selector.stopId == stopId &&
            selector.agencyId == null &&
            selector.routeType == null
    }

    private fun frequencyTrip(tripId: String): Boolean =
        runCatching { isFrequencyTrip(tripId) }.getOrDefault(true)

    private fun GermanyRealtimeOverlayResult.Matched.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds

    private fun GermanyRealtimeOverlayResult.NoMatch.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds
}
