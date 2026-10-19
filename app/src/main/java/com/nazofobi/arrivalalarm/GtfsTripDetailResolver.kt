package com.nazofobi.arrivalalarm

/**
 * A particular GTFS trip on a particular service date, not just a route or a display label.
 * Route/service/direction are retained so a reused trip_id after a feed update fails closed.
 * Frequency-based trips require an additional instance departure and are not resolved by
 * this static scheduled-trip reader until that disambiguation is implemented.
 */
data class GtfsTripInstanceIdentity(
    val tripId: String,
    val serviceDate: GtfsServiceDate,
    val serviceId: String,
    val routeId: String,
    val directionId: Int?,
    val frequencyStartSeconds: Int? = null,
) {
    init {
        require(tripId.isNotBlank() && serviceId.isNotBlank() && routeId.isNotBlank())
        require(frequencyStartSeconds == null || frequencyStartSeconds >= 0)
    }

    companion object {
        fun scheduled(trip: GtfsScheduleTrip, serviceDate: GtfsServiceDate) =
            GtfsTripInstanceIdentity(
                tripId = trip.id,
                serviceDate = serviceDate,
                serviceId = trip.serviceId,
                routeId = trip.routeId,
                directionId = trip.directionId,
            )
    }
}

data class GtfsTripDetailStop(
    val stopSequence: Int,
    val stopId: String,
    val name: String,
    val parentStationId: String?,
    val platformCode: String?,
    val wheelchairBoarding: Int?,
    val scheduledArrival: String?,
    val scheduledDeparture: String?,
    // Missing provider coordinates are never treated as a valid boarding/alighting point.
    // Defaults keep pre-existing diagnostic/test callers source-compatible.
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val pickupType: Int? = null,
    val dropOffType: Int? = null,
)

data class GtfsTripDetail(
    val identity: GtfsTripInstanceIdentity,
    val headsign: String?,
    val stops: List<GtfsTripDetailStop>,
)

enum class GtfsTripDetailFailure {
    INDEX_NOT_READY,
    DATA_READ_FAILED,
    TRIP_NOT_FOUND,
    TRIP_CHANGED,
    SERVICE_INACTIVE,
    FREQUENCY_INSTANCE_UNSUPPORTED,
    MISSING_OR_TRUNCATED_SEQUENCE,
    INVALID_STOP_SEQUENCE,
    INVALID_STOP_TIME,
    MISSING_STOP_METADATA,
}

sealed interface GtfsTripDetailResult {
    data class Available(val detail: GtfsTripDetail) : GtfsTripDetailResult
    data class Unavailable(val reason: GtfsTripDetailFailure) : GtfsTripDetailResult
}

/** Small seam for deterministic host-side tests and the real Germany-wide GTFS database. */
interface GtfsTripDetailSource {
    fun trip(id: String): GtfsScheduleTrip?
    fun stopTimes(tripId: String, limit: Int): List<GtfsScheduleStopTime>
    fun stop(id: String): GtfsScheduleStop?
    fun calendar(serviceId: String): GtfsScheduleCalendar?
    fun calendarException(serviceId: String, date: String): GtfsScheduleCalendarDate?
    fun hasFrequencies(tripId: String): Boolean
}

class GtfsTripDetailResolver(private val source: GtfsTripDetailSource) {
    constructor(repository: GtfsScheduleRepository) : this(
        object : GtfsTripDetailSource {
            override fun trip(id: String) = repository.trip(id)
            override fun stopTimes(tripId: String, limit: Int) =
                repository.stopTimesForTrip(tripId, limit)
            override fun stop(id: String) = repository.stop(id)
            override fun calendar(serviceId: String) = repository.calendar(serviceId)
            override fun calendarException(serviceId: String, date: String) =
                repository.calendarException(serviceId, date)
            override fun hasFrequencies(tripId: String) =
                repository.frequenciesForTrip(tripId, limit = 1).isNotEmpty()
        }
    )

