package com.nazofobi.arrivalalarm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A failed explicit trip selection never falls through to a different journey search. */
internal enum class SelectedTripPlanFailure {
    INDEX_NOT_READY,
    DATA_READ_FAILED,
    TRIP_NOT_FOUND,
    TRIP_IDENTITY_DRIFT,
    SERVICE_INACTIVE,
    FREQUENCY_INSTANCE_UNSUPPORTED,
    MISSING_OR_TRUNCATED_SEQUENCE,
    INVALID_STOP_SEQUENCE,
    INVALID_STOP_TIME,
    MISSING_STOP_METADATA,
    SESSION_CLOSED,
    INVALID_STOP_ROLES,
    STOP_REFERENCE_CHANGED,
    TRIP_CANCELLED,
    ROUTE_NOT_FOUND,
    ROUTE_IDENTITY_DRIFT,
    SOURCE_FRESHNESS_MISSING,
    MISSING_SCHEDULE_EVIDENCE,
    INVALID_SCHEDULE_EVIDENCE,
}

internal enum class SelectedTripAlternativeAction {
    SELECT_ANOTHER_TRIP,
}

/**
 * The selected trip identity and all ordered stop roles travel with the planned
 * route. A conflict always gives the caller an explicit next action; it never
 * silently invokes the generic route search or substitutes another departure.
 */
internal sealed interface SelectedTripPlanOutcome {
    data class Planned(
        val selection: GtfsSelectedTrip,
        val routeOption: RouteOption,
    ) : SelectedTripPlanOutcome

    data class Conflict(
        val reason: SelectedTripPlanFailure,
        val alternativeAction: SelectedTripAlternativeAction =
            SelectedTripAlternativeAction.SELECT_ANOTHER_TRIP,
        val roleProblem: TripStopRoleProblem? = null,
    ) : SelectedTripPlanOutcome
}

/**
 * Plans only the rider-confirmed GTFS trip and stop segment. Static truth is
 * re-resolved for the exact service date before the itinerary is constructed.
 * Realtime is an overlay: only a fresh matched cancellation invalidates the
 * selected trip; stale/unavailable realtime does not replace static truth.
 */
