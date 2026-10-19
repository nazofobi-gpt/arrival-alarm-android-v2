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
        arrivalTime = if (sequence == 13) "24:10:00" else "08:00:00",
        departureTime = if (sequence == 13) "24:12:00" else "08:01:00",
        pickupType = null, dropOffType = null, shapeDistanceTraveled = null, timepoint = 1,
    )

    private inner class FakeSource : GtfsTripDetailSource {
        var currentTrip = trip
        var exception: GtfsScheduleCalendarDate? = null
        var frequencies = false
        var times = listOf(st(2, "A"), st(7, "B"), st(13, "A"))
        var stops = mapOf(
            "A" to GtfsScheduleStop("A", "Lohne", 52.6, 8.2, null, 0, "1", 1),
            "B" to GtfsScheduleStop("B", "Bremen Hbf", 53.1, 8.8, "PARENT", 0, "Bahnsteig 2", 1),
        )

        override fun trip(id: String): GtfsScheduleTrip? =
            currentTrip.takeIf { it.id == id }
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