    /**
     * Never reconstruct an explicit selection from a route label or an arbitrary current date.
     * A stale/reused identity, inactive service, incomplete feed or frequency template is
     * reported as unavailable, rather than showing a misleading selectable stop list.
     */
    fun resolve(identity: GtfsTripInstanceIdentity): GtfsTripDetailResult {
        fun unavailable(reason: GtfsTripDetailFailure) =
            GtfsTripDetailResult.Unavailable(reason)

        val trip = source.trip(identity.tripId)
            ?: return unavailable(GtfsTripDetailFailure.TRIP_NOT_FOUND)
        if (trip.serviceId != identity.serviceId ||
            trip.routeId != identity.routeId ||
            trip.directionId != identity.directionId
        ) return unavailable(GtfsTripDetailFailure.TRIP_CHANGED)

        // GTFS frequencies describes templates. Displaying one template as a concrete
        // departure would silently choose the wrong vehicle/time.
        if (identity.frequencyStartSeconds != null || source.hasFrequencies(trip.id)) {
            return unavailable(GtfsTripDetailFailure.FREQUENCY_INSTANCE_UNSUPPORTED)
        }

        val exception = source.calendarException(trip.serviceId, identity.serviceDate.toString())
        val exceptionType = exception?.let {
            runCatching { GtfsCalendarExceptionType.fromValue(it.exceptionType) }.getOrNull()
                ?: return unavailable(GtfsTripDetailFailure.SERVICE_INACTIVE)
        }
        val calendar = source.calendar(trip.serviceId)
        val baseService = calendar?.let {
            val start = it.startDate?.let { date ->
                runCatching { GtfsServiceDate.parse(date) }.getOrNull()
            }
            val end = it.endDate?.let { date ->
                runCatching { GtfsServiceDate.parse(date) }.getOrNull()
            }
            if (start != null && end != null && end >= start) {
                GtfsCalendarService(
                    trip.serviceId, start, end,
                    buildSet {
                        if (it.monday) add(GtfsWeekday.MONDAY)
                        if (it.tuesday) add(GtfsWeekday.TUESDAY)
                        if (it.wednesday) add(GtfsWeekday.WEDNESDAY)
                        if (it.thursday) add(GtfsWeekday.THURSDAY)
                        if (it.friday) add(GtfsWeekday.FRIDAY)
                        if (it.saturday) add(GtfsWeekday.SATURDAY)
                        if (it.sunday) add(GtfsWeekday.SUNDAY)
                    },
                )
            } else null
        }
        val serviceCalendar = GtfsServiceCalendar(
            listOfNotNull(baseService),
            listOfNotNull(exceptionType?.let {
                GtfsCalendarException(trip.serviceId, identity.serviceDate, it)
            }),
        )
        if (!serviceCalendar.isActive(trip.serviceId, identity.serviceDate)) {
            return unavailable(GtfsTripDetailFailure.SERVICE_INACTIVE)
        }

        val stopTimes = source.stopTimes(trip.id, MAX_DETAIL_STOPS)
        // Exactly the cap is ambiguous (a following row may be hidden), so reject.
        if (stopTimes.size < 2 || stopTimes.size >= MAX_DETAIL_STOPS) {
            return unavailable(GtfsTripDetailFailure.MISSING_OR_TRUNCATED_SEQUENCE)
        }
        if (stopTimes.any { it.tripId != trip.id || it.stopSequence < 0 || it.stopId.isBlank() } ||
            stopTimes.zipWithNext().any { (a, b) -> a.stopSequence >= b.stopSequence }
        ) return unavailable(GtfsTripDetailFailure.INVALID_STOP_SEQUENCE)

        // GTFS allows times beyond midnight (24:10:00) and absent non-timepoint
        // clocks, but a malformed non-null provider clock must never be used
        // to offer a concrete, user-confirmable service.
        fun validClock(clock: String?): Boolean {
            if (clock == null) return true
            return try {
                GtfsServiceTime.parse(clock)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
        if (stopTimes.any { !validClock(it.arrivalTime) || !validClock(it.departureTime) }) {
            return unavailable(GtfsTripDetailFailure.INVALID_STOP_TIME)
        }

        val stops = mutableListOf<GtfsTripDetailStop>()
        val stopCache = mutableMapOf<String, GtfsScheduleStop?>()
        for (time in stopTimes) {
            val stop = stopCache.getOrPut(time.stopId) { source.stop(time.stopId) }
                ?: return unavailable(GtfsTripDetailFailure.MISSING_STOP_METADATA)
            // A provider may expose a stop row with unusable metadata. Do not
            // offer boarding or alarm targets whose physical location is unverified.
            if (stop.id != time.stopId || stop.name.isBlank() ||
                !stop.latitude.isFinite() || !stop.longitude.isFinite() ||
                stop.latitude !in -90.0..90.0 || stop.longitude !in -180.0..180.0
            ) return unavailable(GtfsTripDetailFailure.MISSING_STOP_METADATA)
            stops += GtfsTripDetailStop(
                stopSequence = time.stopSequence,
                stopId = time.stopId,
                name = stop.name,
                parentStationId = stop.parentStationId,
                platformCode = stop.platformCode,
                wheelchairBoarding = stop.wheelchairBoarding,
                scheduledArrival = time.arrivalTime,
                scheduledDeparture = time.departureTime,
                latitude = stop.latitude,
                longitude = stop.longitude,
                pickupType = time.pickupType,
                dropOffType = time.dropOffType,
            )
        }
        return GtfsTripDetailResult.Available(GtfsTripDetail(identity, trip.headsign, stops))
    }

    companion object {
        private const val MAX_DETAIL_STOPS = 2_048
    }
}
