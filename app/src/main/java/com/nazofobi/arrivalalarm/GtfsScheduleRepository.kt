package com.nazofobi.arrivalalarm

import android.database.Cursor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GtfsScheduleStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val parentStationId: String?,
    val locationType: Int?,
    val platformCode: String?,
    val wheelchairBoarding: Int?,
)

data class GtfsScheduleNearbyStop(
    val stop: GtfsScheduleStop,
    val distanceMeters: Int,
)

data class GtfsScheduleRoute(
    val id: String,
    val agencyId: String?,
    val shortName: String?,
    val longName: String?,
    val routeType: Int?,
)

data class GtfsScheduleTrip(
    val id: String,
    val routeId: String,
    val serviceId: String,
    val headsign: String?,
    val directionId: Int?,
    val shapeId: String?,
    val wheelchairAccessible: Int?,
    val bikesAllowed: Int?,
    val blockId: String? = null,
)

data class GtfsScheduleStopTime(
    val tripId: String,
    val stopSequence: Int,
    val stopId: String,
    val arrivalTime: String?,
    val departureTime: String?,
    val pickupType: Int?,
    val dropOffType: Int?,
    val shapeDistanceTraveled: Double?,
    val timepoint: Int?,
)

data class GtfsScheduleTripCandidate(
    val trip: GtfsScheduleTrip,
    val stopSequence: Int,
    val arrivalTime: String?,
    val departureTime: String?,
)

data class GtfsScheduleTransfer(
    val fromStopId: String?,
    val toStopId: String?,
    val transferType: Int?,
    val minTransferTimeSeconds: Int?,
    val fromRouteId: String?,
    val toRouteId: String?,
    val fromTripId: String?,
    val toTripId: String?,
)

data class GtfsScheduleFrequency(
    val tripId: String,
    val startTime: String,
    val endTime: String,
    val headwaySeconds: Int,
    val exactTimes: Int?,
)

data class GtfsScheduleShapePoint(
    val shapeId: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val distanceTraveled: Double?,
)

data class GtfsScheduleCalendar(
    val serviceId: String,
    val monday: Boolean,
    val tuesday: Boolean,
    val wednesday: Boolean,
    val thursday: Boolean,
    val friday: Boolean,
    val saturday: Boolean,
    val sunday: Boolean,
    val startDate: String?,
    val endDate: String?,
)

data class GtfsScheduleCalendarDate(
    val serviceId: String,
    val date: String,
    val exceptionType: Int,
)

/**
 * Typed, bounded read facade over NationwideTransitIndex v3's GTFS schedule graph.
 *
 * The repository intentionally contains no routing algorithm. Every list query has a hard cap,
 * and its predicates align with indexes created by NationwideTransitIndex.
 */
