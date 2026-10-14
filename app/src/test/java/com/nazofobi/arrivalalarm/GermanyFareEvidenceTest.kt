package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanyFareEvidenceTest {
    @Test
    fun allLegsExplicitlyAllowBicycles() {
        assertEquals(
            BicycleAllowance.ALLOWED,
            GermanyFareEvidence.bicycleAllowance(listOf(1, 1, 1)),
        )
    }

    @Test
    fun prohibitionOnAnyLegOverridesOtherLegs() {
        assertEquals(
            BicycleAllowance.DISALLOWED,
            GermanyFareEvidence.bicycleAllowance(listOf(1, null, 2)),
        )
    }

    @Test
    fun absentOrUnspecifiedTripEvidenceIsUnknown() {
        listOf(emptyList(), listOf(null), listOf(0), listOf(1, null), listOf(1, 0)).forEach {
            assertEquals(BicycleAllowance.UNKNOWN, GermanyFareEvidence.bicycleAllowance(it))
        }
    }

    @Test
    fun unsupportedGtfsCodesCannotBePromotedToAllowed() {
        assertEquals(BicycleAllowance.UNKNOWN, GermanyFareEvidence.bicycleAllowance(listOf(1, 9)))
    }

    @Test
    fun providerOnlyRouteStartsUnknown() {
        val point = MapPoint(52.66, 8.23, "Lohne")
        val route = RouteOption(
            id = "provider", origin = point, destination = point,
            line = "unknown", direction = "unknown",
            departure = "10:00", arrival = "10:20",
            walkingMinutes = 0, transfers = 0,
        )
        assertEquals(BicycleAllowance.UNKNOWN, route.bicycleAllowance)
    }
}
