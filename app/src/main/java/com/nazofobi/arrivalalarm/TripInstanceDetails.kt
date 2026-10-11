package com.nazofobi.arrivalalarm

/**
 * Identifies one actual GTFS service, not just a route or a reusable trip template.
 * Frequency-operated trips need the concrete start time to disambiguate departures.
 */
data class TripInstanceIdentity(
    val tripId: String,
    val serviceDate: GtfsServiceDate,
    val directionId: Int?,
    val frequencyStartTime: String? = null,
) {
    init {
        require(tripId.isNotBlank()) { "An exact GTFS trip ID is required" }
        require(frequencyStartTime == null || runCatching {
            GtfsServiceTime.parse(frequencyStartTime)
        }.isSuccess) { "Invalid frequency start time" }
    }
}

data class TripInstanceStop(
    val stopId: String,
    val stopSequence: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val parentStationId: String?,
    val platformCode: String?,
    val arrivalTime: String?,
    val departureTime: String?,
)

data class TripInstanceDetails(
    val identity: TripInstanceIdentity,
    val routeId: String,
    val headsign: String?,
    val stops: List<TripInstanceStop>,
)

sealed interface TripInstanceDetailResult {
    data class Found(val details: TripInstanceDetails) : TripInstanceDetailResult
    data class Unavailable(val reason: Reason) : TripInstanceDetailResult

    enum class Reason {
        INDEX_NOT_READY,
        TRIP_NOT_FOUND,
        DIRECTION_CHANGED,
        SERVICE_NOT_ACTIVE,
        FREQUENCY_INSTANCE_REQUIRED,
        FREQUENCY_INSTANCE_INVALID,
        STOP_SEQUENCE_INCOMPLETE,
        STOP_METADATA_MISSING,
        DATA_READ_FAILED,
    }
}

/**
 * The assembler has injectable readers so identity and ordering can be checked on the JVM.
 * A partial/invalid schedule is never presented as the full selected trip.
 */
internal class TripInstanceDetailAssembler(
    private val trip: (String) -> GtfsScheduleTrip?,
    private val stopTimes: (String, Int) -> List<GtfsScheduleStopTime>,
    private val stop: (String) -> GtfsScheduleStop?,
    private val serviceActive: (String, GtfsServiceDate) -> Boolean,
    private val frequencyWindows: (String) -> List<GtfsScheduleFrequency>,
) {
    fun resolve(identity: TripInstanceIdentity): TripInstanceDetailResult {
        val template = trip(identity.tripId)
            ?: return unavailable(TripInstanceDetailResult.Reason.TRIP_NOT_FOUND)
        if (template.directionId != identity.directionId) {
            return unavailable(TripInstanceDetailResult.Reason.DIRECTION_CHANGED)
        }
        if (!serviceActive(template.serviceId, identity.serviceDate)) {
            return unavailable(TripInstanceDetailResult.Reason.SERVICE_NOT_ACTIVE)
        }

        val frequencies = frequencyWindows(identity.tripId)
        val frequencyStart = identity.frequencyStartTime
        if (frequencies.isNotEmpty()) {
            if (frequencyStart == null) {
                return unavailable(TripInstanceDetailResult.Reason.FREQUENCY_INSTANCE_REQUIRED)
            }
            val selectedSeconds = GtfsServiceTime.parse(frequencyStart).secondsFromServiceDayStart
            val matchesWindow = frequencies.any { window ->
                val start = runCatching {
                    GtfsServiceTime.parse(window.startTime).secondsFromServiceDayStart
                }.getOrNull() ?: return@any false
                val end = runCatching {
                    GtfsServiceTime.parse(window.endTime).secondsFromServiceDayStart
                }.getOrNull() ?: return@any false
                window.exactTimes == 1 &&
                    window.headwaySeconds > 0 &&
                    selectedSeconds >= start &&
                    selectedSeconds < end &&
                    (selectedSeconds - start) % window.headwaySeconds == 0
            }
            if (!matchesWindow) {
                return unavailable(TripInstanceDetailResult.Reason.FREQUENCY_INSTANCE_INVALID)
            }
        } else if (frequencyStart != null) {
            return unavailable(TripInstanceDetailResult.Reason.FREQUENCY_INSTANCE_INVALID)
        }

        val times = stopTimes(identity.tripId, MAX_STOP_ROWS)
        // If the cap is reached, the query may have cut off the tail of the journey.
        if (times.size < 2 || times.size >= MAX_STOP_ROWS ||
            times.any { it.tripId != identity.tripId || it.stopId.isBlank() || it.stopSequence <= 0 } ||
            times.zipWithNext().any { (previous, next) ->
                previous.stopSequence >= next.stopSequence
            }
        ) {
            return unavailable(TripInstanceDetailResult.Reason.STOP_SEQUENCE_INCOMPLETE)
        }

        val result = ArrayList<TripInstanceStop>(times.size)
        for (time in times) {
            val detail = stop(time.stopId)
                ?: return unavailable(TripInstanceDetailResult.Reason.STOP_METADATA_MISSING)
            if (detail.id != time.stopId || !detail.latitude.isFinite() ||
                !detail.longitude.isFinite() ||
                detail.latitude !in -90.0..90.0 || detail.longitude !in -180.0..180.0
            ) {
                return unavailable(TripInstanceDetailResult.Reason.STOP_METADATA_MISSING)
            }
            result += TripInstanceStop(
                stopId = detail.id,
                stopSequence = time.stopSequence,
                name = detail.name,
                latitude = detail.latitude,
                longitude = detail.longitude,
                parentStationId = detail.parentStationId,
                platformCode = detail.platformCode,
                arrivalTime = time.arrivalTime,
                departureTime = time.departureTime,
            )
        }
        return TripInstanceDetailResult.Found(
            TripInstanceDetails(identity, template.routeId, template.headsign, result)
        )
    }

    private fun unavailable(reason: TripInstanceDetailResult.Reason) =
        TripInstanceDetailResult.Unavailable(reason)

    private companion object {
        const val MAX_STOP_ROWS = 512
    }
}

