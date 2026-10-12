package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitFareDisclosureTest {
    private fun trip(id: String, bikesAllowed: Int?) = GtfsScheduleTrip(
        id = id,
        routeId = "R",
        serviceId = "S",
        headsign = null,
        directionId = null,
        shapeId = null,
        wheelchairAccessible = null,
        bikesAllowed = bikesAllowed,
    )

    @Test
    fun permitsOnlyWhenEveryLegHasPositiveGtfsEvidence() {
        val trips = mapOf("a" to trip("a", 1), "b" to trip("b", 1))
        assertEquals(
            BicycleCarriage.ALLOWED,
            bicycleCarriageForTripIds(listOf("a", "b"), trips::get),
        )
    }

    @Test
    fun aProhibitedTransferLegMakesWholeJourneyIncompatible() {
        val trips = mapOf("a" to trip("a", 1), "b" to trip("b", 2))
        assertEquals(
            BicycleCarriage.NOT_ALLOWED,
            bicycleCarriageForTripIds(listOf("a", "b"), trips::get),
        )
    }

    @Test
    fun absentOrUnknownDataNeverBecomesPositiveEvidence() {
        val trips = mapOf("allowed" to trip("allowed", 1), "unknown" to trip("unknown", 0))
        for (ids in listOf(
            emptyList(),
            listOf("allowed", "missing"),
            listOf("allowed", "unknown"),
            listOf("allowed", ""),
        )) {
            assertEquals(BicycleCarriage.UNKNOWN, bicycleCarriageForTripIds(ids, trips::get))
        }
    }

    @Test
    fun unrecognizedProviderValueFailsClosed() {
        assertEquals(
            BicycleCarriage.UNKNOWN,
            bicycleCarriageForTripIds(listOf("x")) { trip("x", 3) },
        )
    }

    @Test
    fun prohibitedEvidenceStillWinsWhenAnotherLegIsUnknown() {
        val trips = mapOf("no" to trip("no", 2))
        assertEquals(
            BicycleCarriage.NOT_ALLOWED,
            bicycleCarriageForTripIds(listOf("missing", "no"), trips::get),
        )
    }
}
