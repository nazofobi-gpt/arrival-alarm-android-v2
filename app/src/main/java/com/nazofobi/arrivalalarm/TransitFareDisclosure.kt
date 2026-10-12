package com.nazofobi.arrivalalarm

/**
 * A GTFS trip's bikes_allowed field describes carriage on that trip, not a ticket entitlement.
 * 0/null/unsupported values carry no evidence. A multi-leg journey is only confirmed as
 * bike-compatible if every transit leg explicitly permits bicycles.
 */
enum class BicycleCarriage {
    ALLOWED,
    NOT_ALLOWED,
    UNKNOWN,
}

internal fun bicycleCarriageForTripIds(
    tripIds: List<String>,
    findTrip: (String) -> GtfsScheduleTrip?,
): BicycleCarriage {
    if (tripIds.isEmpty()) return BicycleCarriage.UNKNOWN
    var hasUnknownLeg = false
    for (tripId in tripIds) {
        if (tripId.isBlank()) {
            hasUnknownLeg = true
            continue
        }
        when (findTrip(tripId)?.bikesAllowed) {
            1 -> Unit
            2 -> return BicycleCarriage.NOT_ALLOWED
            else -> hasUnknownLeg = true
        }
    }
    return if (hasUnknownLeg) BicycleCarriage.UNKNOWN else BicycleCarriage.ALLOWED
}
