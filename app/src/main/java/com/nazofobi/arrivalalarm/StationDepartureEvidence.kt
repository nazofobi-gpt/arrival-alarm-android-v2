package com.nazofobi.arrivalalarm

/**
 * The station board never infers a journey or arms an alarm.
 * Only source-resolved scheduled trips carry concrete GTFS identity and
 * boarding sequence into the later user-confirmation flow.
 */
internal data class StationDepartureEvidence(
    val identity: GtfsTripInstanceIdentity,
    val boardingStopSequence: Int,
)

internal fun stationDepartureEvidence(
    trip: GtfsScheduleTrip,
    stopSequence: Int,
    serviceDate: GtfsServiceDate?,
): StationDepartureEvidence? {
    if (serviceDate == null || stopSequence < 0 ||
        trip.id.isBlank() || trip.serviceId.isBlank() || trip.routeId.isBlank()
    ) return null
    return StationDepartureEvidence(
        identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate),
        boardingStopSequence = stopSequence,
    )
}
