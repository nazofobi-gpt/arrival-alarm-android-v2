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

    @Test fun malformedGpsCoordinatesCannotMasqueradeAsArrival() {
        val destination = GeoPoint(53.08, 8.81)
        // Haversine is periodic: adding 360 degrees of latitude maps back to
        // nearly zero calculated distance unless the raw fix is validated first.
        val corrupt = GeoPoint(destination.latitude + 360.0, destination.longitude)
        assertTrue(ArrivalProgressController.distanceMeters(corrupt, destination) < 1.0)
        assertFalse(ArrivalLocationQuality.hasValidCoordinates(corrupt.latitude, corrupt.longitude))
        assertFalse(ArrivalLocationQuality.hasValidCoordinates(90.01, 8.81))
        assertFalse(ArrivalLocationQuality.hasValidCoordinates(53.08, -180.01))
        assertFalse(ArrivalLocationQuality.hasValidCoordinates(Double.NaN, 8.81))
        assertFalse(ArrivalLocationQuality.hasValidCoordinates(53.08, Double.POSITIVE_INFINITY))
    }

    @Test fun validGpsCoordinateBoundariesArePreserved() {
        assertTrue(ArrivalLocationQuality.hasValidCoordinates(53.08, 8.81))
        assertTrue(ArrivalLocationQuality.hasValidCoordinates(-90.0, -180.0))
        assertTrue(ArrivalLocationQuality.hasValidCoordinates(90.0, 180.0))
    }

    @Test fun lowConfidenceFixUpdatesDistanceAndTwoFreshPreciseFixesAreRequiredForArrival() {
        val journey = JourneyController()
        journey.selectStart(GeoPoint(53.08, 8.81))
        journey.selectDestination(GeoPoint(53.06, 8.79))
        journey.arm()

        val uncertain = evidence(distance = 200.0, accuracy = 80.0)
        journey.onDistanceChanged(
            uncertain.distanceMeters,
            allowArrival = ArrivalLocationQuality.confirmsArrival(uncertain),
            fixElapsedRealtimeNanos = 1_000_000_000L,
        )
        assertEquals(JourneyPhase.ARMED, journey.state.phase)
        assertEquals(200.0, journey.state.distanceMeters!!, 0.01)

        val confirmed = evidence(distance = 180.0, accuracy = 20.0)
        journey.onDistanceChanged(
            confirmed.distanceMeters,
            allowArrival = ArrivalLocationQuality.confirmsArrival(confirmed),
            fixElapsedRealtimeNanos = 2_000_000_000L,
        )
        assertEquals(JourneyPhase.ARMED, journey.state.phase)
        assertEquals(180.0, journey.state.distanceMeters!!, 0.01)

        val secondConfirmed = evidence(distance = 175.0, accuracy = 20.0)
        journey.onDistanceChanged(
            secondConfirmed.distanceMeters,
            allowArrival = ArrivalLocationQuality.confirmsArrival(secondConfirmed),
            fixElapsedRealtimeNanos = 3_000_000_000L,
        )
        assertEquals(JourneyPhase.ARRIVED, journey.state.phase)
        assertEquals(175.0, journey.state.distanceMeters!!, 0.01)
    }
}
