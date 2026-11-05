package com.nazofobi.arrivalalarm

/** Scheduled gap between two proven GTFS legs. Not a live connection or boarding guarantee. */
data class RouteTransferWindowEvidence(
    val arrivalStopId: String,
    val departureStopId: String,
    val arrivalStopName: String,
    val departureStopName: String,
    val plannedGapMinutes: Int,
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
        )
    }
    return windows
}
