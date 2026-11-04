package com.nazofobi.arrivalalarm

import android.content.Context

/**
 * Read-only, bounded static GTFS line inspection. Each row is an illustrative
 * scheduled direction/headsign pattern, not a live vehicle or a complete list
 * of every timetable variant. No journey or alarm state is changed.
 */
internal data class GtfsLineDirection(
    val headsign: String,
    val stopNames: List<String>,
    val stopsTruncated: Boolean,
)

internal data class GtfsLineDetail(
    val lineName: String,
    val directions: List<GtfsLineDirection>,
    val additionalDirectionsOmitted: Boolean,
)

internal fun loadGtfsLineDetail(
    context: Context,
    routeId: String,
    expectedSourceVersion: String,
): GtfsLineDetail? {
    if (routeId.isBlank() || expectedSourceVersion.isBlank()) return null
    NationwideTransitIndex(context).use { index ->
        if (!index.isReady() || index.sourceVersion() != expectedSourceVersion) return null
        val detail = inspectGtfsLineDetail(index, routeId)
        // A replaced offline feed invalidates a just-loaded line preview.
        return detail.takeIf { index.isReady() && index.sourceVersion() == expectedSourceVersion }
    }
}

internal fun inspectGtfsLineDetail(index: NationwideTransitIndex, routeId: String): GtfsLineDetail? {
    if (routeId.isBlank()) return null
    val route = GtfsScheduleRepository(index).route(routeId) ?: return null
    val label = route.shortName?.takeIf { it.isNotBlank() }
        ?: route.longName?.takeIf { it.isNotBlank() }
        ?: route.id

    // One deterministic representative per direction+headsign. A later feed can
    // include other stop patterns with the same headsign; describe this as a
    // sample, never as the entire route topology.
    // Prefer a trip with at least two scheduled stops; a partial first trip must
    // not hide an otherwise valid direction from the line details.
    val candidates = index.readableDatabase.rawQuery(
        """
        SELECT MIN(t.trip_id), COALESCE(TRIM(t.headsign), ''), COALESCE(t.direction_id, -1)
        FROM trips t
        WHERE t.route_id = ?
          AND EXISTS (
              SELECT 1 FROM stop_times st
              WHERE st.trip_id = t.trip_id
              ORDER BY st.stop_sequence
              LIMIT 1 OFFSET 1
          )
        GROUP BY t.direction_id, TRIM(t.headsign)
        ORDER BY t.direction_id, TRIM(t.headsign)
        LIMIT ?
        """.trimIndent(),
        arrayOf(routeId, (MAX_LINE_VARIANTS + 1).toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(0) to cursor.getString(1))
            }
        }
    }

    val directions = candidates.take(MAX_LINE_VARIANTS).mapNotNull { (tripId, headsign) ->
        val rows = index.readableDatabase.rawQuery(
            """
            SELECT s.name FROM stop_times st
            JOIN stops s ON s.id = st.stop_id
            WHERE st.trip_id = ?
            ORDER BY st.stop_sequence
            LIMIT ?
            """.trimIndent(),
            arrayOf(tripId, (MAX_LINE_STOPS + 1).toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        if (rows.size < 2) null
        else GtfsLineDirection(
            headsign = headsign,
            stopNames = rows.take(MAX_LINE_STOPS),
            stopsTruncated = rows.size > MAX_LINE_STOPS,
        )
    }
    return GtfsLineDetail(
        lineName = label,
        directions = directions,
        additionalDirectionsOmitted = candidates.size > MAX_LINE_VARIANTS,
    )
}

private const val MAX_LINE_VARIANTS = 8
private const val MAX_LINE_STOPS = 256
