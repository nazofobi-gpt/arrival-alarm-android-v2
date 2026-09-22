package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyControllerTest {
    @Test fun happyPathReachesArrived() {
        val c = JourneyController(arrivalRadiusMeters = 250.0)
        c.selectStart(GeoPoint(1.0, 1.0))
        c.selectDestination(GeoPoint(2.0, 2.0))
        c.arm()
        c.onDistanceChanged(500.0)
        assertEquals(JourneyPhase.ARMED, c.state.phase)
        c.onDistanceChanged(200.0)
        assertEquals(JourneyPhase.ARRIVED, c.state.phase)
    }

    @Test fun destinationWithoutStartIsExplicitError() {
        val c = JourneyController()
        c.selectDestination(GeoPoint(2.0, 2.0))
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
        assertNotNull(c.state.error)
    }

    @Test fun distanceUpdatesDoNotArriveBeforeArmed() {
        val c = JourneyController()
        c.selectStart(GeoPoint(1.0, 1.0))
        c.selectDestination(GeoPoint(2.0, 2.0))
        c.onDistanceChanged(0.0)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
    }

    private fun route() = listOf(
        RouteCheckpoint("a", "A", GeoPoint(53.0, 8.000)),
        RouteCheckpoint("b", "B", GeoPoint(53.0, 8.002)),
        RouteCheckpoint("c", "C", GeoPoint(53.0, 8.004)),
        RouteCheckpoint("d", "D", GeoPoint(53.0, 8.006)),
    )

    @Test fun adaptiveArrivalProgressIsMonotonicAndBounded() {
        val r = route()
        val e = ArrivalProgressEngine()
        e.arm(r, "d")
        val nearC = e.update(LocationFix(r[2].point, 8.0, 5.0))
        val backAtB = e.update(LocationFix(r[1].point, 500.0, 80.0))
        assertEquals(2, nearC.currentRouteIndex)
        assertEquals(2, backAtB.currentRouteIndex)
        assertTrue(backAtB.thresholdMeters!! <= 420.0)
    }

    @Test fun arrivalAlarmRequiresConfirmationAndFiresOnce() {
        val r = route()
        val e = ArrivalProgressEngine(finalConfirmations = 2)
        e.arm(r, "c")
        val first = e.update(LocationFix(r[2].point, 5.0, 0.0))
        val second = e.update(LocationFix(r[2].point, 5.0, 0.0))
        val third = e.update(LocationFix(r[2].point, 5.0, 0.0))
        assertEquals(ArrivalAlertState.FINAL_APPROACH, first.state)
        assertFalse(first.shouldFireFinalAlarm)
        assertTrue(second.shouldFireFinalAlarm)
        assertFalse(third.shouldFireFinalAlarm)
    }

    @Test fun passingTargetAndProcessRestoreAreFailSafe() {
        val r = route()
        val missed = ArrivalProgressEngine(baseRadiusMeters = 50.0, minRadiusMeters = 40.0, maxRadiusMeters = 80.0)
        missed.arm(r, "c")
        missed.update(LocationFix(r[1].point, 5.0, 5.0))
        assertEquals(
            ArrivalAlertState.MISSED_STOP,
            missed.update(LocationFix(r[3].point, 5.0, 5.0)).state,
        )

        val original = ArrivalProgressEngine(finalConfirmations = 1)
        original.arm(r, "c")
        assertTrue(original.update(LocationFix(r[2].point, 5.0, 0.0)).shouldFireFinalAlarm)
        val restored = ArrivalProgressEngine(finalConfirmations = 1)
        restored.arm(r, "c", original.snapshot())
        assertFalse(restored.update(LocationFix(r[2].point, 5.0, 0.0)).shouldFireFinalAlarm)
    }

    @Test fun missingRouteNeverArmsAutonomousTarget() {
        val e = ArrivalProgressEngine()
        assertEquals(ArrivalAlertState.IDLE, e.arm(emptyList(), "c").state)
        assertFalse(e.update(LocationFix(GeoPoint(53.0, 8.0), 5.0, 0.0)).shouldFireFinalAlarm)
    }
}
