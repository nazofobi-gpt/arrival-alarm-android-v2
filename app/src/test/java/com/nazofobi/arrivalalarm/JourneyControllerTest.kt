package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    @Test fun invalidCoordinatesCannotReplaceValidSelections() {
        val c = JourneyController()
        c.selectStart(GeoPoint(53.5, 8.1))
        c.selectDestination(GeoPoint(53.6, 8.2))
        c.selectDestination(GeoPoint(Double.NaN, 9.0))
        assertEquals(GeoPoint(53.6, 8.2), c.state.destination)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
        assertNotNull(c.state.error)

        c.selectStart(GeoPoint(91.0, 8.0))
        assertEquals(GeoPoint(53.5, 8.1), c.state.start)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
        c.arm()
        assertEquals(JourneyPhase.ARMED, c.state.phase)
    }

    @Test fun invalidFreshSelectionsDoNotArmAndRespectGlobalBounds() {
        val c = JourneyController()
        c.selectStart(GeoPoint(Double.POSITIVE_INFINITY, 0.0))
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
        c.selectStart(GeoPoint(-90.0, 180.0))
        c.selectDestination(GeoPoint(20.0, -180.1))
        assertEquals(JourneyPhase.START_SELECTED, c.state.phase)
        c.arm()
        assertEquals(JourneyPhase.START_SELECTED, c.state.phase)
        c.selectDestination(GeoPoint(90.0, -180.0))
        c.arm()
        assertEquals(JourneyPhase.ARMED, c.state.phase)
    }

    @Test fun changingDestinationClearsOldDistanceAndPersistsReset() {
        var persisted: JourneyUiState? = null
        val store = object : JourneyStateStore {
            override fun load(): JourneyUiState? = persisted
            override fun save(state: JourneyUiState) { persisted = state }
        }
        val c = JourneyController(arrivalRadiusMeters = 250.0, stateStore = store)
        c.selectStart(GeoPoint(53.5, 8.1))
        c.selectDestination(GeoPoint(53.6, 8.2))
        c.arm()
        c.onDistanceChanged(600.0)
        assertEquals(600.0, c.state.distanceMeters)

        c.selectDestination(GeoPoint(53.7, 8.3))
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
        assertEquals(GeoPoint(53.7, 8.3), c.state.destination)
        assertNull(c.state.distanceMeters)
        assertNull(persisted?.distanceMeters)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, persisted?.phase)

        c.arm()
        assertNull(c.state.distanceMeters)
        c.onDistanceChanged(120.0)
        assertEquals(JourneyPhase.ARRIVED, c.state.phase)
        assertEquals(120.0, c.state.distanceMeters)
    }

    @Test fun changingDestinationAfterArrivalRemovesPreviousArrivalDistance() {
        val c = JourneyController(arrivalRadiusMeters = 250.0)
        c.selectStart(GeoPoint(53.5, 8.1))
        c.selectDestination(GeoPoint(53.6, 8.2))
        c.arm()
        c.onDistanceChanged(100.0)
        assertEquals(JourneyPhase.ARRIVED, c.state.phase)

        c.selectDestination(GeoPoint(53.8, 8.4))
        assertEquals(JourneyPhase.DESTINATION_SELECTED, c.state.phase)
        assertNull(c.state.distanceMeters)
    }

    @Test fun restoredArrivalRequiresMeasuredProximityWithinConfiguredRadius() {
        val start = GeoPoint(53.083, 8.813)
        val destination = GeoPoint(53.1, 8.9)
        var distance: Double? = null
        val store = object : JourneyStateStore {
            override fun load(): JourneyUiState = JourneyUiState(
                phase = JourneyPhase.ARRIVED,
                start = start,
                destination = destination,
                distanceMeters = distance,
            )
            override fun save(state: JourneyUiState) = Unit
        }

        for (invalid in listOf<Double?>(null, 250.01, 1_000.0)) {
            distance = invalid
            assertEquals(
                JourneyPhase.EMPTY,
                JourneyController(arrivalRadiusMeters = 250.0, stateStore = store).state.phase,
            )
        }

        distance = 250.0
        val restored = JourneyController(arrivalRadiusMeters = 250.0, stateStore = store).state
        assertEquals(JourneyPhase.ARRIVED, restored.phase)
        assertEquals(250.0, restored.distanceMeters ?: -1.0, 0.0)
    }

    @Test fun corruptedPersistedArmedJourneyFailsClosedOnRestart() {
        val store = object : JourneyStateStore {
            override fun load() = JourneyUiState(
                phase = JourneyPhase.ARMED,
                start = GeoPoint(53.0, 8.0),
                destination = GeoPoint(Double.NaN, 8.1),
            )
            override fun save(state: JourneyUiState) = Unit
        }
        val c = JourneyController(stateStore = store)
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
        c.arm()
        assertEquals(JourneyPhase.EMPTY, c.state.phase)
    }

    @Test fun invalidPersistentStoreReadFailsClosedOnRestart() {
        // SharedPreferences can throw ClassCastException for wrong-typed/legacy values.
        val store = object : JourneyStateStore {
            override fun load(): JourneyUiState? =
                throw ClassCastException("Corrupt stored journey phase")

            override fun save(state: JourneyUiState) = Unit
        }

        val controller = JourneyController(stateStore = store)
        assertEquals(JourneyPhase.EMPTY, controller.state.phase)
        controller.arm()
        assertEquals(JourneyPhase.EMPTY, controller.state.phase)
        controller.selectStart(GeoPoint(53.083, 8.813))
        assertEquals(JourneyPhase.START_SELECTED, controller.state.phase)
    }

    @Test fun invalidPersistedDistanceFailsClosedWithoutRejectingHealthyJourney() {
        val start = GeoPoint(53.083, 8.813)
        val destination = GeoPoint(53.1, 8.9)
        var persisted = JourneyUiState(
            phase = JourneyPhase.ARMED, start = start, destination = destination,
            distanceMeters = Double.NaN,
        )
        val store = object : JourneyStateStore {
            override fun load(): JourneyUiState = persisted
            override fun save(state: JourneyUiState) = Unit
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { distance ->
            persisted = persisted.copy(distanceMeters = distance)
            assertEquals(JourneyPhase.EMPTY, JourneyController(stateStore = store).state.phase)
        }
        persisted = persisted.copy(distanceMeters = 500.0)
        val restored = JourneyController(stateStore = store).state
        assertEquals(JourneyPhase.ARMED, restored.phase)
        assertEquals(500.0, restored.distanceMeters ?: -1.0, 0.0)
        assertEquals(destination, restored.destination)
    }

    @Test fun journeyStorageFailureDoesNotInterruptArrivalAndLaterSaves() {
        var failWrites = true
        var persisted: JourneyUiState? = null
        val store = object : JourneyStateStore {
            override fun load(): JourneyUiState? = persisted
            override fun save(state: JourneyUiState) {
                if (failWrites) throw IllegalStateException("preferences unavailable")
                persisted = state
            }
        }
        val controller = JourneyController(stateStore = store)
        controller.selectStart(GeoPoint(53.083, 8.813))
        controller.selectDestination(GeoPoint(53.1, 8.9))
        controller.arm()
        controller.onDistanceChanged(100.0)
        assertEquals(JourneyPhase.ARRIVED, controller.state.phase)
        assertNull(persisted)

        failWrites = false
        controller.selectDestination(GeoPoint(53.2, 8.9))
        assertEquals(JourneyPhase.DESTINATION_SELECTED, persisted?.phase)
        assertNull(persisted?.distanceMeters)
        controller.arm()
        assertEquals(JourneyPhase.ARMED, persisted?.phase)
    }

    @Test fun journeyStorageFailureDoesNotPreventErrorOrAlarmCancellation() {
        val store = object : JourneyStateStore {
            override fun load(): JourneyUiState? = null
            override fun save(state: JourneyUiState) {
                throw IllegalStateException("storage full")
            }
        }
        val controller = JourneyController(stateStore = store)
        controller.selectDestination(GeoPoint(53.2, 8.8))
        assertEquals(JourneyPhase.EMPTY, controller.state.phase)
        assertNotNull(controller.state.error)

        controller.selectStart(GeoPoint(53.0, 8.0))
        controller.selectDestination(GeoPoint(53.1, 8.1))
        controller.arm()
        controller.onDistanceChanged(350.0)
        controller.cancelAlarm()
        assertEquals(JourneyPhase.DESTINATION_SELECTED, controller.state.phase)
        assertNull(controller.state.distanceMeters)
        assertNull(controller.state.error)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRadiusCannotCreateAlwaysArrivingController() {
        JourneyController(arrivalRadiusMeters = Double.POSITIVE_INFINITY)
    }

}
