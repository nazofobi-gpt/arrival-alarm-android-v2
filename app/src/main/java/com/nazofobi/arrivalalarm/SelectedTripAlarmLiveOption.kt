package com.nazofobi.arrivalalarm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * UI-only projection of a freshly verified GTFS selection onto the active alarm screen.
 * Never insert this into route search results or claim realtime vehicle progress.
 */
internal fun verifiedSelectedTripLiveOption(
    restore: GtfsSelectionRestore.Verified,
): RouteOption? {
    val ready = SelectedTripAlarmArmGate.resolve(restore)
        as? SelectedTripAlarmArmDecision.Ready ?: return null
    val start = ready.roles.boarding
    val target = ready.roles.alarmTarget
    val segment = restore.detail.stops.filter { it.stopSequence in start.stopSequence..target.stopSequence }
    if (segment.size < 2 || segment.first() != start || segment.last() != target) return null
    val trip = ready.selection.identity
    return RouteOption(
        id = "verified-gtfs:${trip.tripId}:${trip.serviceDate}:${start.stopSequence}:${target.stopSequence}",
        origin = MapPoint(start.latitude, start.longitude, start.name),
        destination = MapPoint(target.latitude, target.longitude, target.name),
        line = trip.routeId,
        direction = restore.detail.headsign?.takeIf { it.isNotBlank() } ?: target.name,
        departure = start.scheduledDeparture?.take(5).orEmpty(),
        arrival = target.scheduledArrival?.take(5).orEmpty(),
        walkingMinutes = 0,
        transfers = 0,
        tripIds = listOf(trip.tripId),
        stops = segment.map { stop ->
            RouteStop(
                id = stop.stopId,
                name = stop.name,
                latitude = stop.latitude,
                longitude = stop.longitude,
                plannedArrival = gtfsAlarmClockIso(stop.scheduledArrival, trip.serviceDate),
                plannedDeparture = gtfsAlarmClockIso(stop.scheduledDeparture, trip.serviceDate),
                plannedPlatform = stop.platformCode,
            )
        },
        // Static timetable is not a live feed or position observation.
        sourceUpdatedAtEpochSeconds = null,
        realtime = null,
    )
}

/** Do not hide cancellation controls after an explicitly armed GTFS alarm. */
internal fun liveAlarmDisplayRoute(
    options: List<RouteOption>,
    selectedRouteId: String?,
    verifiedPreview: RouteOption?,
    state: JourneyUiState,
): RouteOption? =
    options.firstOrNull { it.id == selectedRouteId } ?: verifiedPreview?.takeIf {
        selectedRouteId == null &&
            (state.phase == JourneyPhase.ARMED || state.phase == JourneyPhase.ARRIVED)
    }

private fun gtfsAlarmClockIso(raw: String?, date: GtfsServiceDate): String? {
    if (raw == null) return null
    return runCatching {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val timestamp = GtfsServiceTime.parse(raw).resolve(date, berlin).epochMillis
        val compact = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply {
            timeZone = berlin
        }.format(Date(timestamp))
        compact.dropLast(2) + ":" + compact.takeLast(2)
    }.getOrNull()
}
