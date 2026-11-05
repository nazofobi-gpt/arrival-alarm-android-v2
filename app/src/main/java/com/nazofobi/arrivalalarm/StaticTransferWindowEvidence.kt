package com.nazofobi.arrivalalarm

/** Scheduled gap between two proven GTFS legs. Not a live connection or boarding guarantee. */
data class RouteTransferWindowEvidence(
    val arrivalStopId: String,
    val departureStopId: String,
    val arrivalStopName: String,
    val departureStopName: String,
    val plannedGapMinutes: Int,
    // Source GTFS UTC instant; absent timestamp never creates a countdown.
    val plannedDepartureEpochMillis: Long = 0L,
    // Source timetable time in the GTFS agency's zone, including DST offset.
    val plannedDepartureLocalTime: String? = null,
    // Exact public GTFS stop coordinates only, never device location.
    val walkingCoordinates: RouteTransferWalkingCoordinates? = null,
)

/**
 * Surface a timetable gap only when every transfer has consistent source identity,
 * valid named stops and a bounded, positive UTC interval. Do not infer walking time,
 * live delay, platform change, connection probability or a guaranteed interchange.
 */
internal fun plannedStaticTransferWindows(
    legs: List<StaticTransitLeg>,
    resolveStopName: (String) -> String?,
): List<RouteTransferWindowEvidence> {
    if (legs.size !in 2..6) return emptyList()
    val windows = ArrayList<RouteTransferWindowEvidence>(legs.size - 1)
    for (index in 0 until legs.lastIndex) {
        val arriving = legs[index]
        val departing = legs[index + 1]
        if (arriving.tripId.isBlank() || departing.tripId.isBlank() ||
            arriving.toStopId.isBlank() || departing.fromStopId.isBlank() ||
            arriving.arrivalEpochMillis <= 0L || departing.departureEpochMillis <= 0L) {
            return emptyList()
        }
        val gapMillis = runCatching {
            Math.subtractExact(departing.departureEpochMillis, arriving.arrivalEpochMillis)
        }.getOrNull() ?: return emptyList()
        // Neither an overlapping timetable nor an implausibly large wait is a usable cue.
        if (gapMillis !in 60_000L..10_800_000L) return emptyList()
        val arrivalName = resolveStopName(arriving.toStopId)?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return emptyList()
        val departureName = resolveStopName(departing.fromStopId)?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return emptyList()
        windows += RouteTransferWindowEvidence(
            arrivalStopId = arriving.toStopId,
            departureStopId = departing.fromStopId,
            arrivalStopName = arrivalName,
            departureStopName = departureName,
            plannedGapMinutes = ((gapMillis + 59_999L) / 60_000L).toInt(),
            plannedDepartureEpochMillis = departing.departureEpochMillis,
        )
    }
    return windows
}

/** Foreground-only scheduled departure countdown, not realtime or connection-risk advice. */
internal fun plannedTransferDepartureCountdownMinutes(
    departureEpochMillis: Long,
    nowEpochMillis: Long,
): Int? {
    if (departureEpochMillis <= 0L || nowEpochMillis <= 0L) return null
    val remainingMillis = runCatching {
        Math.subtractExact(departureEpochMillis, nowEpochMillis)
    }.getOrNull() ?: return null
    // Outside the near-term 3h window or already departed: show no countdown.
    if (remainingMillis !in 1L..10_800_000L) return null
    return ((remainingMillis + 59_999L) / 60_000L).toInt()
}

/** A past *scheduled* transfer departure is not evidence of the vehicle's actual status. */
internal fun plannedTransferDepartureRecentlyPassed(
    departureEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean {
    if (departureEpochMillis <= 0L || nowEpochMillis <= 0L) return false
    val elapsedMillis = runCatching {
        Math.subtractExact(nowEpochMillis, departureEpochMillis)
    }.getOrNull() ?: return false
    // A bounded foreground hint avoids indefinite warnings on historic itineraries.
    return elapsedMillis in 1L..10_800_000L
}

/** Format the source GTFS departure with its agency-zone offset; never use device local time. */
internal fun plannedTransferDepartureLocalTime(
    departureEpochMillis: Long,
    agencyTimeZone: java.util.TimeZone,
): String? {
    if (departureEpochMillis <= 0L) return null
    return runCatching {
        // API 23 supports RFC 822 offset 'Z' but not ISO 8601 pattern 'X' (API 24).
        // Insert the display colon only after the fixed ±HHmm offset is formatted.
        val formatted = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm Z", java.util.Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(java.util.Date(departureEpochMillis))
        formatted.dropLast(2) + ":" + formatted.takeLast(2)
    }.getOrNull()
}

/** Public GTFS transfer endpoints, not a pedestrian route or device location. */
data class RouteTransferWalkingCoordinates(
    val arrivalLatitude: Double,
    val arrivalLongitude: Double,
    val departureLatitude: Double,
    val departureLongitude: Double,
)

/** Omit external walking actions without two different identifiable, plausible stops. */
internal fun plannedTransferWalkingCoordinates(
    arrivalStopId: String,
    departureStopId: String,
    arrivalLatitude: Double?,
    arrivalLongitude: Double?,
    departureLatitude: Double?,
    departureLongitude: Double?,
): RouteTransferWalkingCoordinates? {
    if (arrivalStopId.isBlank() || departureStopId.isBlank() ||
        arrivalStopId == departureStopId ||
        arrivalLatitude == null || arrivalLongitude == null ||
        departureLatitude == null || departureLongitude == null) return null
    if (!validTransitJourneyCoordinates(arrivalLatitude, arrivalLongitude) ||
        !validTransitJourneyCoordinates(departureLatitude, departureLongitude)) return null
    // (0,0) in a German stop feed is a missing-coordinate placeholder.
    if ((arrivalLatitude == 0.0 && arrivalLongitude == 0.0) ||
        (departureLatitude == 0.0 && departureLongitude == 0.0)) return null
    return RouteTransferWalkingCoordinates(
        arrivalLatitude, arrivalLongitude, departureLatitude, departureLongitude,
    )
}

/** A user-tapped external walking map receives only public timetable stop positions. */
internal fun plannedTransferWalkingDirectionsUrl(
    points: RouteTransferWalkingCoordinates,
): String? {
    if (plannedTransferWalkingCoordinates(
            "arrival", "departure",
            points.arrivalLatitude, points.arrivalLongitude,
            points.departureLatitude, points.departureLongitude,
        ) == null) return null
    return "https://www.google.com/maps/dir/?api=1&origin=${points.arrivalLatitude},${points.arrivalLongitude}" +
        "&destination=${points.departureLatitude},${points.departureLongitude}&travelmode=walking"
}
