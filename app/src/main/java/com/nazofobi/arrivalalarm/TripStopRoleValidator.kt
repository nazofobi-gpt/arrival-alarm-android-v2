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
}

sealed interface TripStopRoleResult {
    data class Accepted(val roles: ConfirmedTripStopRoles) : TripStopRoleResult
    data class Rejected(val problem: TripStopRoleProblem) : TripStopRoleResult
}

/**
 * Uses a previously resolved and service-date-verified GtfsTripDetail.
 * The user must explicitly choose boarding/destination and an alarm target
 * (the destination may be provided as the alarm choice by the UI).
 * Never substitute a different trip, sort out-of-order GTFS data, or guess a role.
 */
object TripStopRoleValidator {
    fun validate(detail: GtfsTripDetail, roles: TripStopRoles): TripStopRoleResult {
        fun reject(reason: TripStopRoleProblem) = TripStopRoleResult.Rejected(reason)
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

        return TripStopRoleResult.Accepted(
            ConfirmedTripStopRoles(detail.identity, boarding, destination, alarm)
        )
    }
}
