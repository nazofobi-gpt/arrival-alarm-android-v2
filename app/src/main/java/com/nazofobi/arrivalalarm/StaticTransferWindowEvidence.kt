package com.nazofobi.arrivalalarm

/** Scheduled gap between two proven GTFS legs. Not a live connection or boarding guarantee. */
data class RouteTransferWindowEvidence(
    val arrivalStopId: String,
    val departureStopId: String,
    val arrivalStopName: String,
    val departureStopName: String,
    val plannedGapMinutes: Int,
    // Previous vehicle's GTFS UTC arrival; never derived from realtime or device time.
    val plannedArrivalEpochMillis: Long = 0L,
    val plannedArrivalLocalTime: String? = null,
    // Source GTFS UTC instant; absent timestamp never creates a countdown.
    val plannedDepartureEpochMillis: Long = 0L,
    // Source timetable time in the GTFS agency's zone, including DST offset.
    val plannedDepartureLocalTime: String? = null,
    // Explicit GTFS stop.platform_code values, NOT realtime platform updates.
    val plannedArrivalPlatformCode: String? = null,
    val plannedDeparturePlatformCode: String? = null,
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
            plannedArrivalEpochMillis = arriving.arrivalEpochMillis,
            plannedDepartureEpochMillis = departing.departureEpochMillis,
        )
    }
    return windows
}