/** Reads only the installed, real GTFS graph; realtime cannot switch the chosen trip. */
internal class GtfsTripInstanceDetailReader(index: NationwideTransitIndex) {
    private val repository = GtfsScheduleRepository(index)
    private val ready = { index.isReady() }
    private val assembler = TripInstanceDetailAssembler(
        trip = repository::trip,
        stopTimes = repository::stopTimesForTrip,
        stop = repository::stop,
        serviceActive = ::serviceActive,
        frequencyWindows = { repository.frequenciesForTrip(it, 64) },
    )

    fun resolve(identity: TripInstanceIdentity): TripInstanceDetailResult {
        if (!ready()) {
            return TripInstanceDetailResult.Unavailable(
                TripInstanceDetailResult.Reason.INDEX_NOT_READY,
            )
        }
        return runCatching { assembler.resolve(identity) }.getOrElse {
            TripInstanceDetailResult.Unavailable(TripInstanceDetailResult.Reason.DATA_READ_FAILED)
        }
    }

    private fun serviceActive(serviceId: String, serviceDate: GtfsServiceDate): Boolean {
        val calendar = repository.calendar(serviceId)?.let { row ->
            val start = GtfsServiceDate.parse(row.startDate ?: return false)
            val end = GtfsServiceDate.parse(row.endDate ?: return false)
            GtfsCalendarService(
                serviceId = row.serviceId,
                startDate = start,
                endDate = end,
                activeDays = buildSet {
                    if (row.monday) add(GtfsWeekday.MONDAY)
                    if (row.tuesday) add(GtfsWeekday.TUESDAY)
                    if (row.wednesday) add(GtfsWeekday.WEDNESDAY)
                    if (row.thursday) add(GtfsWeekday.THURSDAY)
                    if (row.friday) add(GtfsWeekday.FRIDAY)
                    if (row.saturday) add(GtfsWeekday.SATURDAY)
                    if (row.sunday) add(GtfsWeekday.SUNDAY)
                },
            )
        }
        val exception = repository.calendarException(serviceId, serviceDate.toString())
            ?.let { row ->
                GtfsCalendarException(
                    serviceId = row.serviceId,
                    date = serviceDate,
                    type = GtfsCalendarExceptionType.fromValue(row.exceptionType),
                )
            }
        return GtfsServiceCalendar(
            calendars = listOfNotNull(calendar),
            exceptions = listOfNotNull(exception),
        ).isActive(serviceId, serviceDate)
    }
}
