package com.nazofobi.arrivalalarm

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
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

/**
 * Frequency start/headway arithmetic must not wrap signed Int and revisit past times.
 * GTFS headway_secs is provider data, so compute the next eligible board on Long.
 */
internal fun firstFrequencyBoardAtOrAfter(
    startSeconds: Long,
    earliestSeconds: Long,
    headwaySeconds: Int,
): Long {
    require(headwaySeconds > 0)
    if (startSeconds >= earliestSeconds) return startSeconds
    val headway = headwaySeconds.toLong()
    val intervals = (earliestSeconds - startSeconds + headway - 1L) / headway
    return startSeconds + intervals * headway
}

/**
 * Nationwide feeds can reuse a trip ID at the same departure time on different
 * routes. Distinguish visible service identity before limiting board results.
 * Repeated updates to the same service still collapse to its first occurrence.
 */
internal fun distinctStationDepartures(departures: List<Departure>): List<Departure> =
    departures.distinctBy { departure ->
        listOf(
            departure.tripId,
            departure.scheduledEpochSeconds,
            departure.routeId,
            departure.line,
            departure.direction,
            departure.gtfsInstance,
            departure.boardingStopSequence,
        )
    }

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
            .let(::distinctStationDepartures)
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
                    serviceDate = serviceDate,
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
                val windowStart = frequency.startSeconds.toLong() + boardOffset
                val windowEnd = frequency.endSeconds.toLong() + boardOffset
                if (earliestBoardSeconds.toLong() >= windowEnd) continue

                var board = firstFrequencyBoardAtOrAfter(
                    windowStart, earliestBoardSeconds.toLong(), frequency.headwaySeconds,
                )
                while (
                    board < windowEnd &&
                    size < MAX_FREQUENCY_DEPARTURES_PER_CANDIDATE
                ) {
                    val scheduled = if (board <= Int.MAX_VALUE) {
                        serviceEpochSeconds(serviceDate, board.toInt())
                    } else null
                    if (scheduled != null && scheduled >= nowEpochSeconds) {
                        add(
                            departure(
                                candidate = candidate,
                                line = line,
                                direction = direction,
                                scheduledEpochSeconds = scheduled,
                                plannedPlatform = plannedPlatform,
                                realtimeTripStartDate = formatServiceDate(serviceDate),
                                realtimeTripStartTime = formatServiceTime((board - boardOffset).toInt()),
                            ),
                        )
                    }
                    board += frequency.headwaySeconds.toLong()
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
        serviceDate: GtfsServiceDate? = null,
        realtimeTripStartDate: String? = null,
        realtimeTripStartTime: String? = null,
    ): Departure {
        val evidence = stationDepartureEvidence(candidate.trip, candidate.stopSequence, serviceDate)
        return Departure(
        tripId = candidate.trip.id,
        line = line,
        direction = direction,
        scheduledEpochSeconds = scheduledEpochSeconds,
        platform = plannedPlatform,
        routeId = candidate.trip.routeId,
        realtimeTripStartDate = realtimeTripStartDate,
        realtimeTripStartTime = realtimeTripStartTime,
        gtfsInstance = evidence?.identity,
        boardingStopSequence = evidence?.boardingStopSequence,
        )
    }

    private fun parseServiceSeconds(raw: String): Int? =
        runCatching { GtfsServiceTime.parse(raw).secondsFromServiceDayStart }.getOrNull()

    private fun formatServiceDate(date: GtfsServiceDate): String =
        GtfsRealtimeServiceKeyFormat.date(date)

    private fun formatServiceTime(seconds: Int): String =
        GtfsRealtimeServiceKeyFormat.time(seconds)

    private fun serviceEpochSeconds(
        serviceDate: GtfsServiceDate,
        serviceSeconds: Int,
    ): Long? = runCatching {
        GtfsServiceTime.parse(
            GtfsRealtimeServiceKeyFormat.time(serviceSeconds),
        ).resolve(serviceDate, agencyTimeZone).epochMillis / 1_000L
    }.getOrNull()

    private fun serviceDates(epochMillis: Long): List<GtfsServiceDate> =
        listOf(
            serviceDate(epochMillis, dayOffset = -1),
            serviceDate(epochMillis),
            serviceDate(epochMillis, dayOffset = 1),
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
        // Tomorrow's GTFS service day has not started yet: search it from 00:00.
        if (delta < 0L) return 0
        val seconds = delta / 1_000L + if (delta % 1_000L == 0L) 0L else 1L
        return seconds.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private companion object {
        const val MAX_FREQUENCY_ROWS_PER_TRIP = 64
        const val MAX_FREQUENCY_DEPARTURES_PER_CANDIDATE = 64
    }
}

/**
 * GTFS / GTFS-RT service-day keys are protocol fields, not localized UI text.
 * Locale.ROOT keeps their decimal digits ASCII on phones using other numeral systems.
 */
