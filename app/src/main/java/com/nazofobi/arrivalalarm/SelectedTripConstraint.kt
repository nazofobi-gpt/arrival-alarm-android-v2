package com.nazofobi.arrivalalarm

/**
 * A confirmed scheduled trip is constrained by its full service-day identity and
 * exact ordered boarding/destination stop occurrences. Stop IDs alone are not
 * sufficient because a loop can visit the same stop more than once.
 */
data class SelectedTripConstraint(
    val identity: GtfsTripInstanceIdentity,
    val boarding: GtfsSelectedStopRef,
    val destination: GtfsSelectedStopRef,
) {
    companion object {
        fun from(selection: GtfsSelectedTrip) = SelectedTripConstraint(
            identity = selection.identity,
            boarding = selection.boarding,
            destination = selection.destination,
        )
    }
}

enum class SelectedTripConflictReason {
    UNSUPPORTED,
    INDEX_NOT_READY,
    DATA_UNAVAILABLE,
    TRIP_UNAVAILABLE,
    IDENTITY_DRIFT,
    SERVICE_INACTIVE,
    FREQUENCY_INSTANCE_UNSUPPORTED,
    INVALID_IDENTITY,
    INVALID_STOP_ROLES,
    STOP_REFERENCE_MISMATCH,
    MISSING_SCHEDULE_EVIDENCE,
    CANCELLED,
}

enum class SelectedTripAlternativeAction {
    SELECT_ANOTHER_TRIP,
}

data class SelectedTripConflict(
    val reason: SelectedTripConflictReason,
    val alternativeAction: SelectedTripAlternativeAction =
        SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP,
    val roleProblem: TripStopRoleProblem? = null,
)

sealed interface SelectedTripPlanningOutcome {
    data class Planned(val option: RouteOption) : SelectedTripPlanningOutcome
    data class Conflict(val conflict: SelectedTripConflict) : SelectedTripPlanningOutcome
}

/** Reject hidden/control characters in provider identities before they become UI or cache keys. */
internal fun isUsableSelectedTripIdentityComponent(value: String): Boolean =
    value.isNotBlank() && value.none { character ->
        Character.isISOControl(character) ||
            Character.getType(character) == Character.FORMAT.toInt()
    }

internal fun selectedTripConstraintInputConflict(
    constraint: SelectedTripConstraint,
): SelectedTripConflict? {
    val identity = constraint.identity
    if (listOf(identity.tripId, identity.serviceId, identity.routeId)
            .any { !isUsableSelectedTripIdentityComponent(it) }
    ) return SelectedTripConflict(SelectedTripConflictReason.INVALID_IDENTITY)

    if (identity.frequencyStartSeconds != null) {
        return SelectedTripConflict(SelectedTripConflictReason.FREQUENCY_INSTANCE_UNSUPPORTED)
    }

    if (constraint.boarding.sequence < 0 ||
        constraint.destination.sequence < 0 ||
        constraint.boarding.sequence >= constraint.destination.sequence ||
        !isUsableSelectedTripIdentityComponent(constraint.boarding.stopId) ||
        !isUsableSelectedTripIdentityComponent(constraint.destination.stopId)
    ) return SelectedTripConflict(SelectedTripConflictReason.INVALID_STOP_ROLES)

    return null
}

internal fun isWellFormedSelectedTripConstraint(
    constraint: SelectedTripConstraint,
): Boolean = selectedTripConstraintInputConflict(constraint) == null

/** Stable, unambiguous UI identity; every service-day and stop-role component is length-framed. */
internal fun selectedTripRouteOptionId(constraint: SelectedTripConstraint): String {
    val identity = constraint.identity
    val parts = listOf(
        identity.tripId,
        identity.serviceDate.toString(),
        identity.serviceId,
        identity.routeId,
        identity.directionId?.toString().orEmpty(),
        identity.frequencyStartSeconds?.toString().orEmpty(),
        constraint.boarding.sequence.toString(),
        constraint.boarding.stopId,
        constraint.destination.sequence.toString(),
        constraint.destination.stopId,
    )
    return "selected-trip:" + parts.joinToString(separator = "|") { part ->
        part.length.toString() + ":" + part
    }
}

/**
 * A constrained option must prove that its first/last physical stops are the
 * exact selected occurrences. Missing sequence or timing evidence fails closed.
 */
internal fun routeOptionCarriesSelectedTripConstraint(option: RouteOption): Boolean {
    val constraint = option.selectedTripConstraint ?: return true
    if (!isWellFormedSelectedTripConstraint(constraint) ||
        option.id != selectedTripRouteOptionId(constraint) ||
        option.tripIds.singleOrNull() != constraint.identity.tripId ||
        option.stops.size < 2
    ) return false

    val sequences = option.stops.map { it.stopSequence ?: return false }
    if (sequences.first() != constraint.boarding.sequence ||
        sequences.last() != constraint.destination.sequence ||
        sequences.zipWithNext().any { (first, next) -> first >= next }
    ) return false

    val boarding = option.stops.first()
    val destination = option.stops.last()
    if (boarding.id != constraint.boarding.stopId ||
        destination.id != constraint.destination.stopId ||
        boarding.plannedDeparture.isNullOrBlank() ||
        destination.plannedArrival.isNullOrBlank()
    ) return false

    val departure = option.departureEpochMillis ?: return false
    val arrival = option.arrivalEpochMillis ?: return false
    if (departure <= 0L || arrival < departure) return false

    return option.origin.latitude == boarding.latitude &&
        option.origin.longitude == boarding.longitude &&
        option.destination.latitude == destination.latitude &&
        option.destination.longitude == destination.longitude
}

