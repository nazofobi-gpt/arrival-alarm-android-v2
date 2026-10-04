package com.nazofobi.arrivalalarm

enum class GermanyRealtimeOverlayFreshnessState {
    FRESH,
    STALE,
}

data class GermanyRealtimeOverlayProvenance(
    val source: String,
    val license: String,
    val gtfsRealtimeVersion: String?,
    val feedVersion: String?,
    val feedTimestampEpochSeconds: Long?,
    val fetchedAtEpochSeconds: Long,
)

data class GermanyRealtimeMatchedStopUpdate(
    val stopSequence: Int,
    val scheduledStopId: String,
    val realtimeStopId: String?,
    val assignedStopId: String?,
    val platformCode: String?,
    val arrivalDelaySeconds: Int?,
    val departureDelaySeconds: Int?,
    val arrivalTimeEpochSeconds: Long?,
    val departureTimeEpochSeconds: Long?,
    val scheduleRelationship: String?,
    val pickupType: String?,
    val dropOffType: String?,
    val stopHeadsign: String?,
)

data class GermanyRealtimeTripOverlay(
    val staticTrip: GtfsScheduleTrip,
    val startDate: String?,
    val startTime: String?,
    val cancelled: Boolean,
    val scheduleRelationship: String?,
    val stopUpdates: List<GermanyRealtimeMatchedStopUpdate>,
    val sourceTimestampEpochSeconds: Long?,
)

sealed interface GermanyRealtimeOverlayResult {
    data class Matched(
        val tripOverlays: List<GermanyRealtimeTripOverlay>,
        val serviceAlerts: List<GermanyRealtimeAlert>,
        val freshness: GermanyRealtimeOverlayFreshnessState,
        val ageSeconds: Long,
        val provenance: GermanyRealtimeOverlayProvenance,
    ) : GermanyRealtimeOverlayResult

    data class NoMatch(
        val freshness: GermanyRealtimeOverlayFreshnessState,
        val ageSeconds: Long,
        val provenance: GermanyRealtimeOverlayProvenance,
    ) : GermanyRealtimeOverlayResult

    data class Unavailable(
        val reason: GermanyRealtimeUnavailableReason,
        val detail: String?,
    ) : GermanyRealtimeOverlayResult
}

interface GermanyRealtimeStaticData {
    fun trip(tripId: String): GtfsScheduleTrip?
    fun route(routeId: String): GtfsScheduleRoute?
    fun stop(stopId: String): GtfsScheduleStop?
    fun stopTimesForTrip(tripId: String, limit: Int): List<GtfsScheduleStopTime>
    fun frequenciesForTrip(tripId: String, limit: Int): List<GtfsScheduleFrequency>
    fun tripsForRouteDirection(
        routeId: String,
        directionId: Int,
        limit: Int,
    ): List<GtfsScheduleTrip>
    fun isServiceActive(serviceId: String, serviceDate: GtfsServiceDate): Boolean
}

class RepositoryGermanyRealtimeStaticData(
    private val repository: GtfsScheduleRepository,
) : GermanyRealtimeStaticData {
    override fun trip(tripId: String) = repository.trip(tripId)

    override fun route(routeId: String) = repository.route(routeId)

    override fun stop(stopId: String) = repository.stop(stopId)

    override fun stopTimesForTrip(tripId: String, limit: Int) =
        repository.stopTimesForTrip(tripId, limit)

    override fun frequenciesForTrip(tripId: String, limit: Int) =
        repository.frequenciesForTrip(tripId, limit)

    override fun tripsForRouteDirection(
        routeId: String,
        directionId: Int,
        limit: Int,
    ) = repository.tripsForRouteDirection(routeId, directionId, limit)

    override fun isServiceActive(
        serviceId: String,
        serviceDate: GtfsServiceDate,
    ): Boolean {
        val exception = repository.calendarException(serviceId, serviceDate.toString())
        if (exception != null) return exception.exceptionType == 1
        val calendar = repository.calendar(serviceId) ?: return false
        val start = calendar.startDate?.let(GtfsServiceDate::parse) ?: return false
        val end = calendar.endDate?.let(GtfsServiceDate::parse) ?: return false
        if (serviceDate < start || serviceDate > end) return false
        return when (serviceDate.weekday) {
            GtfsWeekday.MONDAY -> calendar.monday
            GtfsWeekday.TUESDAY -> calendar.tuesday
            GtfsWeekday.WEDNESDAY -> calendar.wednesday
            GtfsWeekday.THURSDAY -> calendar.thursday
            GtfsWeekday.FRIDAY -> calendar.friday
            GtfsWeekday.SATURDAY -> calendar.saturday
            GtfsWeekday.SUNDAY -> calendar.sunday
        }
    }
}

