package com.nazofobi.arrivalalarm

/** Explicit stop roles remain bound to the exact selected service-day/trip identity. */
data class TripStopRoles(
    val boardingSequence: Int,
    val destinationSequence: Int,
    val alarmTargetSequence: Int,
)

data class ConfirmedTripStopRoles(
    val identity: GtfsTripInstanceIdentity,
    val boarding: GtfsTripDetailStop,
    val destination: GtfsTripDetailStop,
    val alarmTarget: GtfsTripDetailStop,
)

enum class TripStopRoleProblem {
    INVALID_STOP_SEQUENCE,
    BOARDING_STOP_NOT_FOUND,
    DESTINATION_STOP_NOT_FOUND,
    ALARM_STOP_NOT_FOUND,
    BOARDING_NOT_ALLOWED,
    ALIGHTING_NOT_ALLOWED,
    WRONG_STOP_ORDER,
    MISSING_ACTIONABLE_STOP_TIME,
}

sealed interface TripStopRoleResult {
    data class Accepted(val roles: ConfirmedTripStopRoles) : TripStopRoleResult
    data class Rejected(val problem: TripStopRoleProblem) : TripStopRoleResult
}

/**
 * Some GTFS non-timepoint stops have neither arrival nor departure times.
 * They may remain in the displayed itinerary, but cannot be confirmed as
 * timed boarding, destination or alarm events. A malformed supplied time
 * must also fail closed even if the other clock is populated.
 */
internal fun GtfsTripDetailStop.hasActionableScheduleClock(): Boolean {
    if (scheduledArrival == null && scheduledDeparture == null) return false
    return try {
        val arrival = scheduledArrival?.let {
            GtfsServiceTime.parse(it).secondsFromServiceDayStart
        }
        val departure = scheduledDeparture?.let {
            GtfsServiceTime.parse(it).secondsFromServiceDayStart
        }
        // A malformed or inverted dwell interval cannot authorize boarding or an alarm,
        // even if only one of its clocks is required for the selected role.
        arrival == null || departure == null || arrival <= departure
    } catch (_: IllegalArgumentException) {
        false
    }
}

/** A boarding requires departure time; an alighting/alarm event requires arrival time. */
internal fun GtfsTripDetailStop.hasActionableBoardingClock(): Boolean =
    scheduledDeparture != null && hasActionableScheduleClock()

internal fun GtfsTripDetailStop.hasActionableArrivalClock(): Boolean =
    scheduledArrival != null && hasActionableScheduleClock()

/**
 * Uses a previously resolved and service-date-verified GtfsTripDetail.
 * The user must explicitly choose boarding/destination and an alarm target
 * (the destination may be provided as the alarm choice by the UI).
 * Never substitute a different trip, sort out-of-order GTFS data, or guess a role.
 */
