package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalProgressCounterRecoveryTest {
    private val route = ArmedRoute(
        routeId = "counter-recovery-route",
        stops = listOf(
            GeoPoint(52.0000, 8.0000),
            GeoPoint(52.0100, 8.0000),
            GeoPoint(52.0200, 8.0000),
        ),
        targetIndex = 1,
    )

    private fun restore(saved: ArrivalProgressState): ArrivalProgressController {
        var persisted = saved.copy(routeFingerprint = route.persistenceFingerprint)
        val store = object : ArrivalProgressStateStore {
            override fun load(): ArrivalProgressState = persisted
            override fun save(state: ArrivalProgressState) { persisted = state }
        }
        return ArrivalProgressController(store).apply { arm(route) }
    }

    private fun fix(index: Int) = LocationFix(
        point = route.stops[index],
        accuracyMeters = 5.0,
        speedMps = 0.0,
    )

    @Test fun corruptInsideCounterDoesNotTriggerArrivalFromOneFix() {
        val controller = restore(
            ArrivalProgressState(
                routeId = route.routeId,
                armed = true,
                phase = ArrivalAlertPhase.TRACKING,
                consecutiveInsideThreshold = Int.MAX_VALUE,
                arrivalEventCount = Int.MAX_VALUE,
            )
        )

        controller.onLocation(fix(1))
        assertEquals(ArrivalAlertPhase.IMMINENT, controller.state.phase)
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        assertEquals(Int.MAX_VALUE, controller.state.arrivalEventCount)

        controller.onLocation(fix(1))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(2, controller.state.consecutiveInsideThreshold)
        assertEquals(Int.MAX_VALUE, controller.state.arrivalEventCount)
    }

    @Test fun corruptPastCounterDoesNotDeclareMissFromOneFix() {
        val controller = restore(
            ArrivalProgressState(
                routeId = route.routeId,
                armed = true,
                phase = ArrivalAlertPhase.TRACKING,
                lastMatchedStopIndex = 1,
                consecutivePastTarget = Int.MAX_VALUE,
            )
        )

        controller.onLocation(fix(2))
        assertEquals(1, controller.state.consecutivePastTarget)
        assertEquals(ArrivalAlertPhase.TRACKING, controller.state.phase)

        controller.onLocation(fix(2))
        assertEquals(2, controller.state.consecutivePastTarget)
        assertEquals(ArrivalAlertPhase.MISSED, controller.state.phase)
    }

    @Test fun persistedSingleFixRequiresTwoFreshConfirmationsAfterRestart() {
        val controller = restore(
            ArrivalProgressState(
                routeId = route.routeId,
                armed = true,
                phase = ArrivalAlertPhase.IMMINENT,
                consecutiveInsideThreshold = 1,
            )
        )

        // The original single GPS fix cannot establish independent evidence
        // after the in-memory timestamp watermark was lost on restart.
        assertEquals(0, controller.state.consecutiveInsideThreshold)
        controller.onLocation(fix(1))
        assertEquals(ArrivalAlertPhase.IMMINENT, controller.state.phase)
        assertEquals(1, controller.state.consecutiveInsideThreshold)
        assertEquals(0, controller.state.arrivalEventCount)

        controller.onLocation(fix(1))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }
}
