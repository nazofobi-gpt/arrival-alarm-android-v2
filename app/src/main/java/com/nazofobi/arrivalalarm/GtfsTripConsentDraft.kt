package com.nazofobi.arrivalalarm

/**
 * UI confirmation boundary for a source-verified, concrete GTFS service-day trip.
 * A board tap or preview must never call this automatically: boarding and
 * destination require an explicit user action. Building the draft is pure;
 * only AndroidSelectedGtfsTripSession.confirm() may subsequently persist it.
 */
internal sealed interface GtfsTripConsentDraft {
    data class Ready(val selection: GtfsSelectedTrip) : GtfsTripConsentDraft
    data class Rejected(
        val reason: GtfsTripConsentFailure,
        val roleProblem: TripStopRoleProblem? = null,
    ) : GtfsTripConsentDraft
}

internal enum class GtfsTripConsentFailure {
    FREQUENCY_INSTANCE_UNSUPPORTED,
    INVALID_STOP_ROLES,
}

/**
 * The destination is the default arrival-alarm target only after the rider
 * explicitly confirms a boarding and destination stop. An override must stay
 * within that segment; no other service-day or trip can be substituted.
 */
internal fun buildGtfsTripConsentDraft(
    detail: GtfsTripDetail,
    boardingSequence: Int,
    destinationSequence: Int,
    alarmTargetSequence: Int? = null,
): GtfsTripConsentDraft {
    if (detail.identity.frequencyStartSeconds != null) {
        return GtfsTripConsentDraft.Rejected(
            GtfsTripConsentFailure.FREQUENCY_INSTANCE_UNSUPPORTED,
        )
    }
    val result = TripStopRoleValidator.validate(
        detail,
        TripStopRoles(
            boardingSequence = boardingSequence,
            destinationSequence = destinationSequence,
            alarmTargetSequence = alarmTargetSequence ?: destinationSequence,
        ),
    )
    return when (result) {
        is TripStopRoleResult.Rejected -> GtfsTripConsentDraft.Rejected(
            GtfsTripConsentFailure.INVALID_STOP_ROLES,
            result.problem,
        )
        is TripStopRoleResult.Accepted -> {
            val roles = result.roles
            fun ref(stop: GtfsTripDetailStop) =
                GtfsSelectedStopRef(stop.stopSequence, stop.stopId)
            GtfsTripConsentDraft.Ready(
                GtfsSelectedTrip(
                    identity = roles.identity,
                    boarding = ref(roles.boarding),
                    destination = ref(roles.destination),
                    alarmTarget = ref(roles.alarmTarget),
                )
            )
        }
    }
}
