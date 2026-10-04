package com.nazofobi.arrivalalarm

import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

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
    val frequencyBased: Boolean = false,
    val approximate: Boolean = false,
    val frequencyInstanceStartSeconds: Int? = null,
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
    fun candidateTripsAtStop(
        stopId: String,
        serviceDate: GtfsServiceDate,
        earliestBoardSeconds: Int,
        limit: Int,
    ): List<GtfsScheduleTripCandidate>
    fun stopTimesForTrip(tripId: String, limit: Int): List<GtfsScheduleStopTime>
    fun frequenciesForTrip(tripId: String, limit: Int): List<GtfsScheduleFrequency>
    fun transfersFromStop(stopId: String, limit: Int): List<GtfsScheduleTransfer>
    fun linkedTransfersFromTrip(fromTripId: String, limit: Int): List<GtfsScheduleTransfer>
    fun tripsInBlock(blockId: String, serviceId: String, limit: Int): List<GtfsScheduleTrip>
    fun trip(tripId: String): GtfsScheduleTrip?
    fun stop(stopId: String): GtfsScheduleStop?
    fun childStops(parentStationId: String, limit: Int): List<GtfsScheduleStop>
    fun nearbyStops(stopId: String, radiusMeters: Int, limit: Int): List<GtfsScheduleNearbyStop>
    fun isServiceActive(serviceId: String, serviceDate: GtfsServiceDate): Boolean
}