/** Bound provider-supplied platform_code before showing it as timetable evidence. */
internal fun boundedGtfsPlatformCode(value: String?): String? {
    val code = value?.trim() ?: return null
    return code.takeIf {
        it.length in 1..24 && it.none { char ->
            Character.isISOControl(char) || Character.getType(char) == Character.FORMAT.toInt()
        }
    }
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

/** Scheduled-only countdown to the inbound vehicle's planned transfer arrival. */
internal fun plannedTransferArrivalCountdownMinutes(
    window: RouteTransferWindowEvidence,
    nowEpochMillis: Long,
): Int? {
    if (window.arrivalStopId.isBlank() || window.departureStopId.isBlank() ||
        window.arrivalStopName.isBlank() || window.departureStopName.isBlank() ||
        window.plannedGapMinutes !in 1..180 ||
        window.plannedArrivalEpochMillis <= 0L || window.plannedDepartureEpochMillis <= 0L
    ) return null
    val gapMillis = runCatching {
        Math.subtractExact(window.plannedDepartureEpochMillis, window.plannedArrivalEpochMillis)
    }.getOrNull() ?: return null
    // Reject contradictory or rounded-only timestamps. Do not infer realtime arrival.
    if (gapMillis !in 60_000L..10_800_000L ||
        ((gapMillis + 59_999L) / 60_000L).toInt() != window.plannedGapMinutes) return null
    return plannedTransferDepartureCountdownMinutes(window.plannedArrivalEpochMillis, nowEpochMillis)
}

/** Source-verified scheduled imminent departure cue; never a live position claim. */
internal fun plannedTransferImminentDeparture(
    window: RouteTransferWindowEvidence,
    nowEpochMillis: Long,
): Boolean {
    if (window.arrivalStopId.isBlank() || window.departureStopId.isBlank() ||
        window.arrivalStopName.isBlank() || window.departureStopName.isBlank() ||
        window.plannedGapMinutes !in 1..180 ||
        window.plannedArrivalEpochMillis <= 0L || window.plannedDepartureEpochMillis <= 0L ||
        nowEpochMillis <= 0L) return false
    val gapMillis = runCatching {
        Math.subtractExact(window.plannedDepartureEpochMillis, window.plannedArrivalEpochMillis)
    }.getOrNull() ?: return false
    if (gapMillis !in 60_000L..10_800_000L ||
        ((gapMillis + 59_999L) / 60_000L).toInt() != window.plannedGapMinutes) return false
    val remainingMillis = runCatching {
        Math.subtractExact(window.plannedDepartureEpochMillis, nowEpochMillis)
    }.getOrNull() ?: return false
    // Only the last two minutes before scheduled departure; never after.
    return remainingMillis in 1L..120_000L
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

/**
 * Only exact scheduled UTC timestamps, consistent display gap, two distinct public
 * GTFS stops and valid positions authorize any straight-line transfer metric.
 * Neither the distance nor the departure window proves a walkable connection.
 */
private fun verifiedTransferStraightLineDistanceAndGap(
    window: RouteTransferWindowEvidence,
): Pair<Double, Long>? {
    val points = window.walkingCoordinates ?: return null
    if (window.plannedGapMinutes !in 1..180 ||
        window.arrivalStopId.isBlank() || window.departureStopId.isBlank() ||
        window.arrivalStopId == window.departureStopId) return null
    if (plannedTransferWalkingCoordinates(
            window.arrivalStopId, window.departureStopId,
            points.arrivalLatitude, points.arrivalLongitude,
            points.departureLatitude, points.departureLongitude,
        ) == null) return null

    val gapMillis = if (window.plannedArrivalEpochMillis > 0L &&
        window.plannedDepartureEpochMillis > 0L) {
        runCatching {
            Math.subtractExact(
                window.plannedDepartureEpochMillis, window.plannedArrivalEpochMillis,
            )
        }.getOrNull()
    } else null
    if (gapMillis == null || gapMillis !in 60_000L..10_800_000L ||
        ((gapMillis + 59_999L) / 60_000L).toInt() != window.plannedGapMinutes) return null

    val lat1 = Math.toRadians(points.arrivalLatitude)
    val lat2 = Math.toRadians(points.departureLatitude)
    val deltaLat = lat2 - lat1
    val deltaLon = Math.toRadians(points.departureLongitude - points.arrivalLongitude)
    val sinLat = kotlin.math.sin(deltaLat / 2.0)
    val sinLon = kotlin.math.sin(deltaLon / 2.0)
    val a = sinLat * sinLat +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinLon * sinLon
    val distanceMeters = 2.0 * 6_371_000.0 *
        kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
    return distanceMeters.takeIf { it.isFinite() && it >= 0.0 }?.let { it to gapMillis }
}

/**
 * Approximate source-stop straight-line separation, rounded UP to avoid
 * understating it. This is not pedestrian route length or walking duration.
 * Implausible >20km transfers and unevidenced schedule/geometry remain hidden.
 */
internal fun plannedTransferStraightLineDistanceMeters(
    window: RouteTransferWindowEvidence,
): Int? = verifiedTransferStraightLineDistanceAndGap(window)
    ?.first?.takeIf { it >= 1.0 && it <= 20_000.0 }
    ?.let { kotlin.math.ceil(it).toInt() }

/**
 * Conservative warning only: even an optimistic 1.7 m/s straight-line walk may
 * outlast the exact scheduled gap. Absence of this warning does NOT prove safety.
 */
internal fun plannedTransferStraightLineWalkingRisk(
    window: RouteTransferWindowEvidence,
): Boolean {
    val (distanceMeters, gapMillis) = verifiedTransferStraightLineDistanceAndGap(window)
        ?: return false
    return distanceMeters > (gapMillis / 1000.0) * 1.7
}

/**
 * Timetable-only caution for very short connections (<= 3 exact minutes).
 * Does not depend on pedestrian coordinates and must never claim live connection loss.
 * Fail closed if the scheduled timestamps/identities are incomplete or inconsistent.
 */
internal fun plannedTransferShortConnectionRisk(
    window: RouteTransferWindowEvidence,
): Boolean {
    if (window.plannedGapMinutes !in 1..180 ||
        window.arrivalStopId.isBlank() || window.departureStopId.isBlank() ||
        window.arrivalStopName.isBlank() || window.departureStopName.isBlank() ||
        window.plannedArrivalEpochMillis <= 0L || window.plannedDepartureEpochMillis <= 0L) return false
    val gapMillis = runCatching {
        Math.subtractExact(window.plannedDepartureEpochMillis, window.plannedArrivalEpochMillis)
    }.getOrNull() ?: return false
    if (gapMillis !in 60_000L..10_800_000L ||
        ((gapMillis + 59_999L) / 60_000L).toInt() != window.plannedGapMinutes) return false
    return gapMillis <= 180_000L
}
