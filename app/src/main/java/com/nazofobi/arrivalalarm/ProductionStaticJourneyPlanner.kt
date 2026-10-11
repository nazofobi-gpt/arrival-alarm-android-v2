package com.nazofobi.arrivalalarm

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil

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
            walkingMinutes = ceil(
                (journey.access.walkSeconds + journey.egress.walkSeconds) / 60.0,
            ).toInt(),
            transfers = journey.transferCount,
            tripIds = journey.legs.map { it.tripId },
            stops = routeStops,
            refreshToken = null,
            sourceUpdatedAtEpochSeconds = sourceUpdatedAtEpochSeconds,
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
        return stopTimes.subList(fromIndex, toIndex + 1).map { stopTime ->
            val stop = data.stop(stopTime.stopId)
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
        var best: Triple<Int, Int, Long>? = null
        var bestDelta = Long.MAX_VALUE
        for (fromIndex in stopTimes.indices) {
            val from = stopTimes[fromIndex]
            if (from.stopId != leg.fromStopId) continue
            val scheduledDeparture = epoch(
                from.departureTime ?: from.arrivalTime,
                serviceDate,
            ) ?: continue
            for (toIndex in (fromIndex + 1) until stopTimes.size) {
                val to = stopTimes[toIndex]
                if (to.stopId != leg.toStopId) continue
                val scheduledArrival = epoch(
                    to.arrivalTime ?: to.departureTime,
                    serviceDate,
                ) ?: continue
                val scheduledDuration = scheduledArrival - scheduledDeparture
                val routedDuration = leg.arrivalEpochMillis - leg.departureEpochMillis
                val delta = abs(scheduledDuration - routedDuration)
                if (delta < bestDelta) {
                    bestDelta = delta
                    best = Triple(
                        fromIndex,
                        toIndex,
                        leg.departureEpochMillis - scheduledDeparture,
                    )
                }
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

    init {
        require(maxAccessStops in 1..8)
        require(walkingMetersPerSecond in 0.5..3.0)
    }

    fun plan(
        origin: MapPoint,
        destination: MapPoint,
        limit: Int = 3,
    ): LocalStaticJourneyOutcome {
        if (!index.isReady()) return LocalStaticJourneyOutcome.Unavailable
        val boundedLimit = limit.coerceIn(1, 6)
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
        listOf(
            serviceDate(epochMillis, dayOffset = -1),
            serviceDate(epochMillis),
        )

    private fun serviceDate(epochMillis: Long, dayOffset: Int = 0): GtfsServiceDate {
        val calendar = GregorianCalendar(timeZone).apply {
            timeInMillis = epochMillis
            add(Calendar.DAY_OF_MONTH, dayOffset)
        }
        return GtfsServiceDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }
}