class GtfsScheduleRepository(
    private val index: NationwideTransitIndex,
) {
    fun stop(stopId: String): GtfsScheduleStop? {
        if (stopId.isBlank()) return null
        return index.readableDatabase.rawQuery(
            """
            SELECT id,name,lat,lon,parent_station,location_type,platform_code,wheelchair_boarding
            FROM stops
            WHERE id=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(stopId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toStop() else null
        }
    }

    fun childStops(parentStationId: String, limit: Int = 64): List<GtfsScheduleStop> {
        if (parentStationId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_CHILD_STOPS)
        return index.readableDatabase.rawQuery(
            """
            SELECT id,name,lat,lon,parent_station,location_type,platform_code,wheelchair_boarding
            FROM stops
            WHERE parent_station=?
            ORDER BY id
            LIMIT ?
            """.trimIndent(),
            arrayOf(parentStationId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toStop())
            }
        }
    }

    fun nearbyStops(
        stopId: String,
        radiusMeters: Int,
        limit: Int = 16,
    ): List<GtfsScheduleNearbyStop> {
        if (stopId.isBlank() || radiusMeters <= 0 || limit <= 0) return emptyList()
        val origin = stop(stopId) ?: return emptyList()
        val boundedRadius = radiusMeters.coerceAtMost(MAX_NEARBY_RADIUS_METERS)
        val boundedLimit = limit.coerceAtMost(MAX_NEARBY_STOPS)
        val latDelta = boundedRadius / 111_320.0
        val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1_000.0)
        val lonDelta = (boundedRadius / lonScale).coerceAtMost(180.0)
        val scanLimit = (boundedLimit * NEARBY_SCAN_MULTIPLIER).coerceAtMost(MAX_NEARBY_SCAN_ROWS)

        return index.readableDatabase.rawQuery(
            """
            SELECT id,name,lat,lon,parent_station,location_type,platform_code,wheelchair_boarding
            FROM stops
            WHERE id<>?
              AND lat BETWEEN ? AND ?
              AND lon BETWEEN ? AND ?
              AND (location_type IS NULL OR location_type=0)
            ORDER BY lat,lon,id
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                stopId,
                (origin.latitude - latDelta).toString(),
                (origin.latitude + latDelta).toString(),
                (origin.longitude - lonDelta).toString(),
                (origin.longitude + lonDelta).toString(),
                scanLimit.toString(),
            ),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val candidate = cursor.toStop()
                    val distance = distanceMeters(origin, candidate)
                    if (distance <= boundedRadius) add(GtfsScheduleNearbyStop(candidate, distance))
                }
            }
        }.sortedWith(
            compareBy<GtfsScheduleNearbyStop> { it.distanceMeters }.thenBy { it.stop.id }
        ).take(boundedLimit)
    }

    fun route(routeId: String): GtfsScheduleRoute? {
        if (routeId.isBlank()) return null
        return index.readableDatabase.rawQuery(
            """
            SELECT route_id,agency_id,short_name,long_name,route_type
            FROM routes
            WHERE route_id=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(routeId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toRoute() else null
        }
    }

    fun trip(tripId: String): GtfsScheduleTrip? {
        if (tripId.isBlank()) return null
        return index.readableDatabase.rawQuery(
            """
            SELECT trip_id,route_id,service_id,headsign,direction_id,shape_id,wheelchair_accessible,bikes_allowed,block_id
            FROM trips
            WHERE trip_id=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(tripId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toTrip() else null
        }
    }

    fun stopTimesForTrip(tripId: String, limit: Int = 512): List<GtfsScheduleStopTime> {
        if (tripId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_STOP_TIMES)
        return index.readableDatabase.rawQuery(
            """
            SELECT trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type,shape_dist_traveled,timepoint
            FROM stop_times
            WHERE trip_id=?
            ORDER BY stop_sequence
            LIMIT ?
            """.trimIndent(),
            arrayOf(tripId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toStopTime())
            }
        }
    }

    fun candidateTripsAtStop(stopId: String, limit: Int = 128): List<GtfsScheduleTripCandidate> {
        if (stopId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_TRIP_CANDIDATES)
        return index.readableDatabase.rawQuery(
            """
            SELECT
                t.trip_id,t.route_id,t.service_id,t.headsign,t.direction_id,t.shape_id,
                t.wheelchair_accessible,t.bikes_allowed,t.block_id,
                st.stop_sequence,st.arrival_time,st.departure_time
            FROM stop_times st
            JOIN trips t ON t.trip_id=st.trip_id
            WHERE st.stop_id=?
            ORDER BY COALESCE(st.departure_time,st.arrival_time),t.trip_id,st.stop_sequence
            LIMIT ?
            """.trimIndent(),
            arrayOf(stopId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GtfsScheduleTripCandidate(
                            trip = GtfsScheduleTrip(
                                id = cursor.getString(0),
                                routeId = cursor.getString(1),
                                serviceId = cursor.getString(2),
                                headsign = cursor.nullString(3),
                                directionId = cursor.nullInt(4),
                                shapeId = cursor.nullString(5),
                                wheelchairAccessible = cursor.nullInt(6),
                                bikesAllowed = cursor.nullInt(7),
                                blockId = cursor.nullString(8),
                            ),
                            stopSequence = cursor.getInt(9),
                            arrivalTime = cursor.nullString(10),
                            departureTime = cursor.nullString(11),
                        )
                    )
                }
            }
        }
    }

    fun candidateTripsAtStop(
        stopId: String,
        serviceDate: GtfsServiceDate,
        earliestBoardSeconds: Int,
        limit: Int = 128,
    ): List<GtfsScheduleTripCandidate> {
        if (stopId.isBlank() || earliestBoardSeconds < 0) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_TRIP_CANDIDATES)
        val serviceDateRaw = serviceDate.toString()
        val weekdayColumn = when (serviceDate.weekday) {
            GtfsWeekday.MONDAY -> "c.monday"
            GtfsWeekday.TUESDAY -> "c.tuesday"
            GtfsWeekday.WEDNESDAY -> "c.wednesday"
            GtfsWeekday.THURSDAY -> "c.thursday"
            GtfsWeekday.FRIDAY -> "c.friday"
            GtfsWeekday.SATURDAY -> "c.saturday"
            GtfsWeekday.SUNDAY -> "c.sunday"
        }
        val boardTime = "COALESCE(st.departure_time,st.arrival_time)"
        val boardSeconds = """
            (
                CAST(substr($boardTime,1,instr($boardTime,':')-1) AS INTEGER) * 3600 +
                CAST(substr($boardTime,instr($boardTime,':')+1,2) AS INTEGER) * 60 +
                CAST(substr($boardTime,instr($boardTime,':')+4,2) AS INTEGER)
            )
        """.trimIndent()

        return index.readableDatabase.rawQuery(
            """
            SELECT
                t.trip_id,t.route_id,t.service_id,t.headsign,t.direction_id,t.shape_id,
                t.wheelchair_accessible,t.bikes_allowed,t.block_id,
                st.stop_sequence,st.arrival_time,st.departure_time
            FROM stop_times st
            JOIN trips t ON t.trip_id=st.trip_id
            WHERE st.stop_id=?
              AND $boardTime IS NOT NULL
              AND (
                    $boardSeconds >= CAST(? AS INTEGER)
                    OR EXISTS (
                        SELECT 1 FROM frequencies frequency
                        WHERE frequency.trip_id=t.trip_id
                    )
              )
              AND (
                    EXISTS (
                        SELECT 1
                        FROM calendar_dates added
                        WHERE added.service_id=t.service_id
                          AND added.date=?
                          AND added.exception_type=1
                    )
                    OR (
                        NOT EXISTS (
                            SELECT 1
                            FROM calendar_dates exception
                            WHERE exception.service_id=t.service_id
                              AND exception.date=?
                        )
                        AND EXISTS (
                            SELECT 1
                            FROM calendar c
                            WHERE c.service_id=t.service_id
                              AND c.start_date<=?
                              AND c.end_date>=?
                              AND COALESCE($weekdayColumn,0)=1
                        )
                    )
              )
            ORDER BY
                CASE WHEN EXISTS (
                    SELECT 1 FROM frequencies frequency
                    WHERE frequency.trip_id=t.trip_id
                ) THEN 0 ELSE 1 END,
                $boardSeconds,t.trip_id,st.stop_sequence
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                stopId,
                earliestBoardSeconds.toString(),
                serviceDateRaw,
                serviceDateRaw,
                serviceDateRaw,
                serviceDateRaw,
                boundedLimit.toString(),
            ),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GtfsScheduleTripCandidate(
                            trip = GtfsScheduleTrip(
                                id = cursor.getString(0),
                                routeId = cursor.getString(1),
                                serviceId = cursor.getString(2),
                                headsign = cursor.nullString(3),
                                directionId = cursor.nullInt(4),
                                shapeId = cursor.nullString(5),
                                wheelchairAccessible = cursor.nullInt(6),
                                bikesAllowed = cursor.nullInt(7),
                                blockId = cursor.nullString(8),
                            ),
                            stopSequence = cursor.getInt(9),
                            arrivalTime = cursor.nullString(10),
                            departureTime = cursor.nullString(11),
                        )
                    )
                }
            }
        }
    }

    fun frequenciesForTrip(
        tripId: String,
        limit: Int = 64,
    ): List<GtfsScheduleFrequency> {
        if (tripId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_FREQUENCIES)
        val startSeconds = """
            (
                CAST(substr(start_time,1,instr(start_time,':')-1) AS INTEGER) * 3600 +
                CAST(substr(start_time,instr(start_time,':')+1,2) AS INTEGER) * 60 +
                CAST(substr(start_time,instr(start_time,':')+4,2) AS INTEGER)
            )
        """.trimIndent()
        val endSeconds = """
            (
                CAST(substr(end_time,1,instr(end_time,':')-1) AS INTEGER) * 3600 +
                CAST(substr(end_time,instr(end_time,':')+1,2) AS INTEGER) * 60 +
                CAST(substr(end_time,instr(end_time,':')+4,2) AS INTEGER)
            )
        """.trimIndent()
        return index.readableDatabase.rawQuery(
            """
            SELECT trip_id,start_time,end_time,headway_secs,exact_times
            FROM frequencies
            WHERE trip_id=?
            ORDER BY $startSeconds,$endSeconds,headway_secs,COALESCE(exact_times,0)
            LIMIT ?
            """.trimIndent(),
            arrayOf(tripId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GtfsScheduleFrequency(
                            tripId = cursor.getString(0),
                            startTime = cursor.getString(1),
                            endTime = cursor.getString(2),
                            headwaySeconds = cursor.getInt(3),
                            exactTimes = cursor.nullInt(4),
                        )
                    )
                }
            }
        }
    }

    fun tripsInBlock(
        blockId: String,
        serviceId: String,
        limit: Int = 128,
    ): List<GtfsScheduleTrip> {
        if (blockId.isBlank() || serviceId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_BLOCK_TRIPS)
        return index.readableDatabase.rawQuery(
            """
            SELECT trip_id,route_id,service_id,headsign,direction_id,shape_id,wheelchair_accessible,bikes_allowed,block_id
            FROM trips
            WHERE block_id=? AND service_id=?
            ORDER BY trip_id
            LIMIT ?
            """.trimIndent(),
            arrayOf(blockId, serviceId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toTrip())
            }
        }
    }

    fun linkedTransfersFromTrip(
        fromTripId: String,
        limit: Int = 128,
    ): List<GtfsScheduleTransfer> {
        if (fromTripId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_LINKED_TRANSFERS)
        return index.readableDatabase.rawQuery(
            """
            SELECT from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_route_id,to_route_id,from_trip_id,to_trip_id
            FROM transfers
            WHERE from_trip_id=? AND transfer_type IN (4,5)
            ORDER BY to_trip_id,COALESCE(from_stop_id,''),COALESCE(to_stop_id,'')
            LIMIT ?
            """.trimIndent(),
            arrayOf(fromTripId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GtfsScheduleTransfer(
                            fromStopId = cursor.nullString(0),
                            toStopId = cursor.nullString(1),
                            transferType = cursor.nullInt(2),
                            minTransferTimeSeconds = cursor.nullInt(3),
                            fromRouteId = cursor.nullString(4),
                            toRouteId = cursor.nullString(5),
                            fromTripId = cursor.nullString(6),
                            toTripId = cursor.nullString(7),
                        )
                    )
                }
            }
        }
    }

    fun transfersFromStop(stopId: String, limit: Int = 128): List<GtfsScheduleTransfer> {
        if (stopId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_TRANSFERS)
        return index.readableDatabase.rawQuery(
            """
            SELECT from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_route_id,to_route_id,from_trip_id,to_trip_id
            FROM transfers
            WHERE from_stop_id=?
            ORDER BY to_stop_id
            LIMIT ?
            """.trimIndent(),
            arrayOf(stopId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GtfsScheduleTransfer(
                            fromStopId = cursor.nullString(0),
                            toStopId = cursor.nullString(1),
                            transferType = cursor.nullInt(2),
                            minTransferTimeSeconds = cursor.nullInt(3),
                            fromRouteId = cursor.nullString(4),
                            toRouteId = cursor.nullString(5),
                            fromTripId = cursor.nullString(6),
                            toTripId = cursor.nullString(7),
                        )
                    )
                }
            }
        }
    }

    fun shapePoints(shapeId: String, limit: Int = 2_048): List<GtfsScheduleShapePoint> {
        if (shapeId.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_SHAPE_POINTS)
        return index.readableDatabase.rawQuery(
            """
            SELECT shape_id,sequence,lat,lon,dist_traveled
            FROM shapes
            WHERE shape_id=?
            ORDER BY sequence
            LIMIT ?
            """.trimIndent(),
            arrayOf(shapeId, boundedLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GtfsScheduleShapePoint(
                            shapeId = cursor.getString(0),
                            sequence = cursor.getInt(1),
                            latitude = cursor.getDouble(2),
                            longitude = cursor.getDouble(3),
                            distanceTraveled = cursor.nullDouble(4),
                        )
                    )
                }
            }
        }
    }

    fun calendar(serviceId: String): GtfsScheduleCalendar? {
        if (serviceId.isBlank()) return null
        return index.readableDatabase.rawQuery(
            """
            SELECT service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
            FROM calendar
            WHERE service_id=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(serviceId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            GtfsScheduleCalendar(
                serviceId = cursor.getString(0),
                monday = cursor.getInt(1) == 1,
                tuesday = cursor.getInt(2) == 1,
                wednesday = cursor.getInt(3) == 1,
                thursday = cursor.getInt(4) == 1,
                friday = cursor.getInt(5) == 1,
                saturday = cursor.getInt(6) == 1,
                sunday = cursor.getInt(7) == 1,
                startDate = cursor.nullString(8),
                endDate = cursor.nullString(9),
            )
        }
    }

    fun calendarException(serviceId: String, date: String): GtfsScheduleCalendarDate? {
        if (serviceId.isBlank() || date.isBlank()) return null
        return index.readableDatabase.rawQuery(
            """
            SELECT service_id,date,exception_type
            FROM calendar_dates
            WHERE service_id=? AND date=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(serviceId, date),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            GtfsScheduleCalendarDate(
                serviceId = cursor.getString(0),
                date = cursor.getString(1),
                exceptionType = cursor.getInt(2),
            )
        }
    }

    private fun distanceMeters(from: GtfsScheduleStop, to: GtfsScheduleStop): Int {
        val meanLatitudeRadians = Math.toRadians((from.latitude + to.latitude) / 2.0)
        val latitudeMeters = (to.latitude - from.latitude) * 111_320.0
        val longitudeMeters = (to.longitude - from.longitude) * 111_320.0 * cos(meanLatitudeRadians)
        return sqrt(latitudeMeters * latitudeMeters + longitudeMeters * longitudeMeters).roundToInt()
    }

    private fun Cursor.toStop() = GtfsScheduleStop(
        id = getString(0),
        name = getString(1),
        latitude = getDouble(2),
        longitude = getDouble(3),
        parentStationId = nullString(4),
        locationType = nullInt(5),
        platformCode = nullString(6),
        wheelchairBoarding = nullInt(7),
    )

    private fun Cursor.toRoute() = GtfsScheduleRoute(
        id = getString(0),
        agencyId = nullString(1),
        shortName = nullString(2),
        longName = nullString(3),
        routeType = nullInt(4),
    )

    private fun Cursor.toTrip() = GtfsScheduleTrip(
        id = getString(0),
        routeId = getString(1),
        serviceId = getString(2),
        headsign = nullString(3),
        directionId = nullInt(4),
        shapeId = nullString(5),
        wheelchairAccessible = nullInt(6),
        bikesAllowed = nullInt(7),
        blockId = nullString(8),
    )

    private fun Cursor.toStopTime() = GtfsScheduleStopTime(
        tripId = getString(0),
        stopSequence = getInt(1),
        stopId = getString(2),
        arrivalTime = nullString(3),
        departureTime = nullString(4),
        pickupType = nullInt(5),
        dropOffType = nullInt(6),
        shapeDistanceTraveled = nullDouble(7),
        timepoint = nullInt(8),
    )

    private fun Cursor.nullString(index: Int): String? = if (isNull(index)) null else getString(index)
    private fun Cursor.nullInt(index: Int): Int? = if (isNull(index)) null else getInt(index)
    private fun Cursor.nullDouble(index: Int): Double? = if (isNull(index)) null else getDouble(index)

    companion object {
        private const val MAX_CHILD_STOPS = 256
        private const val MAX_NEARBY_RADIUS_METERS = 2_000
        private const val MAX_NEARBY_STOPS = 64
        private const val NEARBY_SCAN_MULTIPLIER = 8
        private const val MAX_NEARBY_SCAN_ROWS = 512
        private const val MAX_STOP_TIMES = 2_048
        private const val MAX_TRIP_CANDIDATES = 512
        private const val MAX_TRANSFERS = 512
        private const val MAX_LINKED_TRANSFERS = 512
        private const val MAX_BLOCK_TRIPS = 512
        private const val MAX_FREQUENCIES = 512
        private const val MAX_SHAPE_POINTS = 10_000
    }
}
