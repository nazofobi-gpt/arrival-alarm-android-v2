package com.nazofobi.arrivalalarm

enum class SelectedTripPlannerConflictReason {
    MISSING_CANCELLATION_EVIDENCE,
    SELECTED_TRIP_CANCELLED,
    IDENTITY_DRIFT,
    TRIP_UNAVAILABLE,
    FREQUENCY_INSTANCE_UNSUPPORTED,
    STOP_REFERENCE_CHANGED,
    INFEASIBLE_STOP_ROLES,
    MISSING_SCHEDULE_EVIDENCE,
    INVALID_ENDPOINT_COORDINATES,
}

enum class SelectedTripAlternativeAction {
    REFRESH_REALTIME_EVIDENCE,
    SELECT_ANOTHER_TRIP,
    RESELECT_TRIP_AND_STOPS,
    SELECT_SCHEDULED_TRIP,
    RESELECT_BOARDING_AND_DESTINATION,
    REFRESH_TIMETABLE_AND_RESELECT,
    CORRECT_ROUTE_ENDPOINTS,
}

sealed interface SelectedTripPlannerOutcome {
    data class Selected(
        val selection: GtfsSelectedTrip,
        val roles: ConfirmedTripStopRoles,
        val option: RouteOption,
        val sourceUpdatedAtEpochSeconds: Long,
    ) : SelectedTripPlannerOutcome

    data class Conflict(
        val selection: GtfsSelectedTrip,
        val reason: SelectedTripPlannerConflictReason,
        val alternativeAction: SelectedTripAlternativeAction,
        val alternatives: List<RouteOption>,
        val detailFailure: GtfsTripDetailFailure? = null,
        val roleProblem: TripStopRoleProblem? = null,
    ) : SelectedTripPlannerOutcome
}

internal sealed interface SelectedTripPlannerValidation {
    data class Accepted(val roles: ConfirmedTripStopRoles) : SelectedTripPlannerValidation

    data class Rejected(
        val reason: SelectedTripPlannerConflictReason,
        val alternativeAction: SelectedTripAlternativeAction,
        val roleProblem: TripStopRoleProblem? = null,
    ) : SelectedTripPlannerValidation
}

internal object SelectedTripPlannerConstraint {
    fun hasValidCancellationEvidence(evidence: SelectedTripCancellationEvidence?): Boolean =
        evidence != null &&
            evidence.snapshotUpdatedAtEpochSeconds > 0L &&
            evidence.cancelledTripInstances.none {
                it.tripId.isBlank() || it.serviceId.isBlank() || it.routeId.isBlank()
            }

    fun validate(
        selection: GtfsSelectedTrip,
        detail: GtfsTripDetail,
        cancellationEvidence: SelectedTripCancellationEvidence?,
    ): SelectedTripPlannerValidation {
        fun reject(
            reason: SelectedTripPlannerConflictReason,
            action: SelectedTripAlternativeAction,
            problem: TripStopRoleProblem? = null,
        ) = SelectedTripPlannerValidation.Rejected(reason, action, problem)

        val evidence = cancellationEvidence
        if (!hasValidCancellationEvidence(evidence)) {
            return reject(
                SelectedTripPlannerConflictReason.MISSING_CANCELLATION_EVIDENCE,
                SelectedTripAlternativeAction.REFRESH_REALTIME_EVIDENCE,
            )
        }
        if (selection.identity in evidence!!.cancelledTripInstances) {
            return reject(
                SelectedTripPlannerConflictReason.SELECTED_TRIP_CANCELLED,
                SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP,
            )
        }
        if (selection.identity.frequencyStartSeconds != null) {
            return reject(
                SelectedTripPlannerConflictReason.FREQUENCY_INSTANCE_UNSUPPORTED,
                SelectedTripAlternativeAction.SELECT_SCHEDULED_TRIP,
            )
        }
        if (detail.identity != selection.identity) {
            return reject(
                SelectedTripPlannerConflictReason.IDENTITY_DRIFT,
                SelectedTripAlternativeAction.RESELECT_TRIP_AND_STOPS,
            )
        }

        fun matches(reference: GtfsSelectedStopRef): Boolean =
            detail.stops.singleOrNull {
                it.stopSequence == reference.sequence && it.stopId == reference.stopId
            } != null

        if (!matches(selection.boarding) ||
            !matches(selection.destination) ||
            !matches(selection.alarmTarget)
        ) {
            return reject(
                SelectedTripPlannerConflictReason.STOP_REFERENCE_CHANGED,
                SelectedTripAlternativeAction.RESELECT_TRIP_AND_STOPS,
            )
        }

        return when (
            val result = TripStopRoleValidator.validate(
                detail,
                TripStopRoles(
                    boardingSequence = selection.boarding.sequence,
                    destinationSequence = selection.destination.sequence,
                    alarmTargetSequence = selection.alarmTarget.sequence,
                ),
            )
        ) {
            is TripStopRoleResult.Accepted ->
                SelectedTripPlannerValidation.Accepted(result.roles)
            is TripStopRoleResult.Rejected ->
                reject(
                    SelectedTripPlannerConflictReason.INFEASIBLE_STOP_ROLES,
                    SelectedTripAlternativeAction.RESELECT_BOARDING_AND_DESTINATION,
                    result.problem,
                )
        }
    }

    fun safeAlternatives(
        options: List<RouteOption>,
        selection: GtfsSelectedTrip,
        cancellationEvidence: SelectedTripCancellationEvidence?,
    ): List<RouteOption> {
        if (!hasValidCancellationEvidence(cancellationEvidence)) return emptyList()
        val cancelledTripIds = cancellationEvidence!!.cancelledTripInstances
            .mapTo(mutableSetOf()) { it.tripId }
        return options.filter { option ->
            option.tripIds.isNotEmpty() &&
                option.tripIds.none {
                    it == selection.identity.tripId || it in cancelledTripIds
                }
        }
    }
}
