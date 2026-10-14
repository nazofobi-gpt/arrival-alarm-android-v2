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

    @Test fun corruptedProgressStoreReadFailsClosedAndAllowsFreshTracking() {
        // Android persisted values may have the wrong type after a corrupt/legacy write.
        var saved: ArrivalProgressState? = null
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? =
                throw ClassCastException("Corrupt stored arrival progress")
            override fun save(state: ArrivalProgressState) { saved = state }
        }
        val controller = ArrivalProgressController(store)
        assertEquals(ArrivalAlertPhase.IDLE, controller.state.phase)
        assertFalse(controller.state.armed)
        controller.arm(route)
        assertEquals(ArrivalAlertPhase.TRACKING, controller.state.phase)
        assertEquals(route.persistenceFingerprint, saved?.routeFingerprint)
        controller.onLocation(fix(route.stops[1]))
        assertTrue(controller.state.armed)
    }

    @Test fun outOfBoundsRestoredStopIndexCannotCreatePhantomArrival() {
        val poisoned = ArrivalProgressState(
            routeId = route.routeId,
            armed = true,
            phase = ArrivalAlertPhase.ARRIVED,
            lastMatchedStopIndex = Int.MAX_VALUE,
            progressFraction = 1.0,
            arrivalEventCount = 1,
            routeFingerprint = route.persistenceFingerprint,
        )
        var persisted: ArrivalProgressState? = poisoned
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = persisted
            override fun save(state: ArrivalProgressState) { persisted = state }
        }

        val controller = ArrivalProgressController(store)
        controller.arm(route)

        assertEquals(ArrivalAlertPhase.TRACKING, controller.state.phase)
        assertEquals(0, controller.state.lastMatchedStopIndex)
        assertEquals(0, controller.state.arrivalEventCount)
        assertEquals(route.persistenceFingerprint, persisted?.routeFingerprint)
        controller.onLocation(fix(route.stops[0]))
        assertTrue(controller.state.armed)
        assertTrue(controller.state.lastMatchedStopIndex in route.stops.indices)
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

    @Test fun poorAccuracyInterruptsArrivalDebounceWithoutFalseAlarm() {
        val c = ArrivalProgressController()
        c.arm(route)
        val target = route.stops[route.targetIndex]

        c.onLocation(fix(target))
        assertEquals(1, c.state.consecutiveInsideThreshold)
        c.onLocation(fix(target, accuracy = 500.0))
        assertEquals(0, c.state.consecutiveInsideThreshold)
        assertEquals(0, c.state.arrivalEventCount)

        c.onLocation(fix(target))
        assertEquals(1, c.state.consecutiveInsideThreshold)
        assertEquals(ArrivalAlertPhase.IMMINENT, c.state.phase)
        c.onLocation(fix(target))
        assertEquals(ArrivalAlertPhase.ARRIVED, c.state.phase)
        assertEquals(1, c.state.arrivalEventCount)
    }

    @Test fun borderlineFixBreaksTwoConsecutiveConfirmedArrivals() {
        val c = ArrivalProgressController()
        c.arm(route)
        val target = route.stops[route.targetIndex]
        val borderline = GeoPoint(target.latitude + 0.00112, target.longitude)
        val confirmed = fix(target, accuracy = 10.0, speed = 0.0)
        val uncertain = fix(borderline, accuracy = 10.0, speed = 0.0)
        val upper = ArrivalProgressController.distanceMeters(borderline, target) + uncertain.accuracyMeters
        val threshold = c.adaptiveThresholdMeters(uncertain)
        assertTrue(upper > threshold)
        assertTrue(upper < threshold * 1.20)

        c.onLocation(confirmed)
        assertEquals(1, c.state.consecutiveInsideThreshold)
        c.onLocation(uncertain)
        assertEquals(0, c.state.consecutiveInsideThreshold)
        assertEquals(0, c.state.arrivalEventCount)
        c.onLocation(confirmed)
        assertEquals(1, c.state.consecutiveInsideThreshold)
        assertEquals(ArrivalAlertPhase.IMMINENT, c.state.phase)
        c.onLocation(confirmed)
        assertEquals(ArrivalAlertPhase.ARRIVED, c.state.phase)
        assertEquals(1, c.state.arrivalEventCount)
    }

    @Test fun horizontalUncertaintyMustFitInsideAdaptiveThreshold() {
        val c = ArrivalProgressController()
        c.arm(route)
        val target = route.stops[route.targetIndex]
        val closeButUncertain = GeoPoint(target.latitude + 0.0012, target.longitude)

        // Roughly 133m distance plus 100m GPS uncertainty exceeds the 200m
        // adaptive threshold: two callbacks must not fire the arrival event.
        repeat(2) { c.onLocation(fix(closeButUncertain, accuracy = 100.0, speed = 0.0)) }
        assertEquals(0, c.state.consecutiveInsideThreshold)
        assertEquals(0, c.state.arrivalEventCount)

        repeat(2) { c.onLocation(fix(target, accuracy = 10.0, speed = 0.0)) }
        assertEquals(ArrivalAlertPhase.ARRIVED, c.state.phase)
        assertEquals(1, c.state.arrivalEventCount)
    }

    @Test fun invalidCoordinatesAndMovementEvidenceCannotAdvanceProgress() {
        val c = ArrivalProgressController()
        c.arm(route)
        val initial = c.state
        c.onLocation(fix(GeoPoint(Double.NaN, 8.79)))
        c.onLocation(fix(GeoPoint(100.0, 8.79)))
        c.onLocation(fix(route.stops[2], accuracy = Double.NaN))
        c.onLocation(fix(route.stops[2], speed = -1.0))
        c.onLocation(fix(route.stops[2], speed = Double.POSITIVE_INFINITY))
        assertEquals(initial, c.state)

        // Int.MIN_VALUE used to overflow abs(Int) and erase deviation impact.
        assertEquals(
            184.0,
            c.adaptiveThresholdMeters(fix(route.stops[0], rt = Int.MIN_VALUE)),
            0.0001,
        )
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
