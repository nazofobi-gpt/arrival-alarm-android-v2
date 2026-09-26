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


    @Test fun selectedRealRoutePublishesTimedPreviousNextAndEta() {
        var now = 1_790_403_600L
        val controller = JourneyController()
        val route = RouteOption(
            id = "journey-real-1",
            origin = MapPoint(52.665, 8.237, "Lohne"),
            destination = MapPoint(53.083, 8.813, "Bremen Hbf"),
            line = "RE 9",
            direction = "Bremen Hbf",
            departure = "08:00",
            arrival = "08:45",
            walkingMinutes = 0,
            transfers = 0,
            tripIds = listOf("trip-re9"),
            stops = listOf(
                RouteStop(
                    id = "a",
                    name = "Lohne",
                    departure = "2026-09-26T08:00:00+02:00",
                    plannedDeparture = "2026-09-26T07:58:00+02:00",
                ),
                RouteStop(
                    id = "b",
                    name = "Diepholz",
                    arrival = "2026-09-26T08:15:00+02:00",
                    departure = "2026-09-26T08:16:00+02:00",
                    plannedArrival = "2026-09-26T08:13:00+02:00",
                    plannedDeparture = "2026-09-26T08:14:00+02:00",
                ),
                RouteStop(
                    id = "c",
                    name = "Bremen Hbf",
                    arrival = "2026-09-26T08:45:00+02:00",
                    plannedArrival = "2026-09-26T08:43:00+02:00",
                ),
            ),
            sourceUpdatedAtEpochSeconds = now - 10,
        )
        val port = JourneyConnectorActionPort(
            controller = controller,
            routeResolver = { id -> route.takeIf { it.id == id } },
            nowEpochSeconds = { now },
        )
        port.setOrigin(route.origin)
        port.setDestination(route.destination)
        port.selectJourney(route.id)

        val betweenStops = port.readSnapshot()
        assertEquals("trip-re9", betweenStops.activeTrip?.tripId)
        assertEquals("Diepholz", betweenStops.activeTrip?.previousStop?.name)
        assertEquals(null, betweenStops.activeTrip?.currentStop)
        assertEquals("Bremen Hbf", betweenStops.activeTrip?.nextStop?.name)
        assertEquals("REALTIME", betweenStops.activeTrip?.timingBasis)
        assertEquals("transport.rest-stopovers", betweenStops.activeTrip?.progressSource)
        assertEquals(route.sourceUpdatedAtEpochSeconds, betweenStops.activeTrip?.progressUpdatedAtEpochSeconds)
        assertEquals(
            1_790_404_980L,
            betweenStops.activeTrip?.scheduledArrivalEpochSeconds,
        )
        assertEquals(
            1_790_405_100L,
            betweenStops.activeTrip?.estimatedArrivalEpochSeconds,
        )

        val versionBeforeDwell = betweenStops.stateVersion
        now = 1_790_405_100L
        val atDestination = port.readSnapshot()
        assertEquals("Bremen Hbf", atDestination.activeTrip?.currentStop?.name)
        assertTrue(atDestination.stateVersion > versionBeforeDwell)
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

    @Test fun liveLocationDistanceUpdatesSnapshotAndArrivalPhase() {
        var now = 50_000L
        val controller = JourneyController(arrivalRadiusMeters = 250.0)
        val port = JourneyConnectorActionPort(
            controller = controller,
            nowEpochSeconds = { now },
        )
        port.setOrigin(MapPoint(52.66, 8.23, "Lohne"))
        port.setDestination(MapPoint(52.67, 8.24, "Hedef"))
        port.armArrivalAlarm()
        val before = port.readSnapshot()

        now += 5
        val tracking = port.updateDistanceToDestination(900.0)
        val during = port.readSnapshot()

        assertTrue(tracking.applied)
        assertEquals(JourneyPhase.ARMED, during.journeyPhase)
        assertEquals(900.0, during.distanceToDestinationMeters ?: -1.0, 0.001)
        assertTrue(during.stateVersion > before.stateVersion)

        now += 5
        val arrived = port.updateDistanceToDestination(120.0)
        val final = port.readSnapshot()

        assertTrue(arrived.applied)
        assertEquals(JourneyPhase.ARRIVED, final.journeyPhase)
        assertEquals(120.0, final.distanceToDestinationMeters ?: -1.0, 0.001)
        assertTrue(final.stateVersion > during.stateVersion)
    }


    @Test fun failedForegroundLifecycleRollsBackRemoteArm() {
        val controller = JourneyController()
        val port = JourneyConnectorActionPort(
            controller = controller,
            nowEpochSeconds = { 60_000L },
            onAlarmArmed = { false },
        )
        port.setOrigin(MapPoint(52.66, 8.23, "Lohne"))
        port.setDestination(MapPoint(53.08, 8.81, "Bremen Hbf"))
        val before = port.readSnapshot()

        val outcome = port.armArrivalAlarm()
        val after = port.readSnapshot()

        assertFalse(outcome.applied)
        assertEquals(JourneyPhase.DESTINATION_SELECTED, after.journeyPhase)
        assertFalse(after.alarmArmed)
        assertEquals(before.stateVersion, after.stateVersion)
    }

    @Test fun cancellingAlarmReleasesForegroundLifecycle() {
        var cancelled = 0
        val controller = JourneyController()
        val port = JourneyConnectorActionPort(
            controller = controller,
            nowEpochSeconds = { 70_000L },
            onAlarmArmed = { true },
            onAlarmCancelled = { cancelled += 1 },
        )
        port.setOrigin(MapPoint(52.66, 8.23, "Lohne"))
        port.setDestination(MapPoint(53.08, 8.81, "Bremen Hbf"))
        assertTrue(port.armArrivalAlarm().applied)

        assertTrue(port.cancelArrivalAlarm().applied)
        assertEquals(1, cancelled)
    }

}