/** Map resolver failures to stable, typed user-actionable conflicts. */
internal fun selectedTripConflictReason(
    failure: GtfsTripDetailFailure,
): SelectedTripConflictReason = when (failure) {
    GtfsTripDetailFailure.INDEX_NOT_READY -> SelectedTripConflictReason.INDEX_NOT_READY
    GtfsTripDetailFailure.DATA_READ_FAILED,
    GtfsTripDetailFailure.SESSION_CLOSED -> SelectedTripConflictReason.DATA_UNAVAILABLE
    GtfsTripDetailFailure.TRIP_NOT_FOUND -> SelectedTripConflictReason.TRIP_UNAVAILABLE
    GtfsTripDetailFailure.TRIP_CHANGED -> SelectedTripConflictReason.IDENTITY_DRIFT
    GtfsTripDetailFailure.SERVICE_INACTIVE -> SelectedTripConflictReason.SERVICE_INACTIVE
    GtfsTripDetailFailure.FREQUENCY_INSTANCE_UNSUPPORTED ->
        SelectedTripConflictReason.FREQUENCY_INSTANCE_UNSUPPORTED
    GtfsTripDetailFailure.MISSING_OR_TRUNCATED_SEQUENCE,
    GtfsTripDetailFailure.INVALID_STOP_SEQUENCE,
    GtfsTripDetailFailure.INVALID_STOP_TIME,
    GtfsTripDetailFailure.MISSING_STOP_METADATA ->
        SelectedTripConflictReason.MISSING_SCHEDULE_EVIDENCE
}

/**
 * Pure, deterministic planner seam. The production planner resolves the current
 * service-day detail first, then this function enforces the selected identity
 * and exact stop occurrences without consulting the wall clock or substituting.
 */
internal fun planSelectedTripFromDetail(
    detail: GtfsTripDetail,
    constraint: SelectedTripConstraint,
    mapper: StaticRouteOptionMapper,
    sourceUpdatedAtEpochSeconds: Long,
): SelectedTripPlanningOutcome {
    selectedTripConstraintInputConflict(constraint)?.let {
        return SelectedTripPlanningOutcome.Conflict(it)
    }
    if (detail.identity != constraint.identity) {
        return SelectedTripPlanningOutcome.Conflict(
            SelectedTripConflict(SelectedTripConflictReason.IDENTITY_DRIFT)
        )
    }

    val roleResult = TripStopRoleValidator.validate(
        detail,
        TripStopRoles(
            boardingSequence = constraint.boarding.sequence,
            destinationSequence = constraint.destination.sequence,
            // This slice has no alarm-target semantics; the destination is the
            // explicit arrival boundary for the selected itinerary.
            alarmTargetSequence = constraint.destination.sequence,
        ),
    )
    val accepted = roleResult as? TripStopRoleResult.Accepted
        ?: return SelectedTripPlanningOutcome.Conflict(
            SelectedTripConflict(
                reason = SelectedTripConflictReason.INVALID_STOP_ROLES,
                roleProblem = (roleResult as? TripStopRoleResult.Rejected)?.problem,
            )
        )

    if (accepted.roles.boarding.stopId != constraint.boarding.stopId ||
        accepted.roles.destination.stopId != constraint.destination.stopId
    ) {
        return SelectedTripPlanningOutcome.Conflict(
            SelectedTripConflict(SelectedTripConflictReason.STOP_REFERENCE_MISMATCH)
        )
    }

    val option = mapper.mapSelectedTrip(detail, constraint, sourceUpdatedAtEpochSeconds)
        ?: return SelectedTripPlanningOutcome.Conflict(
            SelectedTripConflict(SelectedTripConflictReason.MISSING_SCHEDULE_EVIDENCE)
        )
    if (!routeOptionCarriesSelectedTripConstraint(option)) {
        return SelectedTripPlanningOutcome.Conflict(
            SelectedTripConflict(SelectedTripConflictReason.MISSING_SCHEDULE_EVIDENCE)
        )
    }
    return SelectedTripPlanningOutcome.Planned(option)
}

/** Only a fresh, positively matched cancellation can invalidate a selected static trip. */
internal fun selectedTripCancellationConflict(
    constraint: SelectedTripConstraint,
    realtime: RouteRealtimeInfo?,
): SelectedTripConflict? {
    val tripId = constraint.identity.tripId
    if (realtime?.state != RouteRealtimeState.FRESH_MATCHED ||
        tripId !in realtime.matchedTripIds ||
        tripId !in realtime.cancelledTripIds
    ) return null
    return SelectedTripConflict(SelectedTripConflictReason.CANCELLED)
}
