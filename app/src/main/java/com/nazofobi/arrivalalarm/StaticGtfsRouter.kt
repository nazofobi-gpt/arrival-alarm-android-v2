package com.nazofobi.arrivalalarm

import java.util.TimeZone
import kotlin.math.max

data class StaticRouterAccess(
    val stopId: String,
    val walkSeconds: Int = 0,
) {
    init {
        require(stopId.isNotBlank())
        require(walkSeconds >= 0)
    }
}

data class StaticTransitLeg(
    val tripId: String,
    val routeId: String,
    val fromStopId: String,
    val toStopId: String,
    val departureEpochMillis: Long,
    val arrivalEpochMillis: Long,
)

data class StaticTransitJourney(
    val id: String,
    val access: StaticRouterAccess,
    val egress: StaticRouterAccess,
    val legs: List<StaticTransitLeg>,
    val arrivalEpochMillis: Long,
) {
    val transferCount: Int get() = (legs.size - 1).coerceAtLeast(0)
}

sealed interface StaticRouterResult {
    data class Journeys(val journeys: List<StaticTransitJourney>) : StaticRouterResult
    data object SameOrigin : StaticRouterResult
    data object NoPath : StaticRouterResult
}

interface StaticGtfsScheduleData {
    fun candidateTripsAtStop(stopId: String, limit: Int): List<GtfsScheduleTripCandidate>
    fun stopTimesForTrip(tripId: String, limit: Int): List<GtfsScheduleStopTime>
    fun transfersFromStop(stopId: String, limit: Int): List<GtfsScheduleTransfer>
    fun stop(stopId: String): GtfsScheduleStop?
    fun childStops(parentStationId: String, limit: Int): List<GtfsScheduleStop>
    fun isServiceActive(serviceId: String, serviceDate: GtfsServiceDate): Boolean
}

class RepositoryStaticGtfsScheduleData(
    private val repository: GtfsScheduleRepository,
) : StaticGtfsScheduleData {
    override fun candidateTripsAtStop(stopId: String, limit: Int) =
        repository.candidateTripsAtStop(stopId, limit)

    override fun stopTimesForTrip(tripId: String, limit: Int) =
        repository.stopTimesForTrip(tripId, limit)

    override fun transfersFromStop(stopId: String, limit: Int) =
        repository.transfersFromStop(stopId, limit)

    override fun stop(stopId: String) = repository.stop(stopId)

    override fun childStops(parentStationId: String, limit: Int) =
        repository.childStops(parentStationId, limit)

    override fun isServiceActive(serviceId: String, serviceDate: GtfsServiceDate): Boolean {
        val exception = repository.calendarException(serviceId, serviceDate.toString())
        if (exception != null) return exception.exceptionType == GtfsCalendarExceptionType.ADDED.value
        val c = repository.calendar(serviceId) ?: return false
        val start = c.startDate?.let(GtfsServiceDate::parse) ?: return false
        val end = c.endDate?.let(GtfsServiceDate::parse) ?: return false
        if (serviceDate < start || serviceDate > end) return false
        return when (serviceDate.weekday) {
            GtfsWeekday.MONDAY -> c.monday
            GtfsWeekday.TUESDAY -> c.tuesday
            GtfsWeekday.WEDNESDAY -> c.wednesday
            GtfsWeekday.THURSDAY -> c.thursday
            GtfsWeekday.FRIDAY -> c.friday
            GtfsWeekday.SATURDAY -> c.saturday
            GtfsWeekday.SUNDAY -> c.sunday
        }
    }
}

/**
 * Provider-independent bounded static GTFS router core.
 *
 * The caller supplies bounded access/egress stop candidates. This keeps geocoding and UI concerns
 * outside the core while still accounting for walking time. Search is intentionally capped at
 * direct and one-transfer journeys; every repository query is bounded.
 */
