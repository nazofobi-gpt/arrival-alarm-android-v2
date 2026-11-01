package com.nazofobi.arrivalalarm

import kotlin.math.abs

/**
 * The selected trip's alarm target must be the destination actually used by
 * JourneyController. A saved GTFS selection is not enough on its own: resolve
 * it against current static truth and compare the active journey immediately
 * before the alarm command is authorized.
 */
internal sealed interface SelectedTripAlarmArmDecision {
    object ManualJourney : SelectedTripAlarmArmDecision
    data class Ready(
        val selection: GtfsSelectedTrip,
        val roles: ConfirmedTripStopRoles,
    ) : SelectedTripAlarmArmDecision
    data class Blocked(val problem: SelectedTripAlarmArmProblem) : SelectedTripAlarmArmDecision
}

internal enum class SelectedTripAlarmArmProblem {
    RESTORE_REJECTED,
    IDENTITY_MISMATCH,
    INVALID_STOP_ROLES,
    BOARDING_REFERENCE_MISMATCH,
    DESTINATION_REFERENCE_MISMATCH,
    ALARM_TARGET_REFERENCE_MISMATCH,
    JOURNEY_NOT_READY,
    JOURNEY_ORIGIN_MISMATCH,
    JOURNEY_ALARM_TARGET_MISMATCH,
}

internal object SelectedTripAlarmArmGate {
    fun resolve(restore: GtfsSelectionRestore): SelectedTripAlarmArmDecision =
        when (restore) {
            GtfsSelectionRestore.Empty -> SelectedTripAlarmArmDecision.ManualJourney
            is GtfsSelectionRestore.Rejected ->
                SelectedTripAlarmArmDecision.Blocked(SelectedTripAlarmArmProblem.RESTORE_REJECTED)
            is GtfsSelectionRestore.Verified -> {
                val selection = restore.selection
                val detail = restore.detail
                if (detail.identity != selection.identity) {
                    return SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.IDENTITY_MISMATCH
                    )
                }
                val validation = try {
                    TripStopRoleValidator.validate(
                        detail,
                        TripStopRoles(
                            boardingSequence = selection.boarding.sequence,
                            destinationSequence = selection.destination.sequence,
                            alarmTargetSequence = selection.alarmTarget.sequence,
                        ),
                    )
                } catch (_: RuntimeException) {
                    null
                }
                val roles = (validation as? TripStopRoleResult.Accepted)?.roles
                    ?: return SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.INVALID_STOP_ROLES
                    )
                if (roles.identity != selection.identity) {
                    return SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.IDENTITY_MISMATCH
                    )
                }
                if (roles.boarding.stopId != selection.boarding.stopId) {
                    return SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.BOARDING_REFERENCE_MISMATCH
                    )
                }
                if (roles.destination.stopId != selection.destination.stopId) {
                    return SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.DESTINATION_REFERENCE_MISMATCH
                    )
                }
                if (roles.alarmTarget.stopId != selection.alarmTarget.stopId) {
                    return SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.ALARM_TARGET_REFERENCE_MISMATCH
                    )
                }
                SelectedTripAlarmArmDecision.Ready(selection, roles)
            }
        }

    fun evaluate(
        restore: GtfsSelectionRestore,
        journey: JourneyUiState,
    ): SelectedTripAlarmArmDecision =
        when (val resolved = resolve(restore)) {
            SelectedTripAlarmArmDecision.ManualJourney ->
                SelectedTripAlarmArmDecision.ManualJourney
            is SelectedTripAlarmArmDecision.Blocked -> resolved
            is SelectedTripAlarmArmDecision.Ready -> {
                if (journey.phase != JourneyPhase.DESTINATION_SELECTED) {
                    SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.JOURNEY_NOT_READY
                    )
                } else if (!journey.start.matches(resolved.roles.boarding)) {
                    SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.JOURNEY_ORIGIN_MISMATCH
                    )
                } else if (!journey.destination.matches(resolved.roles.alarmTarget)) {
                    SelectedTripAlarmArmDecision.Blocked(
                        SelectedTripAlarmArmProblem.JOURNEY_ALARM_TARGET_MISMATCH
                    )
                } else {
                    resolved
                }
            }
        }

    private fun GeoPoint?.matches(stop: GtfsTripDetailStop): Boolean {
        val point = this ?: return false
        return abs(point.latitude - stop.latitude) <= COORDINATE_TOLERANCE &&
            abs(point.longitude - stop.longitude) <= COORDINATE_TOLERANCE
    }

    private const val COORDINATE_TOLERANCE = 0.000001
}
