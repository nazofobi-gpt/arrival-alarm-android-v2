package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StationDepartureEvidenceTest {
    private val day = GtfsServiceDate.parse("20261030")

    private fun trip(
        tripId: String = "München-RE_42",
        serviceId: String = "weekday",
        routeId: String = "RE-1",
    ) = GtfsScheduleTrip(
        id = tripId,
        routeId = routeId,
        serviceId = serviceId,
        headsign = "München Hbf",
        directionId = 0,
        shapeId = null,
        wheelchairAccessible = null,
        bikesAllowed = null,
    )

    @Test fun printableUnicodeIdsRetainExactScheduledTripIdentity() {
        val evidence = stationDepartureEvidence(trip(), stopSequence = 4, serviceDate = day)
        assertNotNull(evidence)
        assertEquals("München-RE_42", evidence!!.identity.tripId)
        assertEquals("weekday", evidence.identity.serviceId)
        assertEquals("RE-1", evidence.identity.routeId)
        assertEquals(day, evidence.identity.serviceDate)
        assertEquals(4, evidence.boardingStopSequence)
    }

    @Test fun invisibleAndControlCharactersCannotProduceSelectableTripIdentity() {
        val invalid = listOf(
            trip(tripId = "trip\u202Ereversed"),
            trip(tripId = "trip\nnew-line"),
            trip(serviceId = "weekday\tentry"),
            trip(routeId = "RE\u200B1"),
            trip(tripId = "\u200B"),
        )
        for (candidate in invalid) {
            assertNull(stationDepartureEvidence(candidate, stopSequence = 4, serviceDate = day))
        }
    }

    @Test fun absentDateInvalidSequenceAndBlankIdentityFailClosed() {
        assertNull(stationDepartureEvidence(trip(), stopSequence = 4, serviceDate = null))
        assertNull(stationDepartureEvidence(trip(), stopSequence = -1, serviceDate = day))
        assertNull(stationDepartureEvidence(trip(tripId = "  "), stopSequence = 4, serviceDate = day))
        assertNull(stationDepartureEvidence(trip(serviceId = ""), stopSequence = 4, serviceDate = day))
        assertNull(stationDepartureEvidence(trip(routeId = ""), stopSequence = 4, serviceDate = day))
    }
}
