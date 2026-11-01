package com.nazofobi.arrivalalarm

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil

/** Reject untrusted GPS/map coordinates before either local nearest-stop or live routing. */
internal fun validTransitJourneyCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

sealed interface LocalStaticJourneyOutcome {
    data class Results(val options: List<RouteOption>) : LocalStaticJourneyOutcome
    data object SameOrigin : LocalStaticJourneyOutcome
    data object NoPath : LocalStaticJourneyOutcome
    data object Unavailable : LocalStaticJourneyOutcome
}

internal interface StaticRouteOptionData {
    fun route(routeId: String): GtfsScheduleRoute?
    fun trip(tripId: String): GtfsScheduleTrip?
    fun stop(stopId: String): GtfsScheduleStop?
    fun stopTimesForTrip(tripId: String, limit: Int): List<GtfsScheduleStopTime>
}

private class RepositoryStaticRouteOptionData(
    private val repository: GtfsScheduleRepository,
) : StaticRouteOptionData {
    override fun route(routeId: String) = repository.route(routeId)
    override fun trip(tripId: String) = repository.trip(tripId)
    override fun stop(stopId: String) = repository.stop(stopId)
    override fun stopTimesForTrip(tripId: String, limit: Int) =
        repository.stopTimesForTrip(tripId, limit)
}

