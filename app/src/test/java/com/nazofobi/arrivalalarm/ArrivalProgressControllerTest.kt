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

    @Test fun invalidArmedRouteCoordinatesAreRejected() {
        val invalidStops = listOf(
            GeoPoint(Double.NaN, 8.80),
            GeoPoint(Double.POSITIVE_INFINITY, 8.80),
            GeoPoint(53.0, Double.NEGATIVE_INFINITY),
            GeoPoint(90.01, 8.80),
            GeoPoint(-90.01, 8.80),
            GeoPoint(53.0, 180.01),
            GeoPoint(53.0, -180.01),
        )
        invalidStops.forEachIndexed { index, point ->
            for (position in 0..1) {
                val stops = mutableListOf(GeoPoint(53.0, 8.8), GeoPoint(53.1, 8.9))
                stops[position] = point
                try {
                    ArmedRoute("invalid-$index-$position", stops, targetIndex = 1)
                    fail("Invalid route stop must not be armed: $point")
                } catch (_: IllegalArgumentException) {
                    // Fail closed before any location or arrival state is created.
                }
            }
        }
    }

    @Test fun boundaryRouteCoordinatesRemainValid() {
        val boundary = ArmedRoute(
            "boundary",
            stops = listOf(GeoPoint(-90.0, -180.0), GeoPoint(90.0, 180.0)),
            targetIndex = 1,
        )
        assertEquals(1, boundary.targetIndex)
        assertEquals(2, boundary.stops.size)
    }

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

    @Test fun armedIdleSnapshotCannotRestoreAsActiveTrackingPhase() {
        var saved: ArrivalProgressState? = ArrivalProgressState(
            routeId = route.routeId,
            armed = true,
            phase = ArrivalAlertPhase.IDLE,
            lastMatchedStopIndex = route.targetIndex,
            progressFraction = 1.0,
            distanceToTargetMeters = 0.0,
            consecutiveInsideThreshold = 1,
            routeFingerprint = route.persistenceFingerprint,
        )
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = saved
            override fun save(state: ArrivalProgressState) { saved = state }
        }
        val controller = ArrivalProgressController(store)
        controller.arm(route)

        // A corrupted ARMED/IDLE pair must be reset, not surfaced as a live
        // journey with a stale terminal distance or a borrowed GPS confirmation.
        assertTrue(controller.state.armed)
        assertEquals(ArrivalAlertPhase.TRACKING, controller.state.phase)
        assertEquals(0, controller.state.lastMatchedStopIndex)
        assertEquals(0.0, controller.state.progressFraction, 0.0)
        assertNull(controller.state.distanceToTargetMeters)
        assertEquals(0, controller.state.consecutiveInsideThreshold)
        assertEquals(0, controller.state.arrivalEventCount)
        assertEquals(ArrivalAlertPhase.TRACKING, saved?.phase)

        // The valid route is still usable; arrival needs two fresh fixes.
        controller.onLocation(fix(route.stops[route.targetIndex]))
        assertEquals(0, controller.state.arrivalEventCount)
        controller.onLocation(fix(route.stops[route.targetIndex]))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }

    @Test fun callerMutationOfTargetStopCannotRetargetAnArmedAlarm() {
        val suppliedStops = route.stops.toMutableList()
        val suppliedRoute = ArmedRoute("mutable-stop-target", suppliedStops, targetIndex = 2)
        val originalFingerprint = suppliedRoute.persistenceFingerprint
        val controller = ArrivalProgressController()
        controller.arm(suppliedRoute)

        // Transit data refresh may reuse and mutate its original list.
        // An active alarm must keep the target geometry it was armed with.
        suppliedStops[2] = GeoPoint(0.0, 0.0)
        val originalTarget = route.stops[2]
        controller.onLocation(fix(originalTarget, speed = 0.0))
        controller.onLocation(fix(originalTarget, speed = 0.0))

        assertEquals(originalFingerprint, controller.state.routeFingerprint)
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }

    @Test fun clearingCallerOwnedStopsAfterArmCannotCrashGpsProgress() {
        val suppliedStops = route.stops.toMutableList()
        val suppliedRoute = ArmedRoute("mutable-empty-stops", suppliedStops, targetIndex = 2)
        val controller = ArrivalProgressController()
        controller.arm(suppliedRoute)

        suppliedStops.clear()
        controller.onLocation(fix(route.stops[2], speed = 0.0))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        controller.onLocation(fix(route.stops[2], speed = 0.0))

        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
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

    @Test fun invalidRestoredArrivalEventCountCannotSuppressFutureGpsTracking() {
        // A persisted ARRIVED phase with no recorded event cannot be trusted:
        // onLocation otherwise returns early and never rechecks the target.
        for (count in listOf(0, -1)) {
            var persisted: ArrivalProgressState? = ArrivalProgressState(
                routeId = route.routeId,
                armed = true,
                phase = ArrivalAlertPhase.ARRIVED,
                lastMatchedStopIndex = route.targetIndex,
                progressFraction = 0.75,
                arrivalEventCount = count,
                routeFingerprint = route.persistenceFingerprint,
            )
            val store = object : ArrivalProgressStateStore {
                override fun load(): ArrivalProgressState? = persisted
                override fun save(state: ArrivalProgressState) { persisted = state }
            }
            val controller = ArrivalProgressController(store)
            controller.arm(route)
            assertEquals(ArrivalAlertPhase.TRACKING, controller.state.phase)
            assertEquals(0, controller.state.arrivalEventCount)
            assertEquals(0, controller.state.lastMatchedStopIndex)
            controller.onLocation(fix(route.stops[route.targetIndex]))
            assertEquals(1, controller.state.consecutiveInsideThreshold)
            controller.onLocation(fix(route.stops[route.targetIndex]))
            assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
            assertEquals(1, controller.state.arrivalEventCount)
        }
    }

    @Test fun negativeRestoredEventCountCannotPropagateIntoNewArrival() {
        var persisted: ArrivalProgressState? = ArrivalProgressState(
            routeId = route.routeId,
            armed = true,
            phase = ArrivalAlertPhase.TRACKING,
            arrivalEventCount = -1,
            routeFingerprint = route.persistenceFingerprint,
        )
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = persisted
            override fun save(state: ArrivalProgressState) { persisted = state }
        }
        val controller = ArrivalProgressController(store)
        controller.arm(route)
        repeat(2) { controller.onLocation(fix(route.stops[route.targetIndex])) }
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }

    @Test fun invalidRestoredProgressFractionCannotPreserveTerminalArrival() {
        val invalidFractions = listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -0.1,
            1.01,
        )
        invalidFractions.forEach { invalidFraction ->
            var persisted: ArrivalProgressState? = ArrivalProgressState(
                routeId = route.routeId,
                armed = true,
                phase = ArrivalAlertPhase.ARRIVED,
                lastMatchedStopIndex = 1,
                progressFraction = invalidFraction,
                arrivalEventCount = 1,
                routeFingerprint = route.persistenceFingerprint,
            )
            val store = object : ArrivalProgressStateStore {
                override fun load(): ArrivalProgressState? = persisted
                override fun save(state: ArrivalProgressState) { persisted = state }
            }

            val controller = ArrivalProgressController(store)
            controller.arm(route)

            assertEquals("Restored fraction $invalidFraction", ArrivalAlertPhase.TRACKING, controller.state.phase)
            assertEquals(0.0, controller.state.progressFraction, 0.0)
            assertEquals(0, controller.state.arrivalEventCount)
            assertEquals(0, controller.state.lastMatchedStopIndex)
            controller.onLocation(fix(route.stops[0]))
            assertTrue(controller.state.progressFraction.isFinite())
            assertTrue(controller.state.progressFraction in 0.0..1.0)
            assertEquals(0, controller.state.arrivalEventCount)
        }
    }

    @Test fun corruptRestoredMetricsAreDiscardedWithoutLosingTerminalOneShotState() {
        val invalidPairs = listOf(
            Double.NaN to Double.NaN,
            Double.POSITIVE_INFINITY to Double.NEGATIVE_INFINITY,
            -1.0 to 119.0,
            Double.NEGATIVE_INFINITY to 321.0,
        )
        for (phase in listOf(ArrivalAlertPhase.TRACKING, ArrivalAlertPhase.ARRIVED)) {
            for ((distance, threshold) in invalidPairs) {
                var persisted: ArrivalProgressState? = ArrivalProgressState(
                    routeId = route.routeId,
                    armed = true,
                    phase = phase,
                    lastMatchedStopIndex = route.targetIndex,
                    progressFraction = 0.5,
                    distanceToTargetMeters = distance,
                    adaptiveThresholdMeters = threshold,
                    arrivalEventCount = if (phase == ArrivalAlertPhase.ARRIVED) 1 else 0,
                    routeFingerprint = route.persistenceFingerprint,
                )
                val store = object : ArrivalProgressStateStore {
                    override fun load(): ArrivalProgressState? = persisted
                    override fun save(state: ArrivalProgressState) { persisted = state }
                }

                val controller = ArrivalProgressController(store)
                controller.arm(route)
                assertNull(controller.state.distanceToTargetMeters)
                assertNull(controller.state.adaptiveThresholdMeters)
                assertEquals(route.targetIndex, controller.state.lastMatchedStopIndex)
                assertEquals(phase, controller.state.phase)
                assertEquals(if (phase == ArrivalAlertPhase.ARRIVED) 1 else 0, controller.state.arrivalEventCount)
                assertNull(persisted?.distanceToTargetMeters)
                assertNull(persisted?.adaptiveThresholdMeters)

                if (phase == ArrivalAlertPhase.ARRIVED) {
                    controller.onLocation(fix(route.stops[route.targetIndex]))
                    assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
                    assertEquals(1, controller.state.arrivalEventCount)
                }
            }
        }
    }

    @Test fun validRestoredDistanceAndThresholdRemainAvailable() {
        val state = ArrivalProgressState(
            routeId = route.routeId,
            armed = true,
            phase = ArrivalAlertPhase.IMMINENT,
            progressFraction = 0.5,
            distanceToTargetMeters = 42.0,
            adaptiveThresholdMeters = 180.0,
            routeFingerprint = route.persistenceFingerprint,
        )
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = state
            override fun save(state: ArrivalProgressState) = Unit
        }
        val controller = ArrivalProgressController(store)
        controller.arm(route)
        assertEquals(ArrivalAlertPhase.IMMINENT, controller.state.phase)
        assertEquals(42.0, controller.state.distanceToTargetMeters!!, 0.0)
        assertEquals(180.0, controller.state.adaptiveThresholdMeters!!, 0.0)
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

    @Test fun repeatedOrOlderTimestampCannotSatisfyArrivalDebounce() {
        val controller = ArrivalProgressController(nowEpochMillis = { 1_700_000_001_000L })
        controller.arm(route)
        val target = route.stops[route.targetIndex]
        fun gps(epoch: Long) = LocationFix(target, 10.0, 0.0, epochMillis = epoch)

        controller.onLocation(gps(1_700_000_000_000L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        controller.onLocation(gps(1_700_000_000_000L))
        controller.onLocation(gps(1_699_999_999_999L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        assertEquals(0, controller.state.arrivalEventCount)

        controller.onLocation(gps(1_700_000_001_000L))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }

    @Test fun repeatedTimestampCannotSatisfyMissedStopDebounce() {
        val controller = ArrivalProgressController(nowEpochMillis = { 1_700_000_001_000L })
        controller.arm(route)
        val pastTarget = route.stops.last()
        fun gps(epoch: Long) = LocationFix(pastTarget, 10.0, 0.0, epochMillis = epoch)

        controller.onLocation(gps(1_700_000_000_000L))
        assertEquals(1, controller.state.consecutivePastTarget)
        controller.onLocation(gps(1_700_000_000_000L))
        assertEquals(1, controller.state.consecutivePastTarget)
        assertNotEquals(ArrivalAlertPhase.MISSED, controller.state.phase)

        controller.onLocation(gps(1_700_000_001_000L))
        assertEquals(ArrivalAlertPhase.MISSED, controller.state.phase)
    }

    @Test fun switchingTimestampModeCannotReuseAnArrivalConfirmation() {
        val target = route.stops[route.targetIndex]
        val timed = LocationFix(target, 10.0, 0.0, epochMillis = 1_700_000_000_000L)
        val later = timed.copy(epochMillis = 1_700_000_000_001L)
        val untimed = timed.copy(epochMillis = 0L)

        val timedFirst = ArrivalProgressController(nowEpochMillis = { 1_700_000_002_000L })
        timedFirst.arm(route)
        timedFirst.onLocation(timed)
        timedFirst.onLocation(untimed)
        assertEquals(1, timedFirst.state.consecutiveInsideThreshold)
        assertEquals(0, timedFirst.state.arrivalEventCount)
        timedFirst.onLocation(untimed)
        assertEquals(ArrivalAlertPhase.ARRIVED, timedFirst.state.phase)
        assertEquals(1, timedFirst.state.arrivalEventCount)

        val untimedFirst = ArrivalProgressController(nowEpochMillis = { 1_700_000_002_000L })
        untimedFirst.arm(route)
        untimedFirst.onLocation(untimed)
        untimedFirst.onLocation(timed)
        assertEquals(1, untimedFirst.state.consecutiveInsideThreshold)
        assertEquals(0, untimedFirst.state.arrivalEventCount)
        untimedFirst.onLocation(later)
        assertEquals(ArrivalAlertPhase.ARRIVED, untimedFirst.state.phase)
        assertEquals(1, untimedFirst.state.arrivalEventCount)
    }

    @Test fun switchingTimestampModeCannotReuseAMissedStopConfirmation() {
        val past = route.stops.last()
        val timed = LocationFix(past, 10.0, 0.0, epochMillis = 1_700_000_000_000L)
        val later = timed.copy(epochMillis = 1_700_000_000_001L)
        val untimed = timed.copy(epochMillis = 0L)

        val timedFirst = ArrivalProgressController(nowEpochMillis = { 1_700_000_002_000L })
        timedFirst.arm(route)
        timedFirst.onLocation(timed)
        timedFirst.onLocation(untimed)
        assertEquals(1, timedFirst.state.consecutivePastTarget)
        assertNotEquals(ArrivalAlertPhase.MISSED, timedFirst.state.phase)
        timedFirst.onLocation(untimed)
        assertEquals(ArrivalAlertPhase.MISSED, timedFirst.state.phase)

        val untimedFirst = ArrivalProgressController(nowEpochMillis = { 1_700_000_002_000L })
        untimedFirst.arm(route)
        untimedFirst.onLocation(untimed)
        untimedFirst.onLocation(timed)
        assertEquals(1, untimedFirst.state.consecutivePastTarget)
        assertNotEquals(ArrivalAlertPhase.MISSED, untimedFirst.state.phase)
        untimedFirst.onLocation(later)
        assertEquals(ArrivalAlertPhase.MISSED, untimedFirst.state.phase)
    }

    @Test fun restoredArrivalCounterNeedsFreshFixesAfterProcessRecreation() {
        var saved: ArrivalProgressState? = null
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = saved
            override fun save(state: ArrivalProgressState) { saved = state }
        }
        val target = route.stops[route.targetIndex]
        fun gps(epoch: Long) = LocationFix(target, 10.0, 0.0, epochMillis = epoch)
        val initial = ArrivalProgressController(store, nowEpochMillis = { 12_000L })
        initial.arm(route)
        initial.onLocation(gps(10_000L))
        assertEquals(1, initial.state.consecutiveInsideThreshold)

        // The GPS timestamp watermark is in-memory; a persisted single-fix
        // streak must not survive controller recreation as independent evidence.
        val restored = ArrivalProgressController(store, nowEpochMillis = { 12_000L })
        restored.arm(route)
        assertEquals(0, restored.state.consecutiveInsideThreshold)
        restored.onLocation(gps(10_000L))
        assertEquals(1, restored.state.consecutiveInsideThreshold)
        assertEquals(0, restored.state.arrivalEventCount)
        restored.onLocation(gps(10_000L))
        assertEquals(1, restored.state.consecutiveInsideThreshold)
        restored.onLocation(gps(11_000L))
        assertEquals(ArrivalAlertPhase.ARRIVED, restored.state.phase)
        assertEquals(1, restored.state.arrivalEventCount)
    }

    @Test fun restoredMissedStopCounterNeedsFreshFixesAfterProcessRecreation() {
        var saved: ArrivalProgressState? = null
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = saved
            override fun save(state: ArrivalProgressState) { saved = state }
        }
        val past = route.stops.last()
        fun gps(epoch: Long) = LocationFix(past, 10.0, 0.0, epochMillis = epoch)
        val initial = ArrivalProgressController(store, nowEpochMillis = { 22_000L })
        initial.arm(route)
        initial.onLocation(gps(20_000L))
        assertEquals(1, initial.state.consecutivePastTarget)

        val restored = ArrivalProgressController(store, nowEpochMillis = { 22_000L })
        restored.arm(route)
        assertEquals(0, restored.state.consecutivePastTarget)
        restored.onLocation(gps(20_000L))
        assertEquals(1, restored.state.consecutivePastTarget)
        assertNotEquals(ArrivalAlertPhase.MISSED, restored.state.phase)
        restored.onLocation(gps(21_000L))
        assertEquals(ArrivalAlertPhase.MISSED, restored.state.phase)
    }

    @Test fun newArmedTripResetsGpsTimestampWatermark() {
        val controller = ArrivalProgressController(nowEpochMillis = { 10_000L })
        controller.arm(route)
        val target = route.stops[route.targetIndex]
        controller.onLocation(LocationFix(target, 10.0, 0.0, epochMillis = 9_000L))
        controller.disarm()
        controller.arm(route)
        controller.onLocation(LocationFix(target, 10.0, 0.0, epochMillis = 1_000L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
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

    @Test fun negativeGpsTimestampCannotConfirmArrivalOrPreservePendingFix() {
        val controller = ArrivalProgressController(nowEpochMillis = { 200L })
        controller.arm(route)
        val target = route.stops[route.targetIndex]
        fun gps(epoch: Long) = LocationFix(target, 10.0, 0.0, epochMillis = epoch)

        // Negative epochs are malformed provider data, not missing timestamps.
        repeat(2) { controller.onLocation(gps(-1L)) }
        assertEquals(0, controller.state.consecutiveInsideThreshold)
        assertEquals(0, controller.state.arrivalEventCount)

        controller.onLocation(gps(100L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        controller.onLocation(gps(-2L))
        assertEquals(0, controller.state.consecutiveInsideThreshold)
        assertEquals(0, controller.state.arrivalEventCount)

        controller.onLocation(gps(101L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        controller.onLocation(gps(102L))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
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


    @Test fun impossibleFiniteSpeedCannotAdvanceGpsArrivalOrMissedStopDebounce() {
        val c = ArrivalProgressController()
        c.arm(route)
        val target = route.stops[route.targetIndex]

        // A finite but impossible provider speed must not invent the first arrival fix.
        val before = c.state
        c.onLocation(fix(target, speed = 120.01))
        assertEquals(before, c.state)
        c.onLocation(fix(target, speed = Double.MAX_VALUE))
        assertEquals(before, c.state)

        // Once a real confirmation exists, an impossible sample breaks the streak.
        c.onLocation(fix(target, speed = 0.0))
        assertEquals(1, c.state.consecutiveInsideThreshold)
        c.onLocation(fix(target, speed = 121.0))
        assertEquals(0, c.state.consecutiveInsideThreshold)
        assertEquals(0, c.state.arrivalEventCount)

        // The accepted upper boundary does not suppress credible surface-transit GPS.
        c.onLocation(fix(target, speed = 120.0))
        assertEquals(1, c.state.consecutiveInsideThreshold)
        c.onLocation(fix(target, speed = 0.0))
        assertEquals(ArrivalAlertPhase.ARRIVED, c.state.phase)
        assertEquals(1, c.state.arrivalEventCount)

        val missed = ArrivalProgressController()
        missed.arm(route)
        val beyondTarget = route.stops.last()
        missed.onLocation(fix(beyondTarget, speed = 0.0))
        assertEquals(1, missed.state.consecutivePastTarget)
        missed.onLocation(fix(beyondTarget, speed = 121.0))
        assertEquals(0, missed.state.consecutivePastTarget)
        missed.onLocation(fix(beyondTarget, speed = 0.0))
        assertEquals(1, missed.state.consecutivePastTarget)
        assertEquals(0, missed.state.arrivalEventCount)
        assertEquals(1, missed.state.consecutivePastTarget)
        missed.onLocation(fix(beyondTarget, speed = 0.0))
        assertEquals(ArrivalAlertPhase.MISSED, missed.state.phase)
    }

    @Test fun failingStateWriteDoesNotStopArrivalAndLaterSuccessfulSave() {
        var failWrites = true
        var persisted: ArrivalProgressState? = null
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = null
            override fun save(state: ArrivalProgressState) {
                if (failWrites) throw IllegalStateException("Simulated full disk")
                persisted = state
            }
        }
        val controller = ArrivalProgressController(store, nowEpochMillis = { 3_000L })
        controller.arm(route)
        assertEquals(ArrivalAlertPhase.TRACKING, controller.state.phase)
        val target = route.stops[route.targetIndex]
        controller.onLocation(LocationFix(target, 10.0, 0.0, epochMillis = 1000L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)

        failWrites = false
        controller.onLocation(LocationFix(target, 10.0, 0.0, epochMillis = 2000L))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
        assertEquals(ArrivalAlertPhase.ARRIVED, persisted?.phase)
        assertEquals(1, persisted?.arrivalEventCount)
    }

    @Test fun failingStateWriteDoesNotStopMissedOrDisarm() {
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState? = null
            override fun save(state: ArrivalProgressState) {
                throw IllegalStateException("Simulated storage failure")
            }
        }
        val controller = ArrivalProgressController(store, nowEpochMillis = { 3_000L })
        controller.arm(route)
        val pastTarget = route.stops.last()
        controller.onLocation(LocationFix(pastTarget, 10.0, 0.0, epochMillis = 1000L))
        controller.onLocation(LocationFix(pastTarget, 10.0, 0.0, epochMillis = 2000L))
        assertEquals(ArrivalAlertPhase.MISSED, controller.state.phase)
        assertEquals(0, controller.state.arrivalEventCount)

        controller.disarm()
        assertFalse(controller.state.armed)
        assertEquals(ArrivalAlertPhase.IDLE, controller.state.phase)
    }

    @Test fun futureDatedGpsFixCannotPoisonWatermarkOrDebounce() {
        val now = 50_000L
        val controller = ArrivalProgressController(nowEpochMillis = { now })
        val target = route.stops[route.targetIndex]
        fun gps(timestamp: Long) = LocationFix(target, 10.0, 0.0, epochMillis = timestamp)
        controller.arm(route)

        controller.onLocation(gps(49_000L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        // A far-future provider timestamp must not become the last accepted fix.
        controller.onLocation(gps(now + ArrivalProgressController.MAX_FUTURE_FIX_SKEW_MILLIS + 1L))
        assertEquals(0, controller.state.consecutiveInsideThreshold)
        assertEquals(0, controller.state.arrivalEventCount)

        controller.onLocation(gps(49_001L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        controller.onLocation(gps(49_002L))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }

    @Test fun gpsTimestampAtFutureSkewBoundaryRemainsUsable() {
        val now = 50_000L
        val controller = ArrivalProgressController(nowEpochMillis = { now })
        val target = route.stops[route.targetIndex]
        controller.arm(route)

        controller.onLocation(LocationFix(target, 10.0, 0.0, epochMillis = 79_999L))
        controller.onLocation(LocationFix(target, 10.0, 0.0, epochMillis = 80_000L))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }


    @Test fun staleGpsFixCannotConfirmArrivalOrPoisonTimestampWatermark() {
        val now = 1_700_000_100_000L
        val controller = ArrivalProgressController(nowEpochMillis = { now })
        val target = route.stops[route.targetIndex]
        fun gps(epoch: Long) = LocationFix(target, 10.0, 0.0, epochMillis = epoch)
        val stale = now - ArrivalLocationQuality.MAX_FIX_AGE_MILLIS - 1L
        controller.arm(route)

        // Two accurate but outdated callbacks must not create an arrival.
        controller.onLocation(gps(stale))
        controller.onLocation(gps(stale - 1L))
        assertEquals(0, controller.state.arrivalEventCount)
        assertEquals(0, controller.state.consecutiveInsideThreshold)

        controller.onLocation(gps(now - 1_000L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        controller.onLocation(gps(stale))
        assertEquals(0, controller.state.consecutiveInsideThreshold)

        // Old evidence cannot poison the watermark: two fresh fixes still work.
        controller.onLocation(gps(now - 900L))
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        controller.onLocation(gps(now - 800L))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }

    @Test fun staleGpsFixBreaksMissedStopDebounce() {
        val now = 1_700_000_100_000L
        val controller = ArrivalProgressController(nowEpochMillis = { now })
        val past = route.stops.last()
        fun gps(epoch: Long) = LocationFix(past, 10.0, 0.0, epochMillis = epoch)
        controller.arm(route)
        controller.onLocation(gps(now - ArrivalLocationQuality.MAX_FIX_AGE_MILLIS - 1L))
        controller.onLocation(gps(now - ArrivalLocationQuality.MAX_FIX_AGE_MILLIS - 2L))
        assertEquals(0, controller.state.consecutivePastTarget)
        assertNotEquals(ArrivalAlertPhase.MISSED, controller.state.phase)

        controller.onLocation(gps(now - 1_000L))
        assertEquals(1, controller.state.consecutivePastTarget)
        controller.onLocation(gps(now - ArrivalLocationQuality.MAX_FIX_AGE_MILLIS - 1L))
        assertEquals(0, controller.state.consecutivePastTarget)
        controller.onLocation(gps(now - 900L))
        controller.onLocation(gps(now - 800L))
        assertEquals(ArrivalAlertPhase.MISSED, controller.state.phase)
    }

    @Test fun staleAgeBoundaryAndUnknownClockFailClosed() {
        val now = 100_000L
        val atBoundary = ArrivalProgressController(nowEpochMillis = { now })
        val target = route.stops[route.targetIndex]
        fun gps(epoch: Long) = LocationFix(target, 10.0, 0.0, epochMillis = epoch)
        atBoundary.arm(route)
        atBoundary.onLocation(gps(now - ArrivalLocationQuality.MAX_FIX_AGE_MILLIS))
        atBoundary.onLocation(gps(now - ArrivalLocationQuality.MAX_FIX_AGE_MILLIS + 1L))
        assertEquals(ArrivalAlertPhase.ARRIVED, atBoundary.state.phase)

        for (invalidNow in listOf(0L, -1L)) {
            val controller = ArrivalProgressController(nowEpochMillis = { invalidNow })
            controller.arm(route)
            controller.onLocation(gps(100L))
            controller.onLocation(gps(101L))
            assertEquals(0, controller.state.arrivalEventCount)
            // Legacy devices without a timestamp retain their previous behavior.
            controller.onLocation(gps(0L))
            controller.onLocation(gps(0L))
            assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        }
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
