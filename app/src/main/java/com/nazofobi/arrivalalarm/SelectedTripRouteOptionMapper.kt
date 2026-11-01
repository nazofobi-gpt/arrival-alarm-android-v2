package com.nazofobi.arrivalalarm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class SelectedTripRouteOptionMapper(
    private val agencyTimeZone: TimeZone,
    private val walkingMetersPerSecond: Double,
) {
    init {
        require(walkingMetersPerSecond in 0.5..3.0)
    }

    fun map(
        origin: MapPoint,
        destination: MapPoint,
        selection: GtfsSelectedTrip,
        detail: GtfsTripDetail,
        roles: ConfirmedTripStopRoles,
        line: String,
        sourceUpdatedAtEpochSeconds: Long,
    ): RouteOption? {
        if (!validTransitJourneyCoordinates(origin.latitude, origin.longitude) ||
            !validTransitJourneyCoordinates(destination.latitude, destination.longitude) ||
            sourceUpdatedAtEpochSeconds <= 0L ||
            detail.identity != selection.identity ||
            roles.identity != selection.identity ||
            roles.boarding.stopSequence != selection.boarding.sequence ||
            roles.boarding.stopId != selection.boarding.stopId ||
            roles.destination.stopSequence != selection.destination.sequence ||
            roles.destination.stopId != selection.destination.stopId ||
            roles.alarmTarget.stopSequence != selection.alarmTarget.sequence ||
            roles.alarmTarget.stopId != selection.alarmTarget.stopId
        ) return null

        val segment = detail.stops.filter {
            it.stopSequence >= selection.boarding.sequence &&
                it.stopSequence <= selection.destination.sequence
        }
        if (segment.isEmpty() ||
            segment.first() != roles.boarding ||
            segment.last() != roles.destination
        ) return null

        val serviceDate = selection.identity.serviceDate
        val departureEpochMillis = epoch(roles.boarding.scheduledDeparture, serviceDate)
            ?: return null
        val arrivalEpochMillis = epoch(roles.destination.scheduledArrival, serviceDate)
            ?: return null
        if (arrivalEpochMillis <= departureEpochMillis) return null

        val accessWalkSeconds = walkSeconds(
            origin.latitude,
            origin.longitude,
            roles.boarding.latitude,
            roles.boarding.longitude,
        )
        val egressWalkSeconds = walkSeconds(
            destination.latitude,
            destination.longitude,
            roles.destination.latitude,
            roles.destination.longitude,
        )

        val routeStops = mutableListOf<RouteStop>()
        for (stop in segment) {
            val plannedArrival = if (stop.scheduledArrival == null) null else
                epoch(stop.scheduledArrival, serviceDate)?.let(::iso) ?: return null
            val plannedDeparture = if (stop.scheduledDeparture == null) null else
                epoch(stop.scheduledDeparture, serviceDate)?.let(::iso) ?: return null
            routeStops += RouteStop(
                id = "${stop.stopSequence}:${stop.stopId}",
                name = stop.name,
                latitude = stop.latitude,
                longitude = stop.longitude,
                plannedArrival = plannedArrival,
                plannedDeparture = plannedDeparture,
                plannedPlatform = stop.platformCode,
            )
        }

        val identityParts = listOf(
            selection.identity.tripId,
            selection.identity.serviceDate.toString(),
            selection.identity.serviceId,
            selection.identity.routeId,
            selection.identity.directionId?.toString() ?: "",
            selection.boarding.sequence.toString(),
            selection.boarding.stopId,
            selection.destination.sequence.toString(),
            selection.destination.stopId,
        )
        val optionId = "selected:" + identityParts.joinToString("|") {
            "${it.length}:$it"
        }
        return RouteOption(
            id = optionId,
            origin = origin,
            destination = destination,
            line = line.takeIf { it.isNotBlank() } ?: selection.identity.routeId,
            direction = detail.headsign?.takeIf { it.isNotBlank() } ?: roles.destination.name,
            departure = clock(departureEpochMillis),
            arrival = clock(arrivalEpochMillis),
            walkingMinutes = totalWalkingMinutes(accessWalkSeconds, egressWalkSeconds),
            transfers = 0,
            tripIds = listOf(selection.identity.tripId),
            stops = routeStops,
            sourceUpdatedAtEpochSeconds = sourceUpdatedAtEpochSeconds,
            departureEpochMillis = departureEpochMillis,
            arrivalEpochMillis = arrivalEpochMillis,
            accessWalking = if (accessWalkSeconds > 0) RouteWalkingLegEvidence(
                stopId = roles.boarding.stopId,
                stopName = roles.boarding.name,
                walkSeconds = accessWalkSeconds,
            ) else null,
            egressWalking = if (egressWalkSeconds > 0) RouteWalkingLegEvidence(
                stopId = roles.destination.stopId,
                stopName = roles.destination.name,
                walkSeconds = egressWalkSeconds,
            ) else null,
        )
    }

    private fun epoch(raw: String?, serviceDate: GtfsServiceDate): Long? = raw?.let {
        runCatching {
            GtfsServiceTime.parse(it).resolve(serviceDate, agencyTimeZone).epochMillis
        }.getOrNull()
    }

    private fun clock(epochMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(Date(epochMillis))

    private fun iso(epochMillis: Long): String {
        val compact = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply {
            timeZone = agencyTimeZone
        }.format(Date(epochMillis))
        if (compact.length < 5) return compact
        val offsetStart = compact.length - 5
        return compact.substring(0, offsetStart) +
            compact.substring(offsetStart, offsetStart + 3) + ":" +
            compact.substring(offsetStart + 3)
    }

    private fun totalWalkingMinutes(accessSeconds: Int, egressSeconds: Int): Int {
        val totalSeconds = accessSeconds.toLong() + egressSeconds.toLong()
        return ((totalSeconds + 59L) / 60L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun walkSeconds(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Int {
        val distance = distanceMeters(fromLatitude, fromLongitude, toLatitude, toLongitude)
        return ceil(distance / walkingMetersPerSecond)
            .coerceIn(0.0, Int.MAX_VALUE.toDouble())
            .toInt()
    }

    private fun distanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double {
        val lat1 = Math.toRadians(fromLatitude)
        val lat2 = Math.toRadians(toLatitude)
        val latDelta = Math.toRadians(toLatitude - fromLatitude)
        val lonDelta = Math.toRadians(toLongitude - fromLongitude)
        val sinLat = sin(latDelta / 2.0)
        val sinLon = sin(lonDelta / 2.0)
        val a = (
            sinLat * sinLat +
                cos(lat1) * cos(lat2) * sinLon * sinLon
            ).coerceIn(0.0, 1.0)
        return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
