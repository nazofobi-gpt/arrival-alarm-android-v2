package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyConnectorActionPortTest {
    @Test fun connectorWritesUseJourneyControllerAndExposeSelectedTrip() {
        var now = 10_000L
        val controller = JourneyController()
        val route = RouteOption(
            id = "route-ice-1",
            origin = MapPoint(52.66, 8.23, "Lohne"),
            destination = MapPoint(53.08, 8.81, "Bremen Hbf"),
            line = "RE 9",
            direction = "Bremen Hbf",
            departure = "08:00",
            arrival = "08:45",
            walkingMinutes = 3,
            transfers = 0,
        )
        val port = JourneyConnectorActionPort(
            controller = controller,
            routeResolver = { id -> route.takeIf { it.id == id } },
            nowEpochSeconds = { now },
        )

        assertTrue(port.setOrigin(route.origin).applied)
        assertTrue(port.setDestination(route.destination).applied)
        assertTrue(port.selectJourney(route.id).applied)
        assertTrue(
            port.setBoardingStop(
                ConnectorStopState(id = "stop-lohne", name = "Lohne Bahnhof")
            ).applied
        )
        assertTrue(port.armArrivalAlarm().applied)

        val state = port.readSnapshot()
        assertEquals(JourneyPhase.ARMED, state.journeyPhase)
        assertTrue(state.alarmArmed)
        assertEquals("RE 9", state.activeTrip?.line)
        assertEquals("Bremen Hbf", state.activeTrip?.direction)
        assertEquals("Lohne Bahnhof", state.activeTrip?.boardingStop?.name)
        assertEquals("Bremen Hbf", state.activeTrip?.destination?.name)

        now += 5
        assertTrue(port.cancelArrivalAlarm().applied)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, port.readSnapshot().journeyPhase)
        assertFalse(port.readSnapshot().alarmArmed)
    }

    @Test fun tripProgressIsReadableForConversationalQuestions() {
        val controller = JourneyController()
        val route = RouteOption(
            id = "route-1",
            origin = MapPoint(52.0, 8.0, "A"),
            destination = MapPoint(53.0, 9.0, "D"),
            line = "IC 1",
            direction = "D",
            departure = "10:00",
            arrival = "11:00",
            walkingMinutes = 0,
            transfers = 0,
        )
        val port = JourneyConnectorActionPort(
            controller = controller,
            routeResolver = { route },
            nowEpochSeconds = { 20_000L },
        )
        port.setOrigin(route.origin)
        port.setDestination(route.destination)
        port.selectJourney(route.id)

        val progress = port.updateTripProgress(
            previousStop = ConnectorStopState(id = "b", name = "B"),
            currentStop = ConnectorStopState(id = "c", name = "C"),
            nextStop = ConnectorStopState(id = "d", name = "D"),
        )

        assertTrue(progress.applied)
        val trip = port.readSnapshot().activeTrip
        assertEquals("B", trip?.previousStop?.name)
        assertEquals("C", trip?.currentStop?.name)
        assertEquals("D", trip?.nextStop?.name)
    }


    @Test fun authoritativeReadRefreshesFreshnessWithoutInventingStateChange() {
        var now = 40_000L
        val port = JourneyConnectorActionPort(
            controller = JourneyController(),
            nowEpochSeconds = { now },
        )

        val first = port.readSnapshot()
        now += 600
        val second = port.readSnapshot()

        assertEquals(first.stateVersion, second.stateVersion)
        assertEquals(40_600L, second.sourceUpdatedAtEpochSeconds)
        assertFalse(second.isStale(40_600L))
    }

    @Test fun invalidDomainOrderIsRejectedWithoutInventingState() {
        val port = JourneyConnectorActionPort(
            controller = JourneyController(),
            routeResolver = { null },
            nowEpochSeconds = { 30_000L },
        )

        val destination = port.setDestination(MapPoint(1.0, 2.0, "Hedef"))
        val journey = port.selectJourney("missing")
        val alarm = port.armArrivalAlarm()

        assertFalse(destination.applied)
        assertFalse(journey.applied)
        assertFalse(alarm.applied)
        assertEquals(JourneyPhase.EMPTY, port.readSnapshot().journeyPhase)
    }
}