internal class SelectedTripItineraryPlanner(
    private val indexReady: () -> Boolean,
    private val resolveDetail: (GtfsTripInstanceIdentity) -> GtfsTripDetailResult,
    private val routeLookup: (String) -> GtfsScheduleRoute?,
    private val sourceUpdatedAtEpochSeconds: () -> Long,
    private val agencyTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Berlin"),
) {
    fun plan(
        selection: GtfsSelectedTrip,
        realtime: RouteRealtimeInfo? = null,
    ): SelectedTripPlanOutcome {
        fun conflict(
            reason: SelectedTripPlanFailure,
            roleProblem: TripStopRoleProblem? = null,
        ) = SelectedTripPlanOutcome.Conflict(reason, roleProblem = roleProblem)

        val identity = selection.identity
        if (identity.frequencyStartSeconds != null) {
            return conflict(SelectedTripPlanFailure.FREQUENCY_INSTANCE_UNSUPPORTED)
        }

        val ready = try {
            indexReady()
        } catch (_: Exception) {
            return conflict(SelectedTripPlanFailure.DATA_READ_FAILED)
        }
        if (!ready) return conflict(SelectedTripPlanFailure.INDEX_NOT_READY)

        val detail = when (
            val resolved = try {
                resolveDetail(identity)
            } catch (_: Exception) {
                return conflict(SelectedTripPlanFailure.DATA_READ_FAILED)
            }
        ) {
            is GtfsTripDetailResult.Available -> resolved.detail
            is GtfsTripDetailResult.Unavailable -> {
                return conflict(resolved.reason.toPlanFailure())
            }
        }
        if (detail.identity != identity) {
            return conflict(SelectedTripPlanFailure.TRIP_IDENTITY_DRIFT)
        }

        val roleResult = TripStopRoleValidator.validate(
            detail,
            TripStopRoles(
                boardingSequence = selection.boarding.sequence,
                destinationSequence = selection.destination.sequence,
                alarmTargetSequence = selection.alarmTarget.sequence,
            ),
        )
        val roles = (roleResult as? TripStopRoleResult.Accepted)?.roles
            ?: return conflict(
                SelectedTripPlanFailure.INVALID_STOP_ROLES,
                (roleResult as? TripStopRoleResult.Rejected)?.problem,
            )

        if (roles.identity != identity) {
            return conflict(SelectedTripPlanFailure.TRIP_IDENTITY_DRIFT)
        }
        if (
            roles.boarding.stopId != selection.boarding.stopId ||
            roles.destination.stopId != selection.destination.stopId ||
            roles.alarmTarget.stopId != selection.alarmTarget.stopId
        ) {
            return conflict(SelectedTripPlanFailure.STOP_REFERENCE_CHANGED)
        }

        if (
            realtime?.state == RouteRealtimeState.FRESH_MATCHED &&
            identity.tripId in realtime.cancelledTripIds
        ) {
            return conflict(SelectedTripPlanFailure.TRIP_CANCELLED)
        }

        val route = try {
            routeLookup(identity.routeId)
        } catch (_: Exception) {
            return conflict(SelectedTripPlanFailure.DATA_READ_FAILED)
        } ?: return conflict(SelectedTripPlanFailure.ROUTE_NOT_FOUND)
        if (route.id != identity.routeId) {
            return conflict(SelectedTripPlanFailure.ROUTE_IDENTITY_DRIFT)
        }

        val sourceUpdatedAt = try {
            sourceUpdatedAtEpochSeconds()
        } catch (_: Exception) {
            return conflict(SelectedTripPlanFailure.DATA_READ_FAILED)
        }
        if (sourceUpdatedAt <= 0L) {
            return conflict(SelectedTripPlanFailure.SOURCE_FRESHNESS_MISSING)
        }

        val departureRaw = roles.boarding.scheduledDeparture
            ?: return conflict(SelectedTripPlanFailure.MISSING_SCHEDULE_EVIDENCE)
        val arrivalRaw = roles.destination.scheduledArrival
            ?: return conflict(SelectedTripPlanFailure.MISSING_SCHEDULE_EVIDENCE)
        val departureMillis = serviceEpochMillis(departureRaw, identity.serviceDate)
            ?: return conflict(SelectedTripPlanFailure.INVALID_SCHEDULE_EVIDENCE)
        val arrivalMillis = serviceEpochMillis(arrivalRaw, identity.serviceDate)
            ?: return conflict(SelectedTripPlanFailure.INVALID_SCHEDULE_EVIDENCE)
        if (arrivalMillis < departureMillis) {
            return conflict(SelectedTripPlanFailure.INVALID_SCHEDULE_EVIDENCE)
        }

        val segmentStops = detail.stops.filter {
            it.stopSequence >= roles.boarding.stopSequence &&
                it.stopSequence <= roles.destination.stopSequence
        }
        if (
            segmentStops.size < 2 ||
            segmentStops.first() != roles.boarding ||
            segmentStops.last() != roles.destination
        ) {
            return conflict(SelectedTripPlanFailure.MISSING_SCHEDULE_EVIDENCE)
        }

        val routeStops = mutableListOf<RouteStop>()
        for (stop in segmentStops) {
            val plannedArrival = stop.scheduledArrival?.let {
                serviceIsoTime(it, identity.serviceDate)
                    ?: return conflict(SelectedTripPlanFailure.INVALID_SCHEDULE_EVIDENCE)
            }
            val plannedDeparture = stop.scheduledDeparture?.let {
                serviceIsoTime(it, identity.serviceDate)
                    ?: return conflict(SelectedTripPlanFailure.INVALID_SCHEDULE_EVIDENCE)
            }
            routeStops += RouteStop(
                id = stop.stopId,
                name = stop.name,
                latitude = stop.latitude,
                longitude = stop.longitude,
                plannedArrival = plannedArrival,
                plannedDeparture = plannedDeparture,
                plannedPlatform = stop.platformCode,
            )
        }

        val line = route.shortName?.takeIf { it.isNotBlank() }
            ?: route.longName?.takeIf { it.isNotBlank() }
            ?: route.id
        val direction = detail.headsign?.takeIf { it.isNotBlank() }
            ?: roles.destination.name
        val routeOption = RouteOption(
            id = stableOptionId(selection),
            origin = MapPoint(
                roles.boarding.latitude,
                roles.boarding.longitude,
                roles.boarding.name,
            ),
            destination = MapPoint(
                roles.destination.latitude,
                roles.destination.longitude,
                roles.destination.name,
            ),
            line = line,
            direction = direction,
            departure = displayClock(departureMillis),
            arrival = displayClock(arrivalMillis),
            walkingMinutes = 0,
            transfers = 0,
            tripIds = listOf(identity.tripId),
            stops = routeStops,
            refreshToken = null,
            sourceUpdatedAtEpochSeconds = sourceUpdatedAt,
            realtime = realtime,
            departureEpochMillis = departureMillis,
            arrivalEpochMillis = arrivalMillis,
            accessWalking = null,
            egressWalking = null,
        )
        return SelectedTripPlanOutcome.Planned(selection, routeOption)
    }

    private fun serviceEpochMillis(raw: String, serviceDate: GtfsServiceDate): Long? =
        try {
            GtfsServiceTime.parse(raw).resolve(serviceDate, agencyTimeZone).epochMillis
        } catch (_: RuntimeException) {
            null
        }

    private fun serviceIsoTime(raw: String, serviceDate: GtfsServiceDate): String? {
        val epochMillis = serviceEpochMillis(raw, serviceDate) ?: return null
        val offsetWithoutColon = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(Date(epochMillis))
        return offsetWithoutColon.dropLast(2) + ":" + offsetWithoutColon.takeLast(2)
    }

    private fun displayClock(epochMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(Date(epochMillis))

    private fun stableOptionId(selection: GtfsSelectedTrip): String {
        val identity = selection.identity
        val parts = listOf(
            identity.tripId,
            identity.serviceDate.toString(),
            identity.serviceId,
            identity.routeId,
            identity.directionId?.toString().orEmpty(),
            identity.frequencyStartSeconds?.toString().orEmpty(),
            selection.boarding.sequence.toString(),
            selection.boarding.stopId,
            selection.destination.sequence.toString(),
            selection.destination.stopId,
            selection.alarmTarget.sequence.toString(),
            selection.alarmTarget.stopId,
        )
        return "selected-trip:" + parts.joinToString(separator = "") { "${it.length}:$it" }
    }

    private fun GtfsTripDetailFailure.toPlanFailure(): SelectedTripPlanFailure = when (this) {
        GtfsTripDetailFailure.INDEX_NOT_READY -> SelectedTripPlanFailure.INDEX_NOT_READY
        GtfsTripDetailFailure.DATA_READ_FAILED -> SelectedTripPlanFailure.DATA_READ_FAILED
        GtfsTripDetailFailure.TRIP_NOT_FOUND -> SelectedTripPlanFailure.TRIP_NOT_FOUND
        GtfsTripDetailFailure.TRIP_CHANGED -> SelectedTripPlanFailure.TRIP_IDENTITY_DRIFT
        GtfsTripDetailFailure.SERVICE_INACTIVE -> SelectedTripPlanFailure.SERVICE_INACTIVE
        GtfsTripDetailFailure.FREQUENCY_INSTANCE_UNSUPPORTED ->
            SelectedTripPlanFailure.FREQUENCY_INSTANCE_UNSUPPORTED
        GtfsTripDetailFailure.MISSING_OR_TRUNCATED_SEQUENCE ->
            SelectedTripPlanFailure.MISSING_OR_TRUNCATED_SEQUENCE
        GtfsTripDetailFailure.INVALID_STOP_SEQUENCE -> SelectedTripPlanFailure.INVALID_STOP_SEQUENCE
        GtfsTripDetailFailure.INVALID_STOP_TIME -> SelectedTripPlanFailure.INVALID_STOP_TIME
        GtfsTripDetailFailure.MISSING_STOP_METADATA -> SelectedTripPlanFailure.MISSING_STOP_METADATA
        GtfsTripDetailFailure.SESSION_CLOSED -> SelectedTripPlanFailure.SESSION_CLOSED
    }
}
