package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalLocationQualityTest {
    private fun evidence(
        distance: Double = 200.0,
        accuracy: Double? = 15.0,
        ageMillis: Long? = 1_000L,
    ) = ArrivalLocationEvidence(distance, accuracy, ageMillis)

    @Test fun freshPreciseFixCanConfirmArrival() {
        assertTrue(ArrivalLocationQuality.isUsableForTracking(evidence()))
        assertTrue(ArrivalLocationQuality.confirmsArrival(evidence()))
        assertFalse(ArrivalLocationQuality.confirmsArrival(evidence(distance = 260.0)))
    }

    @Test fun missingUnreliableOrStaleFixIsRejected() {
        assertFalse(ArrivalLocationQuality.isUsableForTracking(evidence(accuracy = null)))
        assertFalse(ArrivalLocationQuality.isUsableForTracking(evidence(accuracy = 200.0)))
        assertFalse(ArrivalLocationQuality.isUsableForTracking(evidence(accuracy = Double.NaN)))
        assertFalse(ArrivalLocationQuality.isUsableForTracking(evidence(ageMillis = null)))
        assertFalse(ArrivalLocationQuality.isUsableForTracking(evidence(ageMillis = -1L)))
        assertFalse(ArrivalLocationQuality.isUsableForTracking(evidence(ageMillis = 30_001L)))
        assertFalse(ArrivalLocationQuality.isUsableForTracking(evidence(distance = Double.NaN)))
        assertFalse(ArrivalLocationQuality.confirmsArrival(evidence(accuracy = 200.0)))
        assertFalse(ArrivalLocationQuality.confirmsArrival(evidence(ageMillis = 60_000L)))
    }

    @Test fun uncertaintyCircleMustFitInsideArrivalRadius() {
        // GPS reports 200 m, but actual location might still be 280 m away.
        assertTrue(ArrivalLocationQuality.isUsableForTracking(evidence(accuracy = 80.0)))
        assertFalse(ArrivalLocationQuality.confirmsArrival(evidence(accuracy = 80.0)))
        assertTrue(ArrivalLocationQuality.confirmsArrival(evidence(distance = 170.0, accuracy = 80.0)))
        assertFalse(ArrivalLocationQuality.confirmsArrival(evidence(), radiusMeters = -1.0))
    }

    @Test fun lowConfidenceLocationUpdatesDistanceWithoutFiringAlarm() {
        val journey = JourneyController()
        journey.selectStart(GeoPoint(53.08, 8.81))
        journey.selectDestination(GeoPoint(53.06, 8.79))
        journey.arm()

        val uncertain = evidence(distance = 200.0, accuracy = 80.0)
        journey.onDistanceChanged(uncertain.distanceMeters, allowArrival = ArrivalLocationQuality.confirmsArrival(uncertain))
        assertEquals(JourneyPhase.ARMED, journey.state.phase)
        assertEquals(200.0, journey.state.distanceMeters!!, 0.01)

        val confirmed = evidence(distance = 180.0, accuracy = 20.0)
        journey.onDistanceChanged(confirmed.distanceMeters, allowArrival = ArrivalLocationQuality.confirmsArrival(confirmed))
        assertEquals(JourneyPhase.ARRIVED, journey.state.phase)
    }
}
