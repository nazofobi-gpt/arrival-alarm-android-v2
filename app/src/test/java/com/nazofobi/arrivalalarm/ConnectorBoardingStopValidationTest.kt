package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorBoardingStopValidationTest {
    private val route = RouteOption(
        id = "typed-boarding-test",
        origin = MapPoint(52.66, 8.23, "Lohne"),
        destination = MapPoint(53.08, 8.81, "Bremen Hbf"),
        line = "RE 9",
        direction = "Bremen Hbf",
        departure = "08:00",
        arrival = "08:45",
        walkingMinutes = 0,
        transfers = 0,
    )

    private fun selectedPort(): JourneyConnectorActionPort =
        JourneyConnectorActionPort(
            controller = JourneyController(),
            routeResolver = { id -> route.takeIf { it.id == id } },
            nowEpochSeconds = { 50_000L },
        ).also { port ->
            assertTrue(port.setOrigin(route.origin).applied)
            assertTrue(port.setDestination(route.destination).applied)
            assertTrue(port.selectJourney(route.id).applied)
        }

    @Test fun malformedTypedStopsDoNotMutateSelectedTripOrStateVersion() {
        val port = selectedPort()
        val before = port.readSnapshot()
        val invalid = listOf(
            ConnectorStopState(name = " "),
            ConnectorStopState(name = "Partial latitude", latitude = 52.66),
            ConnectorStopState(name = "Partial longitude", longitude = 8.23),
            ConnectorStopState(name = "North out of bounds", latitude = 90.01, longitude = 8.23),
            ConnectorStopState(name = "West out of bounds", latitude = 52.66, longitude = -180.01),
            ConnectorStopState(name = "NaN", latitude = Double.NaN, longitude = 8.23),
            ConnectorStopState(name = "Infinite", latitude = 52.66, longitude = Double.POSITIVE_INFINITY),
        )
        for (stop in invalid) {
            val result = port.setBoardingStop(stop)
            assertFalse("Should reject $stop", result.applied)
            val after = port.readSnapshot()
            assertEquals(before.stateVersion, after.stateVersion)
            assertEquals(before.activeTrip, after.activeTrip)
            assertEquals(before.journeyPhase, after.journeyPhase)
        }
    }

    @Test fun coordinateFreeAndBoundaryStopsRemainValid() {
        val port = selectedPort()
        val initial = port.readSnapshot()

        assertTrue(
            port.setBoardingStop(
                ConnectorStopState(id = "lo-platform", name = "Lohne Bahnhof")
            ).applied
        )
        val first = port.readSnapshot()
        assertEquals("Lohne Bahnhof", first.activeTrip?.boardingStop?.name)
        assertNull(first.activeTrip?.boardingStop?.latitude)
        assertTrue(first.stateVersion > initial.stateVersion)

        assertTrue(
            port.setBoardingStop(
                ConnectorStopState(name = "Geographic boundary", latitude = -90.0, longitude = 180.0)
            ).applied
        )
        val second = port.readSnapshot()
        assertEquals(-90.0, second.activeTrip?.boardingStop?.latitude)
        assertEquals(180.0, second.activeTrip?.boardingStop?.longitude)
        assertTrue(second.stateVersion > first.stateVersion)
    }
}
