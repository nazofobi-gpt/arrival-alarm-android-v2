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
    // Do not offer a selectable trip whose opaque provider identity contains
    // invisible format or control characters (for example bidi overrides).
    // Normal printable Unicode GTFS identifiers remain valid.
    fun usableId(id: String): Boolean = id.isNotBlank() && id.none { ch ->
        Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT.toInt()
    }
    if (serviceDate == null || stopSequence < 0 ||
        !usableId(trip.id) || !usableId(trip.serviceId) || !usableId(trip.routeId)
    ) return null
    return StationDepartureEvidence(
        identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate),
        boardingStopSequence = stopSequence,
    )
}
