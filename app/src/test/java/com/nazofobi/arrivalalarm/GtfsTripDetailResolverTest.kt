package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsTripDetailResolverTest {
    private val serviceDate = GtfsServiceDate(2026, 10, 6) // Tuesday
    private val trip = GtfsScheduleTrip(
        id = "T17", routeId = "R5", serviceId = "WEEKDAY",
        headsign = "Bremen Hbf", directionId = 1,
        shapeId = null, wheelchairAccessible = null, bikesAllowed = null,
    )

    @Test
    fun concreteTripKeepsFullOrderedSequenceAndPlatformMetadata() {
        val source = FakeSource()
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        val result = GtfsTripDetailResolver(source).resolve(identity)
        assertTrue(result is GtfsTripDetailResult.Available)
        val detail = (result as GtfsTripDetailResult.Available).detail
        assertEquals(identity, detail.identity)
        assertEquals("Bremen Hbf", detail.headsign)
        assertEquals(listOf(2, 7, 13), detail.stops.map { it.stopSequence })
        // The same physical stop may occur more than once; sequence, not stopId, is identity.
        assertEquals(listOf("A", "B", "A"), detail.stops.map { it.stopId })
        assertEquals("Bahnsteig 2", detail.stops[1].platformCode)
        assertEquals("PARENT", detail.stops[1].parentStationId)
        assertEquals("24:10:00", detail.stops[2].scheduledArrival)
    }

    @Test
    fun rejectsStaleTripWhenFeedReusesTripIdWithOtherDirection() {
        val source = FakeSource().apply { currentTrip = trip.copy(directionId = 0) }
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.TRIP_CHANGED),
            GtfsTripDetailResolver(source).resolve(
                GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
            ),
        )
    }

    @Test
    fun providerCannotSubstituteAnotherTripIdWithMatchingRouteAndService() {
        val source = FakeSource().apply {
            // Simulate a buggy/stale provider that ignores the requested lookup key.
            // Its returned stop_times are internally consistent with the wrong trip.
            allowMismatchedLookup = true
            currentTrip = trip.copy(id = "OTHER-TRIP")
            times = times.map { it.copy(tripId = "OTHER-TRIP") }
        }
        val selected = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.TRIP_CHANGED),
            GtfsTripDetailResolver(source).resolve(selected),
        )
        source.currentTrip = trip
        source.times = source.times.map { it.copy(tripId = trip.id) }
        assertTrue(GtfsTripDetailResolver(source).resolve(selected) is GtfsTripDetailResult.Available)
    }

    @Test
    fun calendarDateRemovalWinsOverWeekdayAndAddedExceptionEnablesExtraDay() {
        val source = FakeSource()
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        source.exception = GtfsScheduleCalendarDate("WEEKDAY", "20261006", 2)
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.SERVICE_INACTIVE),
            GtfsTripDetailResolver(source).resolve(identity),
        )
        // Sunday would normally be inactive. A provider ADD exception may activate it.
        source.exception = GtfsScheduleCalendarDate("WEEKDAY", "20261011", 1)
        val sunday = identity.copy(serviceDate = GtfsServiceDate(2026, 10, 11))
        assertTrue(GtfsTripDetailResolver(source).resolve(sunday) is GtfsTripDetailResult.Available)
    }

    @Test
    fun rejectsMissingMetadataAndNonMonotonicSequence() {
        val source = FakeSource()
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        source.stops = source.stops - "B"
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.MISSING_STOP_METADATA),
            GtfsTripDetailResolver(source).resolve(identity),
        )
        source.stops = FakeSource().stops
        source.times = listOf(st(2, "A"), st(2, "B"), st(13, "A"))
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.INVALID_STOP_SEQUENCE),
            GtfsTripDetailResolver(source).resolve(identity),
        )
    }

    @Test
    fun invalidProviderStopNamesAndCoordinatesNeverBecomeConfirmedTripDetails() {
        val source = FakeSource()
        val resolver = GtfsTripDetailResolver(source)
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        assertTrue(resolver.resolve(identity) is GtfsTripDetailResult.Available)
        val validStop = requireNotNull(source.stops["B"])
        val malformed = listOf(
            validStop.copy(name = "  "),
            validStop.copy(latitude = Double.NaN),
            validStop.copy(longitude = Double.NaN),
            validStop.copy(latitude = Double.POSITIVE_INFINITY),
            validStop.copy(longitude = Double.NEGATIVE_INFINITY),
            validStop.copy(latitude = 90.001),
            validStop.copy(latitude = -90.001),
            validStop.copy(longitude = 180.001),
            validStop.copy(longitude = -180.001),
        )
        malformed.forEach { badStop ->
            source.stops = source.stops + ("B" to badStop)
            assertEquals(
                GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.MISSING_STOP_METADATA),
                resolver.resolve(identity),
            )
        }
        source.stops = source.stops + ("B" to validStop)
        assertTrue(resolver.resolve(identity) is GtfsTripDetailResult.Available)
    }

    @Test
    fun rejectedStopIdentityMismatchCannotLabelWrongPhysicalStation() {
        val source = FakeSource()
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        val originallyValid = requireNotNull(source.stops["B"])
        // A source returning a different stop than requested must fail closed.
        source.stops = source.stops + ("B" to originallyValid.copy(id = "WRONG-STOP"))
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.MISSING_STOP_METADATA),
            GtfsTripDetailResolver(source).resolve(identity),
        )
        source.stops = source.stops + ("B" to originallyValid)
        assertTrue(GtfsTripDetailResolver(source).resolve(identity) is GtfsTripDetailResult.Available)
    }

    @Test
    fun rejectsReversedDwellWhilePreservingValidExtendedServiceDayTimes() {
        val source = FakeSource()
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        val resolver = GtfsTripDetailResolver(source)
        val original = source.times

        source.times = original.mapIndexed { index, stop ->
            if (index == 1) stop.copy(arrivalTime = "09:05:00", departureTime = "09:00:00")
            else stop
        }
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.INVALID_STOP_TIME),
            resolver.resolve(identity),
        )

        // A legitimate overnight dwell and a zero-length dwell are still valid.
        for (departure in listOf("24:05:00", "24:03:00")) {
            source.times = original.mapIndexed { index, stop ->
                if (index == 1) stop.copy(
                    arrivalTime = "24:03:00",
                    departureTime = departure,
                ) else stop
            }
            assertTrue(resolver.resolve(identity) is GtfsTripDetailResult.Available)
        }
    }

    @Test
    fun malformedScheduledStopClocksCannotBecomeConfirmableTripDetails() {
        val source = FakeSource()
        val resolver = GtfsTripDetailResolver(source)
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        val original = source.times
        val malformed = listOf("08:60:00", "08:00:60", "08:00", "08:00:00junk", "-1:00:00", "1000:00:00", "")
        for (clock in malformed) {
            source.times = original.mapIndexed { index, stop ->
                if (index == 1) stop.copy(departureTime = clock) else stop
            }
            assertEquals(
                "Invalid departure clock must be rejected: $clock",
                GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.INVALID_STOP_TIME),
                resolver.resolve(identity),
            )
        }
        source.times = original.mapIndexed { index, stop ->
            if (index == 1) stop.copy(arrivalTime = "24:99:00") else stop
        }
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.INVALID_STOP_TIME),
            resolver.resolve(identity),
        )
        // Extended service-day clocks stay supported. A non-timepoint may be null.
        source.times = original.mapIndexed { index, stop ->
            when (index) {
                1 -> stop.copy(arrivalTime = null, departureTime = "26:05:07")
                2 -> stop.copy(arrivalTime = "26:10:00", departureTime = "26:12:00")
                else -> stop
            }
        }
        assertTrue(resolver.resolve(identity) is GtfsTripDetailResult.Available)
    }

    @Test
    fun rejectsBackwardsTripClocksAcrossStopsIncludingMissingNonTimepoints() {
        val source = FakeSource()
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        val resolver = GtfsTripDetailResolver(source)
        val original = source.times

        // Both stops have valid, non-reversed dwell times, but time travels
        // backwards from the first departure to the next arrival.
        source.times = listOf(
            original[0].copy(arrivalTime = "08:10:00", departureTime = "08:11:00"),
            original[1].copy(arrivalTime = "08:09:00", departureTime = "08:12:00"),
            original[2],
        )
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.INVALID_STOP_TIME),
            resolver.resolve(identity),
        )

        // A skipped non-timepoint must not hide reversed known times.
        source.times = listOf(
            original[0].copy(arrivalTime = "08:10:00", departureTime = "08:11:00"),
            original[1].copy(arrivalTime = null, departureTime = null),
            original[2].copy(arrivalTime = "08:09:59", departureTime = "08:12:00"),
        )
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.INVALID_STOP_TIME),
            resolver.resolve(identity),
        )

        // Legitimate overnight service-day clocks and equal stop-boundary
        // times remain a confirmable, chronologically ordered trip.
        source.times = listOf(
            original[0].copy(arrivalTime = "23:58:00", departureTime = "23:59:00"),
            original[1].copy(arrivalTime = "24:00:00", departureTime = "24:01:00"),
            original[2].copy(arrivalTime = "24:01:00", departureTime = "24:04:00"),
        )
        assertTrue(resolver.resolve(identity) is GtfsTripDetailResult.Available)
    }

    @Test
    fun rejectsFrequencyTemplateAndPotentiallyTruncatedSequence() {
        val source = FakeSource()
        val identity = GtfsTripInstanceIdentity.scheduled(trip, serviceDate)
        source.frequencies = true
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.FREQUENCY_INSTANCE_UNSUPPORTED),
            GtfsTripDetailResolver(source).resolve(identity),
        )
        source.frequencies = false
        source.times = (1..2048).map { st(it, if (it % 2 == 0) "B" else "A") }
        assertEquals(
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.MISSING_OR_TRUNCATED_SEQUENCE),
            GtfsTripDetailResolver(source).resolve(identity),
        )
    }

    private fun st(sequence: Int, stopId: String) = GtfsScheduleStopTime(
        tripId = trip.id, stopSequence = sequence, stopId = stopId,
        arrivalTime = when (sequence) {
            13 -> "24:10:00"
            7 -> "08:02:00"
            else -> "08:00:00"
        },
        departureTime = when (sequence) {
            13 -> "24:12:00"
            7 -> "08:03:00"
            else -> "08:01:00"
        },
        pickupType = null, dropOffType = null, shapeDistanceTraveled = null, timepoint = 1,
    )

    private inner class FakeSource : GtfsTripDetailSource {
        var currentTrip = trip
        var allowMismatchedLookup = false
        var exception: GtfsScheduleCalendarDate? = null
        var frequencies = false
        var times = listOf(st(2, "A"), st(7, "B"), st(13, "A"))
        var stops = mapOf(
            "A" to GtfsScheduleStop("A", "Lohne", 52.6, 8.2, null, 0, "1", 1),
            "B" to GtfsScheduleStop("B", "Bremen Hbf", 53.1, 8.8, "PARENT", 0, "Bahnsteig 2", 1),
        )

        override fun trip(id: String): GtfsScheduleTrip? =
            currentTrip.takeIf { allowMismatchedLookup || it.id == id }
        override fun stopTimes(tripId: String, limit: Int): List<GtfsScheduleStopTime> =
            times.take(limit)
        override fun stop(id: String): GtfsScheduleStop? = stops[id]
        override fun calendar(serviceId: String): GtfsScheduleCalendar? =
            GtfsScheduleCalendar(
                serviceId = "WEEKDAY",
                monday = true, tuesday = true, wednesday = true, thursday = true,
                friday = true, saturday = false, sunday = false,
                startDate = "20261001", endDate = "20261031",
            ).takeIf { serviceId == "WEEKDAY" }
        override fun calendarException(serviceId: String, date: String): GtfsScheduleCalendarDate? =
            exception?.takeIf { it.serviceId == serviceId && it.date == date }
        override fun hasFrequencies(tripId: String): Boolean = frequencies
    }
}