class GermanyRealtimeMatcher(
    private val data: GermanyRealtimeStaticData,
    private val freshWindowSeconds: Long = 120L,
    private val maxStopTimesPerTrip: Int = 2_048,
    private val maxSelectorTrips: Int = 64,
    private val maxFrequencyRowsPerTrip: Int = 64,
) {
    init {
        require(freshWindowSeconds >= 0)
        require(maxStopTimesPerTrip in 1..2_048)
        require(maxSelectorTrips in 1..256)
        require(maxFrequencyRowsPerTrip in 1..512)
    }

    fun match(
        result: GermanyRealtimeFetchResult,
        nowEpochSeconds: Long,
    ): GermanyRealtimeOverlayResult = when (result) {
        is GermanyRealtimeFetchResult.Unavailable -> GermanyRealtimeOverlayResult.Unavailable(
            reason = result.reason,
            detail = result.detail,
        )
        is GermanyRealtimeFetchResult.Available -> match(result.snapshot, nowEpochSeconds)
    }

    fun match(
        snapshot: GermanyRealtimeSnapshot,
        nowEpochSeconds: Long,
    ): GermanyRealtimeOverlayResult {
        if (snapshot.incrementality != "FULL_DATASET") {
            return GermanyRealtimeOverlayResult.Unavailable(
                reason = GermanyRealtimeUnavailableReason.UNSUPPORTED_INCREMENTALITY,
                detail = "Realtime matcher requires FULL_DATASET snapshot",
            )
        }

        val sourceTimestamp = snapshot.feedTimestampEpochSeconds ?: snapshot.fetchedAtEpochSeconds
        val ageSeconds = (nowEpochSeconds - sourceTimestamp).coerceAtLeast(0L)
        val freshness = if (ageSeconds <= freshWindowSeconds) {
            GermanyRealtimeOverlayFreshnessState.FRESH
        } else {
            GermanyRealtimeOverlayFreshnessState.STALE
        }
        val provenance = GermanyRealtimeOverlayProvenance(
            source = snapshot.source,
            license = snapshot.license,
            gtfsRealtimeVersion = snapshot.gtfsRealtimeVersion,
            feedVersion = snapshot.feedVersion,
            feedTimestampEpochSeconds = snapshot.feedTimestampEpochSeconds,
            fetchedAtEpochSeconds = snapshot.fetchedAtEpochSeconds,
        )

        val overlays = snapshot.tripUpdates.mapNotNull(::matchTripUpdate)
        val alerts = snapshot.serviceAlerts.filter { alert ->
            alert.selectors.any(::selectorCanBeApplied)
        }

        return if (overlays.isEmpty() && alerts.isEmpty()) {
            GermanyRealtimeOverlayResult.NoMatch(
                freshness = freshness,
                ageSeconds = ageSeconds,
                provenance = provenance,
            )
        } else {
            GermanyRealtimeOverlayResult.Matched(
                tripOverlays = overlays,
                serviceAlerts = alerts,
                freshness = freshness,
                ageSeconds = ageSeconds,
                provenance = provenance,
            )
        }
    }

    private fun matchTripUpdate(
        update: GermanyRealtimeTripUpdate,
    ): GermanyRealtimeTripOverlay? {
        if (update.selectorValidity == GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE) {
            return null
        }
        val staticTrip = resolveTrip(
            tripId = update.tripId,
            routeId = update.routeId,
            directionId = update.directionId,
            startTime = update.startTime,
            startDate = update.startDate,
            selectorValidity = update.selectorValidity,
        ) ?: return null

        val staticStopTimes = data.stopTimesForTrip(staticTrip.id, maxStopTimesPerTrip)
        val matchedStops = update.stops.mapNotNull { stopUpdate ->
            matchStopUpdate(stopUpdate, staticStopTimes)
        }

        return GermanyRealtimeTripOverlay(
            staticTrip = staticTrip,
            startDate = update.startDate,
            startTime = update.startTime,
            cancelled = update.cancelled,
            scheduleRelationship = update.scheduleRelationship,
            stopUpdates = matchedStops,
            sourceTimestampEpochSeconds = update.updateTimestampEpochSeconds,
        )
    }

    private fun resolveTrip(
        tripId: String?,
        routeId: String?,
        directionId: Int?,
        startTime: String?,
        startDate: String?,
        selectorValidity: GermanyRealtimeTripSelectorValidity,
    ): GtfsScheduleTrip? {
        if (selectorValidity == GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE) {
            return null
        }

        if (tripId != null) {
            val trip = data.trip(tripId) ?: return null
            if (routeId != null && trip.routeId != routeId) return null
            if (directionId != null && trip.directionId != directionId) return null

            val serviceDate = startDate?.let(::parseServiceDate)
            if (startDate != null && serviceDate == null) return null
            if (serviceDate != null && !data.isServiceActive(trip.serviceId, serviceDate)) return null

            val frequencies = data.frequenciesForTrip(trip.id, maxFrequencyRowsPerTrip)
            if (frequencies.isNotEmpty()) {
                if (startTime == null || serviceDate == null) return null
                if (!frequencyInstanceIsPlausible(startTime, frequencies)) return null
            }
            return trip
        }

        if (selectorValidity != GermanyRealtimeTripSelectorValidity.VALID_IDLESS_SCHEDULED) {
            return null
        }
        val resolvedRouteId = routeId ?: return null
        val resolvedDirectionId = directionId ?: return null
        val resolvedStartTime = startTime ?: return null
        val serviceDate = startDate?.let(::parseServiceDate) ?: return null
        val normalizedStart = parseServiceSeconds(resolvedStartTime) ?: return null

        val candidates = data.tripsForRouteDirection(
            resolvedRouteId,
            resolvedDirectionId,
            maxSelectorTrips + 1,
        ).filter { trip ->
            data.isServiceActive(trip.serviceId, serviceDate) &&
                tripStartsAt(trip, normalizedStart)
        }
        return candidates.singleOrNull()
    }

    private fun tripStartsAt(
        trip: GtfsScheduleTrip,
        expectedStartSeconds: Int,
    ): Boolean {
        val first = data.stopTimesForTrip(trip.id, maxStopTimesPerTrip).firstOrNull() ?: return false
        val raw = first.departureTime ?: first.arrivalTime ?: return false
        return parseServiceSeconds(raw) == expectedStartSeconds
    }

    private fun frequencyInstanceIsPlausible(
        startTime: String,
        rows: List<GtfsScheduleFrequency>,
    ): Boolean {
        val instance = parseServiceSeconds(startTime) ?: return false
        return rows.any { row ->
            val start = parseServiceSeconds(row.startTime) ?: return@any false
            val end = parseServiceSeconds(row.endTime) ?: return@any false
            if (row.headwaySeconds <= 0 || start >= end || instance !in start until end) {
                return@any false
            }
            when (row.exactTimes) {
                1 -> (instance - start) % row.headwaySeconds == 0
                0, null -> true
                else -> false
            }
        }
    }

    private fun matchStopUpdate(
        update: GermanyRealtimeStopUpdate,
        staticStopTimes: List<GtfsScheduleStopTime>,
    ): GermanyRealtimeMatchedStopUpdate? {
        if (update.validity != GermanyRealtimeStopUpdateValidity.VALID) return null
        val scheduled = if (update.stopSequence != null) {
            staticStopTimes.singleOrNull { it.stopSequence == update.stopSequence }
        } else {
            val stopId = update.stopId ?: return null
            staticStopTimes.filter { it.stopId == stopId }.singleOrNull()
        } ?: return null

        val realtimeStopId = update.assignedStopId ?: update.stopId
        val platformCode = realtimeStopId?.let(data::stop)?.platformCode
            ?: data.stop(scheduled.stopId)?.platformCode

        return GermanyRealtimeMatchedStopUpdate(
            stopSequence = scheduled.stopSequence,
            scheduledStopId = scheduled.stopId,
            realtimeStopId = update.stopId,
            assignedStopId = update.assignedStopId,
            platformCode = platformCode,
            arrivalDelaySeconds = update.arrivalDelaySeconds,
            departureDelaySeconds = update.departureDelaySeconds,
            arrivalTimeEpochSeconds = update.arrivalTimeEpochSeconds,
            departureTimeEpochSeconds = update.departureTimeEpochSeconds,
            scheduleRelationship = update.scheduleRelationship,
            pickupType = update.pickupType,
            dropOffType = update.dropOffType,
            stopHeadsign = update.stopHeadsign,
        )
    }

    private fun selectorCanBeApplied(
        selector: GermanyRealtimeAlertSelector,
    ): Boolean {
        if (selector.validity != GermanyRealtimeAlertSelectorValidity.VALID) return false
        val route = selector.routeId?.let(data::route)
        if (selector.routeId != null && route == null) return false
        if (selector.stopId != null && data.stop(selector.stopId) == null) return false

        val tripSelector = selector.trip
        val trip = tripSelector?.let { trip ->
            resolveTrip(
                tripId = trip.tripId,
                routeId = trip.routeId,
                directionId = trip.directionId,
                startTime = trip.startTime,
                startDate = trip.startDate,
                selectorValidity = trip.validity,
            )
        }
        if (tripSelector != null && trip == null) return false

        if (trip != null) {
            val tripRoute = data.route(trip.routeId) ?: return false
            if (selector.routeId != null && trip.routeId != selector.routeId) return false
            if (selector.directionId != null && trip.directionId != selector.directionId) return false
            if (selector.agencyId != null && tripRoute.agencyId != selector.agencyId) return false
            if (selector.routeType != null && tripRoute.routeType != selector.routeType) return false
            if (selector.stopId != null) {
                val servesStop = data.stopTimesForTrip(trip.id, maxStopTimesPerTrip)
                    .any { it.stopId == selector.stopId }
                if (!servesStop) return false
            }
        } else if (route != null) {
            if (selector.agencyId != null && route.agencyId != selector.agencyId) return false
            if (selector.routeType != null && route.routeType != selector.routeType) return false
        }

        return true
    }

    private fun parseServiceDate(raw: String): GtfsServiceDate? =
        runCatching { GtfsServiceDate.parse(raw) }.getOrNull()

    private fun parseServiceSeconds(raw: String): Int? =
        runCatching { GtfsServiceTime.parse(raw).secondsFromServiceDayStart }.getOrNull()
}
