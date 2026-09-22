package com.nazofobi.arrivalalarm

import org.junit.Assert.*
import org.junit.Test

class ArrivalProgressControllerTest {
    private val route = ArmedRoute(
        routeId = "vbn-6-test",
        stops = listOf(
            GeoPoint(53.0830, 8.8130),
            GeoPoint(53.0735, 8.8065),
            GeoPoint(53.0618, 8.7908),
            GeoPoint(53.0475, 8.7867),
        ),
        targetIndex = 2,
    )

    private fun fix(point: GeoPoint, accuracy: Double = 10.0, speed: Double = 8.0, rt: Int = 0) =
        LocationFix(point, accuracy, speed, rt)

    @Test fun noRouteMeansNoAutonomousTarget() {
        val c = ArrivalProgressController()
        c.onLocation(fix(route.stops[0]))
        assertFalse(c.state.armed)
        assertEquals(ArrivalAlertPhase.IDLE, c.state.phase)
        assertEquals(0, c.state.arrivalEventCount)
    }

    @Test fun adaptiveThresholdIsBoundedAndRespondsToUncertainty() {
        val c = ArrivalProgressController()
        val clean = c.adaptiveThresholdMeters(fix(route.stops[0], accuracy = 3.0, speed = 0.0, rt = 0))
        val uncertain = c.adaptiveThresholdMeters(fix(route.stops[0], accuracy = 80.0, speed = 25.0, rt = 300))
        assertTrue(uncertain > clean)
        assertTrue(clean >= 120.0)
        assertTrue(uncertain <= 320.0)
    }

    @Test fun tripProgressNeverRegressesUnderGpsJitter() {
        val c = ArrivalProgressController()
        c.arm(route)
        c.onLocation(fix(route.stops[1]))
        val afterForward = c.state
        c.onLocation(fix(route.stops[0]))
        assertTrue(c.state.lastMatchedStopIndex >= afterForward.lastMatchedStopIndex)
        assertTrue(c.state.progressFraction >= afterForward.progressFraction)
    }

    @Test fun arrivalNeedsDebounceAndFiresExactlyOnce() {
        val c = ArrivalProgressController()
        c.arm(route)
        val target = route.stops[route.targetIndex]
        c.onLocation(fix(target))
        assertEquals(ArrivalAlertPhase.IMMINENT, c.state.phase)
        assertEquals(0, c.state.arrivalEventCount)
        c.onLocation(fix(target))
        assertEquals(ArrivalAlertPhase.ARRIVED, c.state.phase)
        assertEquals(1, c.state.arrivalEventCount)
        c.onLocation(fix(route.stops.last()))
        assertEquals(ArrivalAlertPhase.ARRIVED, c.state.phase)
        assertEquals(1, c.state.arrivalEventCount)
    }

    @Test fun overshootBecomesMissedOnlyAfterTwoPastTargetFixes() {
        val c = ArrivalProgressController()
        c.arm(route)
        val pastTarget = route.stops[3]
        c.onLocation(fix(pastTarget))
        assertNotEquals(ArrivalAlertPhase.MISSED, c.state.phase)
        c.onLocation(fix(pastTarget))
        assertEquals(ArrivalAlertPhase.MISSED, c.state.phase)
        assertEquals(0, c.state.arrivalEventCount)
    }

    @Test fun persistedStateResumesWithoutDuplicateArrival() {
        var saved: ArrivalProgressState? = null
        val store = object : ArrivalProgressStateStore {
            override fun load() = saved
            override fun save(state: ArrivalProgressState) { saved = state }
        }
        val first = ArrivalProgressController(store)
        first.arm(route)
        first.onLocation(fix(route.stops[1]))
        val before = first.state

        val restored = ArrivalProgressController(store)
        restored.arm(route)
        assertEquals(before.lastMatchedStopIndex, restored.state.lastMatchedStopIndex)
        assertEquals(before.phase, restored.state.phase)

        val target = route.stops[2]
        restored.onLocation(fix(target))
        restored.onLocation(fix(target))
        assertEquals(1, restored.state.arrivalEventCount)

        val afterArrival = ArrivalProgressController(store)
        afterArrival.arm(route)
        afterArrival.onLocation(fix(route.stops[3]))
        assertEquals(ArrivalAlertPhase.ARRIVED, afterArrival.state.phase)
        assertEquals(1, afterArrival.state.arrivalEventCount)
    }
}