internal class StaticRouteOptionMapper(
    private val data: StaticRouteOptionData,
    private val agencyTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Berlin"),
    private val maxStopTimesPerTrip: Int = 2_048,
) {
    init {
        require(maxStopTimesPerTrip in 2..2_048)
    }

    /**
     * Add two non-negative GTFS walking durations without Int overflow.
     * Times are approximate provider walk estimates, never measured geometry.
     */
    private fun totalWalkingMinutes(accessSeconds: Int, egressSeconds: Int): Int {
        val sum = accessSeconds.toLong() + egressSeconds.toLong()
        return ((sum + 59L) / 60L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun map(
        journey: StaticTransitJourney,
        origin: MapPoint,
        destination: MapPoint,
        serviceDate: GtfsServiceDate,
        sourceUpdatedAtEpochSeconds: Long,
    ): RouteOption {
        val lineLabels = journey.legs.map { leg ->
            data.route(leg.routeId)?.let { route ->
                route.shortName ?: route.longName ?: route.id
            } ?: leg.routeId
        }
        val direction = journey.legs.lastOrNull()
            ?.let { data.trip(it.tripId)?.headsign }
            ?.takeIf { it.isNotBlank() }
            ?: destination.label
        val routeStops = journey.legs
            .flatMap { leg -> stopsForLeg(leg, serviceDate) }
            .fold(mutableListOf<RouteStop>()) { acc, next ->
                val previous = acc.lastOrNull()
                if (previous != null && previous.id != null && previous.id == next.id) {
                    acc[acc.lastIndex] = previous.copy(
                        name = next.name.ifBlank { previous.name },
                        latitude = next.latitude ?: previous.latitude,
                        longitude = next.longitude ?: previous.longitude,
                        arrival = previous.arrival ?: next.arrival,
                        plannedArrival = previous.plannedArrival ?: next.plannedArrival,
                        departure = next.departure ?: previous.departure,
                        plannedDeparture = next.plannedDeparture ?: previous.plannedDeparture,
                    )
                } else {
                    acc += next
                }
                acc
            }

        return RouteOption(
            id = journey.id,
            origin = origin,
            destination = destination,
            line = lineLabels.ifEmpty { listOf("GTFS") }.joinToString(" → "),
            direction = direction,
            departure = journey.legs.firstOrNull()?.departureEpochMillis?.let(::clock).orEmpty(),
            arrival = clock(journey.arrivalEpochMillis),
            walkingMinutes = totalWalkingMinutes(journey.access.walkSeconds, journey.egress.walkSeconds),
            transfers = journey.transferCount,
            tripIds = journey.legs.map { it.tripId },
            stops = routeStops,
            refreshToken = null,
            sourceUpdatedAtEpochSeconds = sourceUpdatedAtEpochSeconds,
            departureEpochMillis = journey.legs.firstOrNull()?.departureEpochMillis,
            arrivalEpochMillis = journey.arrivalEpochMillis,
            accessWalking = walkingLegEvidence(journey.access),
            egressWalking = walkingLegEvidence(journey.egress),
        )
    }

    private fun walkingLegEvidence(access: StaticRouterAccess): RouteWalkingLegEvidence? {
        if (access.walkSeconds <= 0) return null
        val name = data.stop(access.stopId)?.name?.takeIf { it.isNotBlank() } ?: access.stopId
        return RouteWalkingLegEvidence(
            stopId = access.stopId,
            stopName = name,
            walkSeconds = access.walkSeconds,
        )
    }

    private fun stopsForLeg(
        leg: StaticTransitLeg,
        serviceDate: GtfsServiceDate,
    ): List<RouteStop> {
        val stopTimes = data.stopTimesForTrip(leg.tripId, maxStopTimesPerTrip)
        val pair = bestStopRange(stopTimes, leg, serviceDate) ?: return listOfNotNull(
            endpoint(leg.fromStopId, departureMillis = leg.departureEpochMillis),
            endpoint(leg.toStopId, arrivalMillis = leg.arrivalEpochMillis),
        )
        val (fromIndex, toIndex, shiftMillis) = pair
        // A circular journey may revisit one stop hundreds of times. Reuse
        // stop metadata within this leg instead of repeating repository reads.
        // containsKey also caches absent stops (null), unlike getOrPut.
        val stopsById = HashMap<String, GtfsScheduleStop?>()
        return stopTimes.subList(fromIndex, toIndex + 1).map { stopTime ->
            val stop = if (stopsById.containsKey(stopTime.stopId)) {
                stopsById[stopTime.stopId]
            } else {
                data.stop(stopTime.stopId).also { stopsById[stopTime.stopId] = it }
            }
            RouteStop(
                id = stopTime.stopId,
                name = stop?.name ?: stopTime.stopId,
                latitude = stop?.latitude,
                longitude = stop?.longitude,
                plannedArrival = shiftedIso(stopTime.arrivalTime, serviceDate, shiftMillis),
                plannedDeparture = shiftedIso(stopTime.departureTime, serviceDate, shiftMillis),
                plannedPlatform = stop?.platformCode,
            )
        }
    }

    private fun bestStopRange(
        stopTimes: List<GtfsScheduleStopTime>,
        leg: StaticTransitLeg,
        serviceDate: GtfsServiceDate,
    ): Triple<Int, Int, Long>? {
        // Scan backwards so the lookup contains only destination stops after the
        // boarding index. A time-ordered map avoids the quadratic search on long
        // loop routes that revisit the same boarding and alighting stops.
        val laterArrivals = java.util.TreeMap<Long, Int>()
        val routedDuration = leg.arrivalEpochMillis - leg.departureEpochMillis
        var best: Triple<Int, Int, Long>? = null
        var bestDelta = Long.MAX_VALUE
        var bestFromIndex = Int.MAX_VALUE
        var bestToIndex = Int.MAX_VALUE

        for (index in stopTimes.indices.reversed()) {
            val stopTime = stopTimes[index]
            if (stopTime.stopId == leg.fromStopId) {
                val scheduledDeparture = epoch(
                    stopTime.departureTime ?: stopTime.arrivalTime,
                    serviceDate,
                )
                if (scheduledDeparture != null && laterArrivals.isNotEmpty()) {
                    val expectedArrival = scheduledDeparture + routedDuration
                    // The closest arrival must be the floor or ceiling of the
                    // expected time, regardless of the original GTFS row ordering.
                    val neighbors = listOfNotNull(
                        laterArrivals.floorEntry(expectedArrival),
                        laterArrivals.ceilingEntry(expectedArrival),
                    )
                    for (entry in neighbors) {
                        val toIndex = entry.value
                        val delta = abs(entry.key - expectedArrival)
                        // Preserve the old deterministic tie-break: earliest
                        // boarding row, followed by earliest alighting row.
                        if (
                            delta < bestDelta ||
                            (delta == bestDelta && (
                                index < bestFromIndex ||
                                (index == bestFromIndex && toIndex < bestToIndex)
                            ))
                        ) {
                            bestDelta = delta
                            bestFromIndex = index
                            bestToIndex = toIndex
                            best = Triple(
                                index,
                                toIndex,
                                leg.departureEpochMillis - scheduledDeparture,
                            )
                        }
                    }
                }
            }

            // Insert after evaluating this row: boarding and alighting at the
            // same sequence position is invalid, even when both stop IDs match.
            if (stopTime.stopId == leg.toStopId) {
                epoch(
                    stopTime.arrivalTime ?: stopTime.departureTime,
                    serviceDate,
                )?.let { arrival -> laterArrivals[arrival] = index }
            }
        }
        return best
    }

    private fun endpoint(
        stopId: String,
        arrivalMillis: Long? = null,
        departureMillis: Long? = null,
    ): RouteStop? {
        val stop = data.stop(stopId) ?: return null
        return RouteStop(
            id = stop.id,
            name = stop.name,
            latitude = stop.latitude,
            longitude = stop.longitude,
            plannedArrival = arrivalMillis?.let(::iso),
            plannedDeparture = departureMillis?.let(::iso),
            plannedPlatform = stop.platformCode,
        )
    }

    private fun shiftedIso(
        raw: String?,
        serviceDate: GtfsServiceDate,
        shiftMillis: Long,
    ): String? = epoch(raw, serviceDate)?.let { iso(it + shiftMillis) }

    private fun epoch(raw: String?, serviceDate: GtfsServiceDate): Long? =
        raw?.let {
            runCatching {
                GtfsServiceTime.parse(it).resolve(serviceDate, agencyTimeZone).epochMillis
            }.getOrNull()
        }

    private fun clock(epochMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(Date(epochMillis))

    private fun iso(epochMillis: Long): String {
        val compact = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(Date(epochMillis))
        if (compact.length < 5) return compact
        val offsetStart = compact.length - 5
        return compact.substring(0, offsetStart) +
            compact.substring(offsetStart, offsetStart + 3) + ":" +
            compact.substring(offsetStart + 3)
    }
}

/** Calendar-day candidates for service selection; use civil days, not fixed 24-hour offsets
 * (which would drift across Europe/Berlin daylight-saving transitions).
 * Yesterday retains GTFS 24+ hour trips; tomorrow captures departures after midnight.
 */
internal fun staticRoutingServiceDates(epochMillis: Long, timeZone: TimeZone): List<GtfsServiceDate> =
    (-1..1).map { offset ->
        val calendar = GregorianCalendar(timeZone).apply {
            timeInMillis = epochMillis
            add(Calendar.DAY_OF_MONTH, offset)
        }
        GtfsServiceDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

/** Only requested positive route-option limits may initiate timetable routing. */
internal fun boundedStaticJourneyOptionLimit(requested: Int): Int =
    requested.coerceIn(0, 6)

class ProductionStaticJourneyPlanner(
    private val index: NationwideTransitIndex,
    agencyTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Berlin"),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val maxAccessStops: Int = 4,
    private val walkingMetersPerSecond: Double = 1.25,
) {
    private val timeZone = agencyTimeZone
    private val repository = GtfsScheduleRepository(index)
    private val scheduleData = RepositoryStaticGtfsScheduleData(repository)
    private val router = StaticGtfsRouter(scheduleData, agencyTimeZone = agencyTimeZone)
    private val mapper = StaticRouteOptionMapper(
        RepositoryStaticRouteOptionData(repository),
        agencyTimeZone = agencyTimeZone,
    )
    private val selectedTripPlanner = SelectedTripItineraryPlanner(
        indexReady = index::isReady,
        resolveDetail = GtfsTripDetailResolver(repository)::resolve,
        routeLookup = repository::route,
        sourceUpdatedAtEpochSeconds = index::fetchedAt,
        agencyTimeZone = timeZone,
    )

    init {
        require(maxAccessStops in 1..8)
        require(walkingMetersPerSecond in 0.5..3.0)
    }

    /**
     * Plans only the rider-confirmed trip and ordered stop roles. Conflicts
     * return an explicit alternative action instead of falling back to search.
     */
    internal fun planSelectedTrip(
        selection: GtfsSelectedTrip,
        realtime: RouteRealtimeInfo? = null,
    ): SelectedTripPlanOutcome = selectedTripPlanner.plan(selection, realtime)

    fun plan(
        origin: MapPoint,
        destination: MapPoint,
        limit: Int = 3,
        maxWalkingMinutes: Int? = null,
    ): LocalStaticJourneyOutcome {
        if (!validTransitJourneyCoordinates(origin.latitude, origin.longitude) ||
            !validTransitJourneyCoordinates(destination.latitude, destination.longitude)
        ) return LocalStaticJourneyOutcome.NoPath
        val boundedLimit = boundedStaticJourneyOptionLimit(limit)
        if (boundedLimit == 0 || (maxWalkingMinutes != null && maxWalkingMinutes < 0)) {
            return LocalStaticJourneyOutcome.NoPath
        }
        val maxWalkingSeconds = maxWalkingMinutes
            ?.coerceAtMost(Int.MAX_VALUE / 60)?.times(60)
        if (!index.isReady()) return LocalStaticJourneyOutcome.Unavailable
        val originNearby = index.nearest(
            origin.latitude,
            origin.longitude,
            maxAccessStops,
        )
        val destinationNearby = index.nearest(
            destination.latitude,
            destination.longitude,
            maxAccessStops,
        )
        if (originNearby.isEmpty() || destinationNearby.isEmpty()) {
            return LocalStaticJourneyOutcome.NoPath
        }
        if (originNearby.first().stop.id == destinationNearby.first().stop.id) {
            return LocalStaticJourneyOutcome.SameOrigin
        }

        // Preserve all nearby stops, including shared candidates. Distinct origin and
        // destination primary stops have already passed the same-origin guard above;
        // dropping overlapping candidates here can discard the only viable boarding stop.
        val origins = originNearby.map { nearby ->
            StaticRouterAccess(
                stopId = nearby.stop.id,
                walkSeconds = walkSeconds(nearby.distanceMeters),
            )
        }
        val destinations = destinationNearby.map { nearby ->
            StaticRouterAccess(
                stopId = nearby.stop.id,
                walkSeconds = walkSeconds(nearby.distanceMeters),
            )
        }
        if (origins.isEmpty() || destinations.isEmpty()) return LocalStaticJourneyOutcome.NoPath

        val departureMillis = nowMillis()
        var sawSameOrigin = false
        val datedJourneys = buildList {
            serviceDates(departureMillis).forEach { serviceDate ->
                when (
                    val result = router.route(
                        origins = origins,
                        destinations = destinations,
                        serviceDate = serviceDate,
                        departureEpochMillis = departureMillis,
                        maxWalkingSeconds = maxWalkingSeconds,
                    )
                ) {
                    is StaticRouterResult.Journeys -> result.journeys.forEach { journey ->
                        add(serviceDate to journey)
                    }
                    StaticRouterResult.SameOrigin -> sawSameOrigin = true
                    StaticRouterResult.NoPath -> Unit
                }
            }
        }
        val options = datedJourneys
            .sortedWith(
                compareBy<Pair<GtfsServiceDate, StaticTransitJourney>> {
                    it.second.arrivalEpochMillis
                }.thenBy { it.second.transferCount }
                    .thenBy { it.second.id }
            )
            .distinctBy { it.second.id }
            .take(boundedLimit)
            .map { (serviceDate, journey) ->
                mapper.map(
                    journey = journey,
                    origin = origin,
                    destination = destination,
                    serviceDate = serviceDate,
                    sourceUpdatedAtEpochSeconds = index.fetchedAt(),
                )
            }
        return when {
            options.isNotEmpty() -> LocalStaticJourneyOutcome.Results(options)
            sawSameOrigin -> LocalStaticJourneyOutcome.SameOrigin
            else -> LocalStaticJourneyOutcome.NoPath
        }
    }

    private fun walkSeconds(distanceMeters: Int): Int =
        ceil(distanceMeters.coerceAtLeast(0) / walkingMetersPerSecond).toInt()

    private fun serviceDates(epochMillis: Long): List<GtfsServiceDate> =
        staticRoutingServiceDates(epochMillis, timeZone)
}
