package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripInstanceDetailsTest {
    private val monday = GtfsServiceDate.parse("20261012")
    private val key = TripInstanceIdentity("trip-Bremen-Achim", monday, directionId = 1)
    private val trip = GtfsScheduleTrip(
        id = key.tripId,
        routeId = "route-12",
        serviceId = "weekday",
        headsign = "Achim",
        directionId = 1,
        shapeId = null,
        wheelchairAccessible = null,
        bikesAllowed = null,
    )
    private val times = listOf(
        time("bremen", 4, "08:10:00"),
        time("mahdorf", 9, "08:19:00"),
        time("achim", 16, "08:31:00"),
    )
    private val stops = listOf(
        stop("bremen", "Bremen Hbf", null, "1"),
        stop("mahdorf", "Mahndorf", "parent-mahndorf", "2"),
        stop("achim", "Achim", null, "3"),
    ).associateBy { it.id }

    @Test
    fun selectedInstancePreservesTrueStopOrderAndStationMetadata() {
        val result = reader().resolve(key)
        assertTrue(result is TripInstanceDetailResult.Found)
        val resolved = (result as TripInstanceDetailResult.Found).details
        assertEquals(key, resolved.identity)
        assertEquals("route-12", resolved.routeId)
        assertEquals(listOf(4, 9, 16), resolved.stops.map { it.stopSequence })
        assertEquals(listOf("bremen", "mahdorf", "achim"), resolved.stops.map { it.stopId })
        assertEquals("parent-mahndorf", resolved.stops[1].parentStationId)
        assertEquals("08:31:00", resolved.stops.last().departureTime)
    }

    @Test
    fun wrongDirectionDoesNotSilentlySwitchToAnotherTrip() {
        assertReason(
            TripInstanceDetailResult.Reason.DIRECTION_CHANGED,
            reader().resolve(key.copy(directionId = 0)),
        )
    }

    @Test
    fun inactiveServiceDateIsRejected() {
        assertReason(
            TripInstanceDetailResult.Reason.SERVICE_NOT_ACTIVE,
            reader(active = false).resolve(key),
        )
    }

    @Test
    fun incompleteOrDuplicateSequenceFailsClosed() {
        assertReason(
            TripInstanceDetailResult.Reason.STOP_SEQUENCE_INCOMPLETE,
            reader(stopTimes = listOf(times[0], times[1].copy(stopSequence = 4), times[2]))
                .resolve(key),
        )
        assertReason(
            TripInstanceDetailResult.Reason.STOP_SEQUENCE_INCOMPLETE,
            reader(stopTimes = listOf(times[0])).resolve(key),
        )
        assertReason(
            TripInstanceDetailResult.Reason.STOP_SEQUENCE_INCOMPLETE,
            reader(stopTimes = List(512) { index ->
                time("bremen", index + 1, "08:10:00")
            }).resolve(key),
        )
    }

    @Test
    fun missingStopMetadataCannotAppearAsRealStop() {
        assertReason(
            TripInstanceDetailResult.Reason.STOP_METADATA_MISSING,
            reader(stopLookup = stops - "mahdorf").resolve(key),
        )
    }

    @Test
    fun frequencyServiceRequiresActualStartInstanceAndValidHeadway() {
        val frequency = GtfsScheduleFrequency(
            tripId = key.tripId,
            startTime = "24:00:00",
            endTime = "25:00:00",
            headwaySeconds = 600,
            exactTimes = 1,
        )
        val r = reader(frequency = listOf(frequency))
        assertReason(
            TripInstanceDetailResult.Reason.FREQUENCY_INSTANCE_REQUIRED,
            r.resolve(key),
        )
        assertReason(
            TripInstanceDetailResult.Reason.FREQUENCY_INSTANCE_INVALID,
            r.resolve(key.copy(frequencyStartTime = "24:07:00")),
        )
        assertTrue(
            r.resolve(key.copy(frequencyStartTime = "24:20:00"))
                is TripInstanceDetailResult.Found,
        )
    }

    private fun reader(
        stopTimes: List<GtfsScheduleStopTime> = times,
        stopLookup: Map<String, GtfsScheduleStop> = stops,
        active: Boolean = true,
        frequency: List<GtfsScheduleFrequency> = emptyList(),
    ) = TripInstanceDetailAssembler(
        trip = { tripId -> trip.takeIf { tripId == trip.id } },
        stopTimes = { _, _ -> stopTimes },
        stop = { stopLookup[it] },
        serviceActive = { serviceId, date ->
            active && serviceId == "weekday" && date == monday
        },
        frequencyWindows = { frequency },
    )

    private fun time(id: String, sequence: Int, departure: String) = GtfsScheduleStopTime(
        tripId = key.tripId,
        stopSequence = sequence,
        stopId = id,
        arrivalTime = departure,
        departureTime = departure,
        pickupType = 0,
        dropOffType = 0,
        shapeDistanceTraveled = null,
        timepoint = null,
    )

    private fun stop(id: String, name: String, parentId: String?, platform: String) =
        GtfsScheduleStop(
            id = id,
            name = name,
            latitude = 52.0,
            longitude = 8.0,
            parentStationId = parentId,
            locationType = 0,
            platformCode = platform,
            wheelchairBoarding = null,
        )

    private fun assertReason(
        reason: TripInstanceDetailResult.Reason,
        result: TripInstanceDetailResult,
    ) {
        assertEquals(TripInstanceDetailResult.Unavailable(reason), result)
    }
}