class StaticGtfsRouter(
    private val data: StaticGtfsScheduleData,
    private val agencyTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Berlin"),
    private val maxCandidatesPerStop: Int = 128,
    private val maxStopTimesPerTrip: Int = 512,
    private val maxTransfersPerStop: Int = 64,
    private val maxChildStops: Int = 64,
    private val implicitTransferSeconds: Int = 120,
    private val maxJourneys: Int = 6,
) {
    init {
        require(maxCandidatesPerStop in 1..512)
        require(maxStopTimesPerTrip in 2..2_048)
        require(maxTransfersPerStop in 1..512)
        require(maxChildStops in 1..256)
        require(implicitTransferSeconds >= 0)
        require(maxJourneys in 1..32)
    }

    fun route(
        origins: List<StaticRouterAccess>,
        destinations: List<StaticRouterAccess>,
        serviceDate: GtfsServiceDate,
        departureEpochMillis: Long,
    ): StaticRouterResult {
        val from = origins.distinctBy { it.stopId }.take(16)
        val to = destinations.distinctBy { it.stopId }.take(16)
        if (from.isEmpty() || to.isEmpty()) return StaticRouterResult.NoPath
        if (from.any { a -> to.any { b -> a.stopId == b.stopId } }) return StaticRouterResult.SameOrigin

        val destinationByStop = to.associateBy { it.stopId }
        val found = mutableListOf<StaticTransitJourney>()

        for (access in from) {
            val earliestBoard = departureEpochMillis + access.walkSeconds * 1_000L
            for (candidate in data.candidateTripsAtStop(access.stopId, maxCandidatesPerStop)) {
                if (!data.isServiceActive(candidate.trip.serviceId, serviceDate)) continue
                val first = legOnTrip(
                    candidate = candidate,
                    boardStopId = access.stopId,
                    earliestBoardEpochMillis = earliestBoard,
                    serviceDate = serviceDate,
                    destinationByStop = destinationByStop,
                )
                if (first != null) {
                    found += journey(access, first.second, listOf(first.first))
                    continue
                }

                val stopTimes = data.stopTimesForTrip(candidate.trip.id, maxStopTimesPerTrip)
                val boardIndex = stopTimes.indexOfFirst {
                    it.stopId == access.stopId && it.stopSequence == candidate.stopSequence
                }
                if (boardIndex < 0) continue
                val boardDeparture = epoch(stopTimes[boardIndex].departureTime ?: stopTimes[boardIndex].arrivalTime, serviceDate)
                    ?: continue
                if (boardDeparture < earliestBoard) continue

                for (alight in stopTimes.drop(boardIndex + 1)) {
                    val firstArrival = epoch(alight.arrivalTime ?: alight.departureTime, serviceDate) ?: continue
                    val rules = transferRulesFor(alight.stopId)
                    val targets = transferTargets(alight.stopId, rules)
                    for (targetStopId in targets) {
                        for (next in data.candidateTripsAtStop(targetStopId, maxCandidatesPerStop)) {
                            if (!data.isServiceActive(next.trip.serviceId, serviceDate)) continue
                            val applicable = rules.filter { rule ->
                                transferTargetsForRule(rule).contains(targetStopId) &&
                                    ruleMatches(rule, candidate.trip, next.trip)
                            }
                            val maxSpecificity = applicable.maxOfOrNull(::ruleSpecificity)
                            val governingRules = if (maxSpecificity == null) {
                                emptyList()
                            } else {
                                applicable.filter { ruleSpecificity(it) == maxSpecificity }
                            }
                            if (governingRules.size > 1) continue
                            val governing = governingRules.singleOrNull()
                            if (governing?.transferType == 3) continue
                            if (governing == null && targetStopId != alight.stopId) continue
                            val transferSeconds = if (governing == null) {
                                implicitTransferSeconds
                            } else {
                                max(0, governing.minTransferTimeSeconds ?: 0)
                            }
                            val transferReady = firstArrival + transferSeconds * 1_000L
                            val second = legOnTrip(
                                candidate = next,
                                boardStopId = targetStopId,
                                earliestBoardEpochMillis = transferReady,
                                serviceDate = serviceDate,
                                destinationByStop = destinationByStop,
                            ) ?: continue
                            val firstLeg = StaticTransitLeg(
                                tripId = candidate.trip.id,
                                routeId = candidate.trip.routeId,
                                fromStopId = access.stopId,
                                toStopId = alight.stopId,
                                departureEpochMillis = boardDeparture,
                                arrivalEpochMillis = firstArrival,
                            )
                            found += journey(access, second.second, listOf(firstLeg, second.first))
                        }
                    }
                }
            }
        }

        val deduped = found
            .sortedWith(compareBy<StaticTransitJourney> { it.arrivalEpochMillis }.thenBy { it.transferCount }.thenBy { it.id })
            .distinctBy { it.id }
            .take(maxJourneys)
        return if (deduped.isEmpty()) StaticRouterResult.NoPath else StaticRouterResult.Journeys(deduped)
    }

    private fun transferRulesFor(alightStopId: String): List<GtfsScheduleTransfer> {
        val stop = data.stop(alightStopId)
        val sources = listOfNotNull(alightStopId, stop?.parentStationId).distinct()
        return sources.flatMap { data.transfersFromStop(it, maxTransfersPerStop) }
    }

    private fun transferTargets(
        alightStopId: String,
        rules: List<GtfsScheduleTransfer>,
    ): List<String> = buildList {
        add(alightStopId)
        rules.forEach { rule -> addAll(transferTargetsForRule(rule)) }
    }.distinct().take(maxTransfersPerStop + maxChildStops + 1)

    private fun transferTargetsForRule(rule: GtfsScheduleTransfer): List<String> {
        val target = data.stop(rule.toStopId)
        val children = if (target?.locationType == 1) {
            data.childStops(rule.toStopId, maxChildStops).map { it.id }
        } else {
            emptyList()
        }
        return (listOf(rule.toStopId) + children).distinct()
    }

    private fun ruleMatches(
        rule: GtfsScheduleTransfer,
        fromTrip: GtfsScheduleTrip,
        toTrip: GtfsScheduleTrip,
    ): Boolean =
        (rule.fromTripId == null || rule.fromTripId == fromTrip.id) &&
            (rule.fromRouteId == null || rule.fromRouteId == fromTrip.routeId) &&
            (rule.toTripId == null || rule.toTripId == toTrip.id) &&
            (rule.toRouteId == null || rule.toRouteId == toTrip.routeId)

    private fun ruleSpecificity(rule: GtfsScheduleTransfer): Int {
        val trips = listOf(rule.fromTripId, rule.toTripId).count { it != null }
        val routes = listOf(rule.fromRouteId, rule.toRouteId).count { it != null }
        val hasCrossSideTripRoute =
            (rule.fromTripId != null && rule.toRouteId != null) ||
                (rule.fromRouteId != null && rule.toTripId != null)
        return when {
            trips == 2 -> 6
            hasCrossSideTripRoute -> 5
            trips >= 1 -> 4
            routes == 2 -> 3
            routes >= 1 -> 2
            else -> 1
        }
    }

    private fun legOnTrip(
        candidate: GtfsScheduleTripCandidate,
        boardStopId: String,
        earliestBoardEpochMillis: Long,
        serviceDate: GtfsServiceDate,
        destinationByStop: Map<String, StaticRouterAccess>,
    ): Pair<StaticTransitLeg, StaticRouterAccess>? {
        val times = data.stopTimesForTrip(candidate.trip.id, maxStopTimesPerTrip)
        val boardIndex = times.indexOfFirst {
            it.stopId == boardStopId && it.stopSequence == candidate.stopSequence
        }
        if (boardIndex < 0) return null
        val departure = epoch(times[boardIndex].departureTime ?: times[boardIndex].arrivalTime, serviceDate) ?: return null
        if (departure < earliestBoardEpochMillis) return null
        for (arrival in times.drop(boardIndex + 1)) {
            val egress = destinationByStop[arrival.stopId] ?: continue
            val arrivalEpoch = epoch(arrival.arrivalTime ?: arrival.departureTime, serviceDate) ?: continue
            return StaticTransitLeg(
                tripId = candidate.trip.id,
                routeId = candidate.trip.routeId,
                fromStopId = boardStopId,
                toStopId = arrival.stopId,
                departureEpochMillis = departure,
                arrivalEpochMillis = arrivalEpoch,
            ) to egress
        }
        return null
    }

    private fun journey(
        access: StaticRouterAccess,
        egress: StaticRouterAccess,
        legs: List<StaticTransitLeg>,
    ): StaticTransitJourney {
        val arrival = legs.last().arrivalEpochMillis + egress.walkSeconds * 1_000L
        val id = buildString {
            append(access.stopId).append('>')
            legs.forEach { append(it.tripId).append(':').append(it.fromStopId).append('-').append(it.toStopId).append('>') }
            append(egress.stopId)
        }
        return StaticTransitJourney(id, access, egress, legs, arrival)
    }

    private fun epoch(raw: String?, serviceDate: GtfsServiceDate): Long? =
        raw?.let { runCatching { GtfsServiceTime.parse(it).resolve(serviceDate, agencyTimeZone).epochMillis }.getOrNull() }
}
