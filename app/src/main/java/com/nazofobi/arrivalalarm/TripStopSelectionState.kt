package com.nazofobi.arrivalalarm

/**
 * Pure, immutable state for selecting an ordered boarding/destination segment
 * on one concrete GTFS TripInstance. Stop references include both sequence
 * and stop ID so repeated loop stops cannot silently alias one another.
 */
internal data class TripStopSelectionState(
    val detail: GtfsTripDetail,
    val boarding: GtfsSelectedStopRef,
    val destination: GtfsSelectedStopRef? = null,
    val alarmTarget: GtfsSelectedStopRef? = null,
    val roleProblem: TripStopRoleProblem? = null,
)

internal sealed interface TripStopSelectionAction {
    data class ReplaceTrip(
        val detail: GtfsTripDetail,
        val boarding: GtfsTripDetailStop,
    ) : TripStopSelectionAction

    data class SelectDestination(val sequence: Int?) : TripStopSelectionAction
    data class SelectAlarmTarget(val sequence: Int?) : TripStopSelectionAction
}

/**
 * Reducer for the consent panel's stop-role state. It never persists anything:
 * only AndroidSelectedGtfsTripSession.confirm() may write the selected trip.
 */
internal object TripStopSelectionReducer {
    fun initial(
        detail: GtfsTripDetail,
        boarding: GtfsTripDetailStop,
    ): TripStopSelectionState {
        val boardingRef = ref(boarding)
        val problem = if (resolveStop(detail, boardingRef) == null) {
            TripStopRoleProblem.BOARDING_STOP_NOT_FOUND
        } else {
            null
        }
        return TripStopSelectionState(
            detail = detail,
            boarding = boardingRef,
            roleProblem = problem,
        )
    }

    fun reduce(
        state: TripStopSelectionState,
        action: TripStopSelectionAction,
    ): TripStopSelectionState = when (action) {
        is TripStopSelectionAction.ReplaceTrip ->
            replaceTrip(state, action.detail, action.boarding)
        is TripStopSelectionAction.SelectDestination ->
            selectDestination(state, action.sequence)
        is TripStopSelectionAction.SelectAlarmTarget ->
            selectAlarmTarget(state, action.sequence)
    }

    fun destinationStop(state: TripStopSelectionState): GtfsTripDetailStop? =
        state.destination?.let { resolveStop(state.detail, it) }

    fun alarmTargetStop(state: TripStopSelectionState): GtfsTripDetailStop? {
        val selected = state.alarmTarget
        return if (selected == null) destinationStop(state) else resolveStop(state.detail, selected)
    }

    fun draft(state: TripStopSelectionState): GtfsTripConsentDraft {
        state.roleProblem?.let {
            return GtfsTripConsentDraft.Rejected(
                GtfsTripConsentFailure.INVALID_STOP_ROLES,
                it,
            )
        }
        val boarding = resolveStop(state.detail, state.boarding)
            ?: return rejected(TripStopRoleProblem.BOARDING_STOP_NOT_FOUND)
        val destinationRef = state.destination
            ?: return rejected(TripStopRoleProblem.DESTINATION_STOP_NOT_FOUND)
        val destination = resolveStop(state.detail, destinationRef)
            ?: return rejected(TripStopRoleProblem.DESTINATION_STOP_NOT_FOUND)
        val alarm = if (state.alarmTarget == null) {
            destination
        } else {
            resolveStop(state.detail, state.alarmTarget)
                ?: return rejected(TripStopRoleProblem.ALARM_STOP_NOT_FOUND)
        }
        return buildGtfsTripConsentDraft(
            detail = state.detail,
            boardingSequence = boarding.stopSequence,
            destinationSequence = destination.stopSequence,
            alarmTargetSequence = alarm.stopSequence,
        )
    }

    private fun selectDestination(
        state: TripStopSelectionState,
        sequence: Int?,
    ): TripStopSelectionState {
        if (sequence == null) {
            return state.copy(destination = null, alarmTarget = null, roleProblem = null)
        }
        val stop = state.detail.stops.singleOrNull { it.stopSequence == sequence }
            ?: return state.copy(
                destination = null,
                alarmTarget = null,
                roleProblem = TripStopRoleProblem.DESTINATION_STOP_NOT_FOUND,
            )
        val candidate = state.copy(
            destination = ref(stop),
            alarmTarget = null,
            roleProblem = null,
        )
        val draft = draft(candidate)
        return if (draft is GtfsTripConsentDraft.Rejected) {
            candidate.copy(roleProblem = draft.roleProblem)
        } else {
            candidate
        }
    }

    private fun selectAlarmTarget(
        state: TripStopSelectionState,
        sequence: Int?,
    ): TripStopSelectionState {
        if (sequence == null) {
            return state.copy(alarmTarget = null, roleProblem = null)
        }
        val stop = state.detail.stops.singleOrNull { it.stopSequence == sequence }
            ?: return state.copy(
                alarmTarget = null,
                roleProblem = TripStopRoleProblem.ALARM_STOP_NOT_FOUND,
            )
        val candidate = state.copy(alarmTarget = ref(stop), roleProblem = null)
        val draft = draft(candidate)
        return if (draft is GtfsTripConsentDraft.Rejected) {
            candidate.copy(roleProblem = draft.roleProblem)
        } else {
            candidate
        }
    }

    private fun replaceTrip(
        state: TripStopSelectionState,
        detail: GtfsTripDetail,
        boarding: GtfsTripDetailStop,
    ): TripStopSelectionState {
        val boardingRef = ref(boarding)
        if (state.detail.identity != detail.identity || state.boarding != boardingRef) {
            return initial(detail, boarding)
        }
        val destinationRef = state.destination
            ?: return initial(detail, boarding)
        val destination = resolveStop(detail, destinationRef)
            ?: return initial(detail, boarding).copy(
                roleProblem = TripStopRoleProblem.DESTINATION_STOP_NOT_FOUND,
            )
        val alarm = if (state.alarmTarget == null) {
            destination
        } else {
            resolveStop(detail, state.alarmTarget)
                ?: return initial(detail, boarding).copy(
                    roleProblem = TripStopRoleProblem.ALARM_STOP_NOT_FOUND,
                )
        }
        val resolvedBoarding = resolveStop(detail, boardingRef)
            ?: return initial(detail, boarding).copy(
                roleProblem = TripStopRoleProblem.BOARDING_STOP_NOT_FOUND,
            )
        return when (
            val result = TripStopRoleValidator.validate(
                detail,
                TripStopRoles(
                    boardingSequence = resolvedBoarding.stopSequence,
                    destinationSequence = destination.stopSequence,
                    alarmTargetSequence = alarm.stopSequence,
                ),
            )
        ) {
            is TripStopRoleResult.Accepted -> state.copy(detail = detail, roleProblem = null)
            is TripStopRoleResult.Rejected -> initial(detail, boarding).copy(
                roleProblem = result.problem,
            )
        }
    }

    private fun rejected(problem: TripStopRoleProblem) = GtfsTripConsentDraft.Rejected(
        GtfsTripConsentFailure.INVALID_STOP_ROLES,
        problem,
    )

    private fun resolveStop(
        detail: GtfsTripDetail,
        ref: GtfsSelectedStopRef,
    ): GtfsTripDetailStop? = detail.stops.singleOrNull {
        it.stopSequence == ref.sequence && it.stopId == ref.stopId
    }

    private fun ref(stop: GtfsTripDetailStop) =
        GtfsSelectedStopRef(stop.stopSequence, stop.stopId)
}