class RepositoryStaticGtfsScheduleData(
    private val repository: GtfsScheduleRepository,
) : StaticGtfsScheduleData {
    override fun candidateTripsAtStop(
        stopId: String,
        serviceDate: GtfsServiceDate,
        earliestBoardSeconds: Int,
        limit: Int,
    ) = repository.candidateTripsAtStop(stopId, serviceDate, earliestBoardSeconds, limit)

    override fun stopTimesForTrip(tripId: String, limit: Int) =
        repository.stopTimesForTrip(tripId, limit)

    override fun frequenciesForTrip(tripId: String, limit: Int) =
        repository.frequenciesForTrip(tripId, limit)

    override fun transfersFromStop(stopId: String, limit: Int) =
        repository.transfersFromStop(stopId, limit)

    override fun linkedTransfersFromTrip(fromTripId: String, limit: Int) =
        repository.linkedTransfersFromTrip(fromTripId, limit)

    override fun tripsInBlock(blockId: String, serviceId: String, limit: Int) =
        repository.tripsInBlock(blockId, serviceId, limit)

    override fun trip(tripId: String) = repository.trip(tripId)

    override fun stop(stopId: String) = repository.stop(stopId)

    override fun childStops(parentStationId: String, limit: Int) =
        repository.childStops(parentStationId, limit)

    override fun nearbyStops(stopId: String, radiusMeters: Int, limit: Int) =
        repository.nearbyStops(stopId, radiusMeters, limit)

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
    private val maxImplicitTransferRadiusMeters: Int = 350,
    private val maxImplicitTransferCandidates: Int = 16,
    private val implicitWalkingMetersPerSecond: Double = 1.25,
    private val maxFrequencyRowsPerTrip: Int = 64,
    private val maxFrequencyInstancesPerCandidate: Int = 128,
) {
    init {
        require(maxCandidatesPerStop in 1..512)
        require(maxStopTimesPerTrip in 2..2_048)
        require(maxTransfersPerStop in 1..512)
        require(maxChildStops in 1..256)
        require(implicitTransferSeconds >= 0)
        require(maxJourneys in 1..32)
        require(maxImplicitTransferRadiusMeters in 1..2_000)
        require(maxImplicitTransferCandidates in 1..64)
        require(implicitWalkingMetersPerSecond in 0.5..3.0)
        require(maxFrequencyRowsPerTrip in 1..512)
        require(maxFrequencyInstancesPerCandidate in 1..512)
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
            val earliestBoardSeconds = serviceSeconds(earliestBoard, serviceDate) ?: continue
            for (
                instance in candidateInstancesAtStop(
                    stopId = access.stopId,
                    serviceDate = serviceDate,
                    earliestBoardSeconds = earliestBoardSeconds,
                )
            ) {
                val candidate = instance.candidate
                if (!data.isServiceActive(candidate.trip.serviceId, serviceDate)) continue
                val first = legOnTrip(
                    instance = instance,
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
                val boardStopTime = stopTimes[boardIndex]
                if (!allowsScheduledPickup(boardStopTime)) continue
                val boardDeparture = shiftedEpoch(
                    boardStopTime.departureTime ?: boardStopTime.arrivalTime,
                    serviceDate,
                    instance.shiftSeconds,
                ) ?: continue
                if (boardDeparture < earliestBoard) continue

                for (alight in stopTimes.drop(boardIndex + 1)) {
                    val firstArrival = shiftedEpoch(
                        alight.arrivalTime ?: alight.departureTime,
                        serviceDate,
                        instance.shiftSeconds,
                    ) ?: continue
                    val firstLeg = StaticTransitLeg(
                        tripId = candidate.trip.id,
                        routeId = candidate.trip.routeId,
                        fromStopId = access.stopId,
                        toStopId = alight.stopId,
                        departureEpochMillis = boardDeparture,
                        arrivalEpochMillis = firstArrival,
                        frequencyBased = instance.frequencyBased,
                        approximate = instance.approximate,
                        frequencyInstanceStartSeconds = instance.frequencyInstanceStartSeconds,
                    )
                    linkedContinuationLegs(
                        fromTrip = candidate.trip,
                        alight = alight,
                        firstArrivalEpochMillis = firstArrival,
                        serviceDate = serviceDate,
                        destinationByStop = destinationByStop,
                    ).forEach { second ->
                        found += journey(access, second.second, listOf(firstLeg, second.first))
                    }

                    if (!allowsScheduledDropOff(alight)) continue
                    val rules = transferRulesFor(alight.stopId)
                    val targets = transferTargets(alight.stopId, rules)
                    for (target in targets) {
                        val targetStopId = target.stopId
                        val earliestTransferReady = firstArrival + target.walkSeconds * 1_000L
                        val earliestTransferSeconds = serviceSeconds(earliestTransferReady, serviceDate) ?: continue
                        for (
                            nextInstance in candidateInstancesAtStop(
                                stopId = targetStopId,
                                serviceDate = serviceDate,
                                earliestBoardSeconds = earliestTransferSeconds,
                            )
                        ) {
                            val next = nextInstance.candidate
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
                            val transferSeconds = if (governing?.transferType == 2) {
                                max(target.walkSeconds, max(0, governing.minTransferTimeSeconds ?: 0))
                            } else {
                                target.walkSeconds
                            }
                            val transferReady = firstArrival + transferSeconds * 1_000L
                            val second = legOnTrip(
                                instance = nextInstance,
                                boardStopId = targetStopId,
                                earliestBoardEpochMillis = transferReady,
                                serviceDate = serviceDate,
                                destinationByStop = destinationByStop,
                            ) ?: continue
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

    private data class TripInstance(
        val candidate: GtfsScheduleTripCandidate,
        val shiftSeconds: Int = 0,
        val frequencyBased: Boolean = false,
        val approximate: Boolean = false,
        val frequencyInstanceStartSeconds: Int? = null,
        val boardServiceSeconds: Int,
    )

    private data class ParsedFrequency(
        val row: GtfsScheduleFrequency,
        val startSeconds: Int,
        val endSeconds: Int,
        val approximate: Boolean,
    )

    private fun candidateInstancesAtStop(
        stopId: String,
        serviceDate: GtfsServiceDate,
        earliestBoardSeconds: Int,
    ): List<TripInstance> = data.candidateTripsAtStop(
        stopId,
        serviceDate,
        earliestBoardSeconds,
        maxCandidatesPerStop,
    ).asSequence()
        .flatMap { candidate -> expandFrequencyCandidate(candidate, earliestBoardSeconds).asSequence() }
        .sortedWith(
            compareBy<TripInstance> { it.boardServiceSeconds }
                .thenBy { it.candidate.trip.id }
                .thenBy { it.candidate.stopSequence }
        )
        .take(maxCandidatesPerStop)
        .toList()

    private fun expandFrequencyCandidate(
        candidate: GtfsScheduleTripCandidate,
        earliestBoardSeconds: Int,
    ): List<TripInstance> {
        val rawBoard = candidate.departureTime ?: candidate.arrivalTime ?: return emptyList()
        val templateBoardSeconds = parseServiceSeconds(rawBoard) ?: return emptyList()
        val rows = data.frequenciesForTrip(candidate.trip.id, maxFrequencyRowsPerTrip)
        if (rows.isEmpty()) {
            return if (templateBoardSeconds >= earliestBoardSeconds) {
                listOf(TripInstance(candidate = candidate, boardServiceSeconds = templateBoardSeconds))
            } else {
                emptyList()
            }
        }

        val firstStop = data.stopTimesForTrip(candidate.trip.id, maxStopTimesPerTrip).firstOrNull()
            ?: return emptyList()
        val templateStartSeconds = parseServiceSeconds(
            firstStop.departureTime ?: firstStop.arrivalTime ?: return emptyList()
        ) ?: return emptyList()
        val boardOffsetSeconds = templateBoardSeconds - templateStartSeconds
        if (boardOffsetSeconds < 0) return emptyList()

        val parsed = rows.map { row ->
            val start = parseServiceSeconds(row.startTime) ?: return emptyList()
            val end = parseServiceSeconds(row.endTime) ?: return emptyList()
            val exact = row.exactTimes ?: 0
            if (row.headwaySeconds <= 0 || start >= end || exact !in 0..1) return emptyList()
            ParsedFrequency(row, start, end, approximate = exact == 0)
        }.sortedWith(
            compareBy<ParsedFrequency> { it.startSeconds }
                .thenBy { it.endSeconds }
                .thenBy { it.row.headwaySeconds }
                .thenBy { it.approximate }
        )
        if (parsed.zipWithNext().any { (left, right) -> right.startSeconds < left.endSeconds }) {
            return emptyList()
        }

        val instances = mutableListOf<TripInstance>()
        for (frequency in parsed) {
            if (instances.size >= maxFrequencyInstancesPerCandidate) break
            val windowStart = frequency.startSeconds + boardOffsetSeconds
            val windowEnd = frequency.endSeconds + boardOffsetSeconds
            if (earliestBoardSeconds >= windowEnd) continue

            if (frequency.approximate) {
                val lowerBound = max(earliestBoardSeconds, windowStart)
                val estimated = (lowerBound + frequency.row.headwaySeconds / 2)
                    .coerceAtMost(windowEnd - 1)
                instances += TripInstance(
                    candidate = candidate,
                    shiftSeconds = estimated - templateBoardSeconds,
                    frequencyBased = true,
                    approximate = true,
                    frequencyInstanceStartSeconds = estimated - boardOffsetSeconds,
                    boardServiceSeconds = estimated,
                )
                continue
            }

            var departure = windowStart
            if (departure < earliestBoardSeconds) {
                val delta = earliestBoardSeconds - departure
                departure += ((delta + frequency.row.headwaySeconds - 1) / frequency.row.headwaySeconds) *
                    frequency.row.headwaySeconds
            }
            while (departure < windowEnd && instances.size < maxFrequencyInstancesPerCandidate) {
                instances += TripInstance(
                    candidate = candidate,
                    shiftSeconds = departure - templateBoardSeconds,
                    frequencyBased = true,
                    approximate = false,
                    frequencyInstanceStartSeconds = departure - boardOffsetSeconds,
                    boardServiceSeconds = departure,
                )
                departure += frequency.row.headwaySeconds
            }
        }
        return instances
    }

    private fun parseServiceSeconds(raw: String): Int? =
        runCatching { GtfsServiceTime.parse(raw).secondsFromServiceDayStart }.getOrNull()

    private fun linkedContinuationLegs(
        fromTrip: GtfsScheduleTrip,
        alight: GtfsScheduleStopTime,
        firstArrivalEpochMillis: Long,
        serviceDate: GtfsServiceDate,
        destinationByStop: Map<String, StaticRouterAccess>,
    ): List<Pair<StaticTransitLeg, StaticRouterAccess>> {
        val explicit = data.linkedTransfersFromTrip(fromTrip.id, maxTransfersPerStop)
            .filter { rule ->
                rule.transferType == 4 || rule.transferType == 5
            }
            .filter { rule ->
                (rule.fromTripId == null || rule.fromTripId == fromTrip.id) &&
                    (rule.fromRouteId == null || rule.fromRouteId == fromTrip.routeId)
            }

        if (explicit.isNotEmpty()) {
            return explicit.asSequence()
                .filter { it.transferType == 4 }
                .filter { it.fromStopId == null || it.fromStopId == alight.stopId }
                .mapNotNull { rule ->
                    val toTripId = rule.toTripId ?: return@mapNotNull null
                    val toTrip = data.trip(toTripId) ?: return@mapNotNull null
                    if (!ruleMatches(rule, fromTrip, toTrip)) return@mapNotNull null
                    if (!data.isServiceActive(toTrip.serviceId, serviceDate)) return@mapNotNull null
                    continuationLegOnTrip(
                        trip = toTrip,
                        boardStopId = rule.toStopId ?: alight.stopId,
                        earliestBoardEpochMillis = firstArrivalEpochMillis,
                        serviceDate = serviceDate,
                        destinationByStop = destinationByStop,
                    )
                }
                .distinctBy { result ->
                    val leg = result.first
                    listOf(leg.tripId, leg.fromStopId, leg.toStopId).joinToString("|")
                }
                .toList()
        }

        val blockId = fromTrip.blockId ?: return emptyList()
        return data.tripsInBlock(blockId, fromTrip.serviceId, maxCandidatesPerStop)
            .asSequence()
            .filter { it.id != fromTrip.id && it.blockId == blockId && it.serviceId == fromTrip.serviceId }
            .filter { data.isServiceActive(it.serviceId, serviceDate) }
            .mapNotNull { trip ->
                continuationLegOnTrip(
                    trip = trip,
                    boardStopId = alight.stopId,
                    earliestBoardEpochMillis = firstArrivalEpochMillis,
                    serviceDate = serviceDate,
                    destinationByStop = destinationByStop,
                )
            }
            .distinctBy { result ->
                val leg = result.first
                listOf(leg.tripId, leg.fromStopId, leg.toStopId).joinToString("|")
            }
            .toList()
    }

    private fun continuationLegOnTrip(
        trip: GtfsScheduleTrip,
        boardStopId: String,
        earliestBoardEpochMillis: Long,
        serviceDate: GtfsServiceDate,
        destinationByStop: Map<String, StaticRouterAccess>,
    ): Pair<StaticTransitLeg, StaticRouterAccess>? {
        val times = data.stopTimesForTrip(trip.id, maxStopTimesPerTrip)
        val board = times.firstOrNull() ?: return null
        if (board.stopId != boardStopId) return null
        val departure = epoch(board.departureTime ?: board.arrivalTime, serviceDate) ?: return null
        if (departure < earliestBoardEpochMillis) return null
        if (departure - earliestBoardEpochMillis > MAX_CONTINUATION_WAIT_MILLIS) return null
        for (arrival in times.drop(1)) {
            if (!allowsScheduledDropOff(arrival)) continue
            val egress = destinationByStop[arrival.stopId] ?: continue
            val arrivalEpoch = shiftedEpoch(
                arrival.arrivalTime ?: arrival.departureTime,
                serviceDate,
                instance.shiftSeconds,
            ) ?: continue
            if (arrivalEpoch < departure) continue
            return StaticTransitLeg(
                tripId = trip.id,
                routeId = trip.routeId,
                fromStopId = boardStopId,
                toStopId = arrival.stopId,
                departureEpochMillis = departure,
                arrivalEpochMillis = arrivalEpoch,
                frequencyBased = instance.frequencyBased,
                approximate = instance.approximate,
                frequencyInstanceStartSeconds = instance.frequencyInstanceStartSeconds,
            ) to egress
        }
        return null
    }

    private fun transferRulesFor(alightStopId: String): List<GtfsScheduleTransfer> {
        val stop = data.stop(alightStopId)
        val sources = listOfNotNull(alightStopId, stop?.parentStationId).distinct()
        return sources.flatMap { data.transfersFromStop(it, maxTransfersPerStop) }
    }

    private data class TransferTarget(
        val stopId: String,
        val walkSeconds: Int,
    )

    private fun transferTargets(
        alightStopId: String,
        rules: List<GtfsScheduleTransfer>,
    ): List<TransferTarget> {
        val source = data.stop(alightStopId)
        val targets = linkedMapOf<String, TransferTarget>()

        fun offer(stop: GtfsScheduleStop?, stopId: String, knownDistanceMeters: Int? = null) {
            val walkSeconds = if (stopId == alightStopId) {
                implicitTransferSeconds
            } else {
                implicitTransferWalkSeconds(source, stop, knownDistanceMeters)
            }
            val previous = targets[stopId]
            if (previous == null || walkSeconds < previous.walkSeconds) {
                targets[stopId] = TransferTarget(stopId, walkSeconds)
            }
        }

        offer(source, alightStopId, 0)

        rules.forEach { rule ->
            transferTargetsForRule(rule).forEach { targetStopId ->
                offer(data.stop(targetStopId), targetStopId)
            }
        }

        val siblingParentId = when {
            source?.parentStationId != null -> source.parentStationId
            source?.locationType == 1 -> source.id
            else -> null
        }
        if (siblingParentId != null) {
            data.childStops(siblingParentId, maxChildStops).forEach { sibling ->
                if (sibling.id != alightStopId) offer(sibling, sibling.id)
            }
        }

        data.nearbyStops(
            stopId = alightStopId,
            radiusMeters = maxImplicitTransferRadiusMeters,
            limit = maxImplicitTransferCandidates,
        ).take(maxImplicitTransferCandidates).forEach { nearby ->
            offer(nearby.stop, nearby.stop.id, nearby.distanceMeters)
        }

        return targets.values.toList()
    }

    private fun implicitTransferWalkSeconds(
        from: GtfsScheduleStop?,
        to: GtfsScheduleStop?,
        knownDistanceMeters: Int? = null,
    ): Int {
        if (from == null || to == null) return max(implicitTransferSeconds, 1)
        val distanceMeters = knownDistanceMeters ?: transferDistanceMeters(from, to)
        val walkingSeconds = ceil(distanceMeters.coerceAtLeast(0) / implicitWalkingMetersPerSecond)
            .toInt()
            .coerceAtLeast(if (from.id == to.id) 0 else 1)
        return max(implicitTransferSeconds, walkingSeconds)
    }

    private fun transferDistanceMeters(from: GtfsScheduleStop, to: GtfsScheduleStop): Int {
        val meanLatitudeRadians = Math.toRadians((from.latitude + to.latitude) / 2.0)
        val latitudeMeters = (to.latitude - from.latitude) * 111_320.0
        val longitudeMeters = (to.longitude - from.longitude) * 111_320.0 * cos(meanLatitudeRadians)
        return ceil(sqrt(latitudeMeters * latitudeMeters + longitudeMeters * longitudeMeters)).toInt()
    }

    private fun transferTargetsForRule(rule: GtfsScheduleTransfer): List<String> {
        // Linked-trip type 4/5 rows may intentionally omit stop IDs. This router node keeps
        // current stop-based semantics only; linked-trip routing is implemented by the successor.
        val toStopId = rule.toStopId ?: return emptyList()
        val target = data.stop(toStopId)
        val children = if (target?.locationType == 1) {
            data.childStops(toStopId, maxChildStops).map { it.id }
        } else {
            emptyList()
        }
        return (listOf(toStopId) + children).distinct()
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
        instance: TripInstance,
        boardStopId: String,
        earliestBoardEpochMillis: Long,
        serviceDate: GtfsServiceDate,
        destinationByStop: Map<String, StaticRouterAccess>,
    ): Pair<StaticTransitLeg, StaticRouterAccess>? {
        val candidate = instance.candidate
        val times = data.stopTimesForTrip(candidate.trip.id, maxStopTimesPerTrip)
        val boardIndex = times.indexOfFirst {
            it.stopId == boardStopId && it.stopSequence == candidate.stopSequence
        }
        if (boardIndex < 0) return null
        val boardStopTime = times[boardIndex]
        if (!allowsScheduledPickup(boardStopTime)) return null
        val departure = shiftedEpoch(
            boardStopTime.departureTime ?: boardStopTime.arrivalTime,
            serviceDate,
            instance.shiftSeconds,
        ) ?: return null
        if (departure < earliestBoardEpochMillis) return null
        for (arrival in times.drop(boardIndex + 1)) {
            if (!allowsScheduledDropOff(arrival)) continue
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

    private fun allowsScheduledPickup(stopTime: GtfsScheduleStopTime): Boolean =
        stopTime.pickupType == null || stopTime.pickupType == 0

    private fun allowsScheduledDropOff(stopTime: GtfsScheduleStopTime): Boolean =
        stopTime.dropOffType == null || stopTime.dropOffType == 0

    private fun journey(
        access: StaticRouterAccess,
        egress: StaticRouterAccess,
        legs: List<StaticTransitLeg>,
    ): StaticTransitJourney {
        val arrival = legs.last().arrivalEpochMillis + egress.walkSeconds * 1_000L
        val id = buildString {
            append(access.stopId).append('>')
            legs.forEach {
                append(it.tripId).append('@').append(it.departureEpochMillis).append(':')
                    .append(it.fromStopId).append('-').append(it.toStopId).append('>')
            }
            append(egress.stopId)
        }
        return StaticTransitJourney(id, access, egress, legs, arrival)
    }

    private fun serviceSeconds(epochMillis: Long, serviceDate: GtfsServiceDate): Int? {
        val anchor = GtfsServiceTime.parse("00:00:00").resolve(serviceDate, agencyTimeZone).epochMillis
        val deltaMillis = epochMillis - anchor
        if (deltaMillis < 0L) return null
        val seconds = deltaMillis / 1_000L + if (deltaMillis % 1_000L == 0L) 0L else 1L
        return seconds.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun epoch(raw: String?, serviceDate: GtfsServiceDate): Long? =
        raw?.let { runCatching { GtfsServiceTime.parse(it).resolve(serviceDate, agencyTimeZone).epochMillis }.getOrNull() }

    private fun shiftedEpoch(raw: String?, serviceDate: GtfsServiceDate, shiftSeconds: Int): Long? =
        epoch(raw, serviceDate)?.plus(shiftSeconds * 1_000L)

    private companion object {
        const val MAX_CONTINUATION_WAIT_MILLIS = 6L * 60L * 60L * 1_000L
    }
}