object TripStopRoleValidator {
    fun validate(detail: GtfsTripDetail, roles: TripStopRoles): TripStopRoleResult {
        fun reject(reason: TripStopRoleProblem) = TripStopRoleResult.Rejected(reason)
        // A pre-resolved UI detail can bypass the persistence store's identity gate.
        // Placeholder IDs must never authorize explicit boarding/alarm consent.
        if (detail.identity.tripId.isBlank() || detail.identity.serviceId.isBlank() ||
            detail.identity.routeId.isBlank()
        ) return reject(TripStopRoleProblem.INVALID_STOP_SEQUENCE)
        val stops = detail.stops
        if (stops.size < 2 || stops.any {
                it.stopSequence < 0 || it.stopId.isBlank() || it.name.isBlank() ||
                    !it.latitude.isFinite() || !it.longitude.isFinite() ||
                    it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0
            } || stops.zipWithNext().any { (a, b) -> a.stopSequence >= b.stopSequence }
        ) return reject(TripStopRoleProblem.INVALID_STOP_SEQUENCE)

        val boarding = stops.singleOrNull { it.stopSequence == roles.boardingSequence }
            ?: return reject(TripStopRoleProblem.BOARDING_STOP_NOT_FOUND)
        val destination = stops.singleOrNull { it.stopSequence == roles.destinationSequence }
            ?: return reject(TripStopRoleProblem.DESTINATION_STOP_NOT_FOUND)
        val alarm = stops.singleOrNull { it.stopSequence == roles.alarmTargetSequence }
            ?: return reject(TripStopRoleProblem.ALARM_STOP_NOT_FOUND)

        // Null/0 is regular GTFS pickup/drop-off. Values 1, 2 and 3 are
        // unavailable or request-only; do not silently present them as ordinary.
        if (boarding.pickupType != null && boarding.pickupType != 0) {
            return reject(TripStopRoleProblem.BOARDING_NOT_ALLOWED)
        }
        if (destination.dropOffType != null && destination.dropOffType != 0) {
            return reject(TripStopRoleProblem.ALIGHTING_NOT_ALLOWED)
        }
        if (boarding.stopSequence >= destination.stopSequence ||
            alarm.stopSequence <= boarding.stopSequence ||
            alarm.stopSequence > destination.stopSequence
        ) return reject(TripStopRoleProblem.WRONG_STOP_ORDER)
        if (!boarding.hasActionableBoardingClock() ||
            !destination.hasActionableArrivalClock() ||
            !alarm.hasActionableArrivalClock()
        ) {
            return reject(TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME)
        }

        // A callback may supply a pre-resolved detail without the normal resolver's
        // cross-stop chronology validation. Do not confirm a trip where boarding
        // happens after the selected alarm or destination arrival.
        val boardingDeparture = GtfsServiceTime.parse(boarding.scheduledDeparture!!)
            .secondsFromServiceDayStart
        val alarmArrival = GtfsServiceTime.parse(alarm.scheduledArrival!!)
            .secondsFromServiceDayStart
        val destinationArrival = GtfsServiceTime.parse(destination.scheduledArrival!!)
            .secondsFromServiceDayStart
        if (boardingDeparture > alarmArrival || alarmArrival > destinationArrival) {
            return reject(TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME)
        }

        // A plausible boarding/alarm/destination clock is not sufficient if an
        // unselected intermediate stop runs backwards in GTFS service-day time.
        // Untimed non-timepoint stops remain legal: compare only known events.
        var lastKnownSeconds = boardingDeparture
        for (stop in stops) {
            if (stop.stopSequence <= boarding.stopSequence) continue
            if (stop.stopSequence > destination.stopSequence) break

            val arrival = try {
                stop.scheduledArrival?.let {
                    GtfsServiceTime.parse(it).secondsFromServiceDayStart
                }
            } catch (_: IllegalArgumentException) {
                return reject(TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME)
            }
            val departure = try {
                stop.scheduledDeparture?.let {
                    GtfsServiceTime.parse(it).secondsFromServiceDayStart
                }
            } catch (_: IllegalArgumentException) {
                return reject(TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME)
            }
            if (arrival != null) {
                if (arrival < lastKnownSeconds) {
                    return reject(TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME)
                }
                lastKnownSeconds = arrival
            }
            if (departure != null) {
                if (departure < lastKnownSeconds) {
                    return reject(TripStopRoleProblem.MISSING_ACTIONABLE_STOP_TIME)
                }
                lastKnownSeconds = departure
            }
        }

        return TripStopRoleResult.Accepted(
            ConfirmedTripStopRoles(detail.identity, boarding, destination, alarm)
        )
    }
}

/**
 * Counts real, ordered GTFS stop occurrences from confirmed boarding to the
 * selected alarm stop, inclusive of the alarm stop. GTFS stop_sequence values
 * are identifiers/order keys, not consecutive ordinal stop counts.
 *
 * This is planned journey information, never live GPS/realtime progress.
 * Fail closed when the persisted roles do not match the current verified trip.
 */
internal fun plannedPhysicalStopsFromBoardingToAlarm(
    selection: GtfsSelectedTrip,
    detail: GtfsTripDetail,
): Int? {
    if (selection.identity != detail.identity) return null
    val validated = TripStopRoleValidator.validate(
        detail,
        TripStopRoles(
            selection.boarding.sequence,
            selection.destination.sequence,
            selection.alarmTarget.sequence,
        ),
    ) as? TripStopRoleResult.Accepted ?: return null
    if (validated.roles.boarding.stopId != selection.boarding.stopId ||
        validated.roles.destination.stopId != selection.destination.stopId ||
        validated.roles.alarmTarget.stopId != selection.alarmTarget.stopId
    ) return null

    val boardingIndex = detail.stops.indexOfFirst {
        it.stopSequence == selection.boarding.sequence
    }
    val alarmIndex = detail.stops.indexOfFirst {
        it.stopSequence == selection.alarmTarget.sequence
    }
    return if (boardingIndex >= 0 && alarmIndex > boardingIndex) {
        alarmIndex - boardingIndex
    } else null
}
