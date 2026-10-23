package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArrivalProgressRouteIdentityTest {
    private val route = ArmedRoute(
        routeId = "same-trip-id",
        stops = listOf(
            GeoPoint(52.0000, 8.0000),
            GeoPoint(52.0100, 8.0000),
            GeoPoint(52.0200, 8.0000),
        ),
        targetIndex = 1,
    )

    private fun fix(point: GeoPoint) = LocationFix(
        point = point,
        accuracyMeters = 5.0,
        speedMps = 0.0,
    )

    @Test fun changingTargetWithinSameTripCannotReusePreviousArrival() {
        val controller = ArrivalProgressController()
        controller.arm(route)
        repeat(2) { controller.onLocation(fix(route.stops[1])) }
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)

        val changed = route.copy(targetIndex = 2)
        assertNotEquals(route.persistenceFingerprint, changed.persistenceFingerprint)
        controller.arm(changed)
        assertEquals(ArrivalAlertPhase.TRACKING, controller.state.phase)
        assertEquals(0, controller.state.arrivalEventCount)

        controller.onLocation(fix(changed.stops[2]))
        assertEquals(ArrivalAlertPhase.IMMINENT, controller.state.phase)
        controller.onLocation(fix(changed.stops[2]))
        assertEquals(ArrivalAlertPhase.ARRIVED, controller.state.phase)
        assertEquals(1, controller.state.arrivalEventCount)
    }

    @Test fun processRecreationWithDifferentTargetDiscardsOldArrival() {
        var persisted: ArrivalProgressState? = null
        val store = object : ArrivalProgressStateStore {
            override fun load() = persisted
            override fun save(state: ArrivalProgressState) { persisted = state }
        }
        val before = ArrivalProgressController(store)
        before.arm(route)
        repeat(2) { before.onLocation(fix(route.stops[1])) }
        assertEquals(ArrivalAlertPhase.ARRIVED, before.state.phase)

        val restored = ArrivalProgressController(store)
        restored.arm(route.copy(targetIndex = 2))
        assertEquals(ArrivalAlertPhase.TRACKING, restored.state.phase)
        assertEquals(0, restored.state.arrivalEventCount)
        assertEquals(0, restored.state.consecutiveInsideThreshold)
    }

    @Test fun identicalArmedRouteStillRestoresOneShotArrival() {
        var persisted: ArrivalProgressState? = null
        val store = object : ArrivalProgressStateStore {
            override fun load() = persisted
            override fun save(state: ArrivalProgressState) { persisted = state }
        }
        ArrivalProgressController(store).apply {
            arm(route)
            repeat(2) { onLocation(fix(route.stops[1])) }
        }
        val restored = ArrivalProgressController(store)
        restored.arm(route.copy())
        assertEquals(ArrivalAlertPhase.ARRIVED, restored.state.phase)
        assertEquals(1, restored.state.arrivalEventCount)
        restored.onLocation(fix(route.stops[1]))
        assertEquals(1, restored.state.arrivalEventCount)
    }

    @Test fun legacyStateWithoutFingerprintAndChangedGeometryFailClosed() {
        var persisted: ArrivalProgressState? = ArrivalProgressState(
            routeId = route.routeId,
            armed = true,
            phase = ArrivalAlertPhase.ARRIVED,
            arrivalEventCount = 1,
        )
        val store = object : ArrivalProgressStateStore {
            override fun load() = persisted
            override fun save(state: ArrivalProgressState) { persisted = state }
        }
        val restored = ArrivalProgressController(store)
        restored.arm(route)
        assertEquals(ArrivalAlertPhase.TRACKING, restored.state.phase)

        repeat(2) { restored.onLocation(fix(route.stops[1])) }
        assertEquals(ArrivalAlertPhase.ARRIVED, restored.state.phase)

        val updatedStops = route.stops.toMutableList().apply { set(0, GeoPoint(52.0001, 8.0000)) }
        val changedGeometry = route.copy(stops = updatedStops)
        assertNotEquals(route.persistenceFingerprint, changedGeometry.persistenceFingerprint)
        restored.arm(changedGeometry)
        assertEquals(ArrivalAlertPhase.TRACKING, restored.state.phase)
    }
}
