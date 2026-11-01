package com.nazofobi.arrivalalarm

import java.util.TimeZone

fun interface SelectedTripAlternativePlanner {
    fun plan(
        origin: MapPoint,
        destination: MapPoint,
        limit: Int,
    ): LocalStaticJourneyOutcome
}

class SelectedTripPlanner(
    private val resolveDetail: (GtfsTripInstanceIdentity) -> GtfsTripDetailResult,
    private val sourceUpdatedAtEpochSeconds: () -> Long,
    private val routeLabel: (String) -> String,
    private val alternativesPlanner: SelectedTripAlternativePlanner,
    private val timeZone: TimeZone,
    private val walkingMetersPerSecond: Double,
) {
    private val optionMapper = SelectedTripRouteOptionMapper(
        agencyTimeZone = timeZone,
        walkingMetersPerSecond = walkingMetersPerSecond,
    )

    fun plan(
        origin: MapPoint,
        destination: MapPoint,
        selection: GtfsSelectedTrip,
        cancellationEvidence: SelectedTripCancellationEvidence?,
        limit: Int = 3,
    ): SelectedTripPlannerOutcome {
        fun alternatives(): List<RouteOption> {
            val evidence = cancellationEvidence ?: return emptyList()
            if (evidence.snapshotUpdatedAtEpochSeconds <= 0L ||
                evidence.cancelledTripIds.any { it.isBlank() }
            ) return emptyList()
            val planned = runCatching {
                alternativesPlanner.plan(origin, destination, limit)
            }.getOrNull() as? LocalStaticJourneyOutcome.Results ?: return emptyList()
            return SelectedTripPlannerConstraint.safeAlternatives(
                planned.options,
                selection,
                evidence,
            )
        }

        fun conflict(
            reason: SelectedTripPlannerConflictReason,
            action: SelectedTripAlternativeAction,
            detailFailure: GtfsTripDetailFailure? = null,
            roleProblem: TripStopRoleProblem? = null,
        ) = SelectedTripPlannerOutcome.Conflict(
            selection = selection,
            reason = reason,
            alternativeAction = action,
            alternatives = alternatives(),
            detailFailure = detailFailure,
            roleProblem = roleProblem,
        )

        if (!validCoordinates(origin.latitude, origin.longitude) ||
            !validCoordinates(destination.latitude, destination.longitude)
        ) {
            return conflict(
                SelectedTripPlannerConflictReason.INVALID_ENDPOINT_COORDINATES,
                SelectedTripAlternativeAction.CORRECT_ROUTE_ENDPOINTS,
            )
        }

        val evidence = cancellationEvidence
        if (evidence == null || evidence.snapshotUpdatedAtEpochSeconds <= 0L ||
            evidence.cancelledTripIds.any { it.isBlank() }
        ) {
            return conflict(
                SelectedTripPlannerConflictReason.MISSING_CANCELLATION_EVIDENCE,
                SelectedTripAlternativeAction.REFRESH_REALTIME_EVIDENCE,
            )
        }
        if (selection.identity.tripId in evidence.cancelledTripIds) {
            return conflict(
                SelectedTripPlannerConflictReason.SELECTED_TRIP_CANCELLED,
                SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP,
            )
        }

        val resolved = runCatching {
            resolveDetail(selection.identity)
        }.getOrElse {
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.DATA_READ_FAILED)
        }
        val detail = when (resolved) {
            is GtfsTripDetailResult.Available -> resolved.detail
            is GtfsTripDetailResult.Unavailable -> {
                val (reason, action) = when (resolved.reason) {
                    GtfsTripDetailFailure.TRIP_CHANGED ->
                        SelectedTripPlannerConflictReason.IDENTITY_DRIFT to
                            SelectedTripAlternativeAction.RESELECT_TRIP_AND_STOPS
                    GtfsTripDetailFailure.TRIP_NOT_FOUND,
                    GtfsTripDetailFailure.SERVICE_INACTIVE ->
                        SelectedTripPlannerConflictReason.TRIP_UNAVAILABLE to
                            SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP
                    GtfsTripDetailFailure.FREQUENCY_INSTANCE_UNSUPPORTED ->
                        SelectedTripPlannerConflictReason.FREQUENCY_INSTANCE_UNSUPPORTED to
                            SelectedTripAlternativeAction.SELECT_SCHEDULED_TRIP
                    else ->
                        SelectedTripPlannerConflictReason.MISSING_SCHEDULE_EVIDENCE to
                            SelectedTripAlternativeAction.REFRESH_TIMETABLE_AND_RESELECT
                }
                return conflict(reason, action, detailFailure = resolved.reason)
            }
        }

        val validation = SelectedTripPlannerConstraint.validate(
            selection = selection,
            detail = detail,
            cancellationEvidence = evidence,
        )
        if (validation is SelectedTripPlannerValidation.Rejected) {
            return conflict(
                validation.reason,
                validation.alternativeAction,
                roleProblem = validation.roleProblem,
            )
        }
        val roles = (validation as SelectedTripPlannerValidation.Accepted).roles

        val sourceUpdatedAt = runCatching {
            sourceUpdatedAtEpochSeconds()
        }.getOrDefault(0L)
        if (sourceUpdatedAt <= 0L) {
            return conflict(
                SelectedTripPlannerConflictReason.MISSING_SCHEDULE_EVIDENCE,
                SelectedTripAlternativeAction.REFRESH_TIMETABLE_AND_RESELECT,
                GtfsTripDetailFailure.DATA_READ_FAILED,
            )
        }

        val line = runCatching {
            routeLabel(selection.identity.routeId)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: selection.identity.routeId
        val option = optionMapper.map(
            origin = origin,
            destination = destination,
            selection = selection,
            detail = detail,
            roles = roles,
            line = line,
            sourceUpdatedAtEpochSeconds = sourceUpdatedAt,
        ) ?: return conflict(
            SelectedTripPlannerConflictReason.MISSING_SCHEDULE_EVIDENCE,
            SelectedTripAlternativeAction.REFRESH_TIMETABLE_AND_RESELECT,
        )

        return SelectedTripPlannerOutcome.Selected(
            selection = selection,
            roles = roles,
            option = option,
            sourceUpdatedAtEpochSeconds = sourceUpdatedAt,
        )
    }

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}