internal object GtfsRealtimeServiceKeyFormat {
    fun date(date: GtfsServiceDate): String =
        String.format(Locale.ROOT, "%04d%02d%02d", date.year, date.month, date.day)

    fun time(seconds: Int): String =
        String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            seconds / 3_600,
            (seconds % 3_600) / 60,
            seconds % 60,
        )
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
        val overlay = try {
            // A cancelled lookup must never begin provider I/O or run matching.
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Station realtime lookup cancelled before fetch")
            }
            val response = fetch()
            // Cancellation may occur while the provider call is completing.
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Station realtime lookup cancelled after fetch")
            }
            match(response, nowEpochSeconds())
        } catch (interrupted: InterruptedException) {
            // Cancellation is not an upstream outage; preserve the interrupt signal.
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) {
            // Do not swallow fatal JVM Errors or expose provider exception text.
            GermanyRealtimeOverlayResult.Unavailable(
                GermanyRealtimeUnavailableReason.NETWORK,
                "realtime_station_overlay_error",
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
        val byTrip = overlay.tripOverlays.groupBy { it.staticTrip.id }
        var matchedAny = false
        val departures = planned.map { departure ->
            val trip = byTrip[departure.tripId]
                .orEmpty()
                .firstOrNull { candidate ->
                    overlayMatchesDeparture(candidate, departure)
                }
                ?: return@map departure
            // A loop trip can visit the same stop_id more than once with different
            // stop_sequence values. The board lacks that sequence, so choosing the
            // first matching update can display a false skipped stop or platform.
            // Keep only unambiguous stop-specific evidence; trip cancellation is global.
            val update = trip.stopUpdates.singleOrNull { it.scheduledStopId == stopId }
            matchedAny = matchedAny || trip.cancelled || update != null
            // SKIPPED is stop-specific; do not offer boarding here or project
            // a predicted departure. Other stops of this trip remain available.
            val skippedAtStop = update?.scheduleRelationship == "SKIPPED"
            departure.copy(
                realtimeEpochSeconds = if (skippedAtStop) null else {
                    // An invalid provider timestamp must not turn a 2026 board into
                    // a 1970/overflowed departure. Valid early-running delays remain usable.
                    val predicted = update?.departureTimeEpochSeconds
                        ?: update?.departureDelaySeconds?.let {
                            departure.scheduledEpochSeconds + it.toLong()
                        }
                    predicted?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
                },
                cancelled = trip.cancelled || skippedAtStop,
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
            // The board has no numeric direction ID; never broaden directed alerts.
            if (selector.directionId != null || tripSelector.directionId != null ||
                tripSelector.validity == GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE
            ) return false
            val startSeconds = tripSelector.startTime?.let(::serviceTimeSeconds)
            if (tripSelector.startTime != null && startSeconds == null) return false
            return planned.any { departure ->
                departure.tripId == tripId &&
                    (selector.routeId == null || departure.routeId == selector.routeId) &&
                    (tripSelector.routeId == null || departure.routeId == tripSelector.routeId) &&
                    (tripSelector.startDate == null ||
                        departure.realtimeTripStartDate == tripSelector.startDate) &&
                    (startSeconds == null ||
                        departure.realtimeTripStartTime?.let(::serviceTimeSeconds) == startSeconds)
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

    private fun overlayMatchesDeparture(
        overlay: GermanyRealtimeTripOverlay,
        departure: Departure,
    ): Boolean {
        // Static feeds may reuse trip IDs across different routes. Do not let
        // a matched overlay cancel or delay an unrelated station-board service.
        if (departure.routeId.isNullOrBlank() || overlay.staticTrip.routeId != departure.routeId) {
            return false
        }
        if (!frequencyTrip(departure.tripId)) return true

        val expectedDate = departure.realtimeTripStartDate ?: return false
        val expectedStart = departure.realtimeTripStartTime
            ?.let(::serviceTimeSeconds)
            ?: return false
        val actualDate = overlay.startDate ?: return false
        val actualStart = overlay.startTime
            ?.let(::serviceTimeSeconds)
            ?: return false

        return actualDate == expectedDate && actualStart == expectedStart
    }

    private fun serviceTimeSeconds(raw: String): Int? =
        runCatching { GtfsServiceTime.parse(raw).secondsFromServiceDayStart }.getOrNull()

    private fun frequencyTrip(tripId: String): Boolean =
        runCatching { isFrequencyTrip(tripId) }.getOrDefault(true)

    private fun GermanyRealtimeOverlayResult.Matched.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds

    private fun GermanyRealtimeOverlayResult.NoMatch.updatedAtEpochSeconds(): Long =
        provenance.feedTimestampEpochSeconds ?: provenance.fetchedAtEpochSeconds
}
