package com.nazofobi.arrivalalarm

import android.database.Cursor

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
    val fromStopId: String,
    val toStopId: String,
    val transferType: Int?,
    val minTransferTimeSeconds: Int?,
    val fromRouteId: String?,
    val toRouteId: String?,
    val fromTripId: String?,
    val toTripId: String?,
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
            SELECT trip_id,route_id,service_id,headsign,direction_id,shape_id,wheelchair_accessible,bikes_allowed
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
                t.wheelchair_accessible,t.bikes_allowed,
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
                            ),
                            stopSequence = cursor.getInt(8),
                            arrivalTime = cursor.nullString(9),
                            departureTime = cursor.nullString(10),
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
                            fromStopId = cursor.getString(0),
                            toStopId = cursor.getString(1),
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
        private const val MAX_STOP_TIMES = 2_048
        private const val MAX_TRIP_CANDIDATES = 512
        private const val MAX_TRANSFERS = 512
        private const val MAX_SHAPE_POINTS = 10_000
    }
}
