package com.nazofobi.arrivalalarm

/**
 * Clips an authoritative GTFS shape to the boarded -> alighted segment.
 *
 * GTFS shape_dist_traveled on stop_times and shapes must use the same units.
 * Never substitute straight lines between stops: absent or inconsistent distance
 * evidence means the map has no supported route geometry for this leg.
 */
internal fun clipGtfsShapePolyline(
    shape: List<GtfsScheduleShapePoint>,
    boardingDistance: Double?,
    alightingDistance: Double?,
    maxVertices: Int = 256,
): List<MapPoint> {
    val from = boardingDistance ?: return emptyList()
    val to = alightingDistance ?: return emptyList()
    if (!from.isFinite() || !to.isFinite() || from < 0.0 || to <= from ||
        maxVertices !in 2..512 || shape.size !in 2..2_048
    ) return emptyList()

    val selectedShapeId = shape.first().shapeId
    if (selectedShapeId.isBlank() || shape.any { it.shapeId != selectedShapeId }) return emptyList()

    var previousSequence = Int.MIN_VALUE
    var previousDistance = Double.NEGATIVE_INFINITY
    for (point in shape) {
        val distance = point.distanceTraveled ?: return emptyList()
        if (point.sequence <= previousSequence || !distance.isFinite() ||
            distance < previousDistance || distance < 0.0 ||
            !point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0
        ) return emptyList()
        previousSequence = point.sequence
        previousDistance = distance
    }

    // Reject an antimeridian jump anywhere inside the selected segment, not
    // only when an interpolated endpoint happens to land on that edge.
    for (index in 1 until shape.size) {
        val previous = shape[index - 1]
        val current = shape[index]
        val segmentStart = previous.distanceTraveled!!
        val segmentEnd = current.distanceTraveled!!
        if (segmentEnd > from && segmentStart < to &&
            kotlin.math.abs(current.longitude - previous.longitude) > 180.0
        ) return emptyList()
    }

    val firstDistance = shape.first().distanceTraveled!!
    val lastDistance = shape.last().distanceTraveled!!
    if (from < firstDistance || to > lastDistance) return emptyList()

    fun pointAt(distance: Double): MapPoint? {
        for (index in 1 until shape.size) {
            val a = shape[index - 1]
            val b = shape[index]
            val start = a.distanceTraveled!!
            val end = b.distanceTraveled!!
            if (distance < start || distance > end || start == end) continue
            val fraction = (distance - start) / (end - start)
            // Avoid drawing an invented shortcut across the antimeridian.
            if (kotlin.math.abs(b.longitude - a.longitude) > 180.0) return null
            return MapPoint(
                a.latitude + fraction * (b.latitude - a.latitude),
                a.longitude + fraction * (b.longitude - a.longitude),
                "",
            )
        }
        return null
    }

    val start = pointAt(from) ?: return emptyList()
    val end = pointAt(to) ?: return emptyList()
    val selected = buildList {
        add(start)
        shape.forEach { point ->
            val distance = point.distanceTraveled!!
            if (distance > from && distance < to) {
                add(MapPoint(point.latitude, point.longitude, ""))
            }
        }
        add(end)
    }
    // Remove only consecutive duplicates: a circular service can legitimately
    // revisit the same coordinates later in the selected trip.
    val withoutConsecutiveDuplicates = selected.filterIndexed { index, point ->
        index == 0 || point.latitude != selected[index - 1].latitude ||
            point.longitude != selected[index - 1].longitude
    }
    if (withoutConsecutiveDuplicates.size < 2) return emptyList()
    if (withoutConsecutiveDuplicates.size <= maxVertices) return withoutConsecutiveDuplicates

    // Stable bound: preserve both exact endpoints, and sample the ordered feed
    // points without sending an unbounded polyline to the map renderer.
    return (0 until maxVertices).map { index ->
        withoutConsecutiveDuplicates[(index.toLong() * (withoutConsecutiveDuplicates.lastIndex) / (maxVertices - 1)).toInt()]
    }
}
