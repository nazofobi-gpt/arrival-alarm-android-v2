package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectorRuntimeTest {
    private class StaticPort : ConnectorActionPort {
        private val snapshot = ArrivalAlarmConnectorSnapshot(
            stateVersion = 4,
            capturedAtEpochSeconds = 1_000,
            sourceUpdatedAtEpochSeconds = 1_000,
            source = "runtime-test",
            journeyPhase = JourneyPhase.DESTINATION_SELECTED,
        )

        override fun readSnapshot() = snapshot
        override fun setOrigin(point: MapPoint) = ConnectorActionOutcome(false, "unused")
        override fun setDestination(point: MapPoint) = ConnectorActionOutcome(false, "unused")
        override fun selectJourney(routeId: String) = ConnectorActionOutcome(false, "unused")
        override fun setBoardingStop(stop: ConnectorStopState) = ConnectorActionOutcome(false, "unused")
        override fun armArrivalAlarm() = ConnectorActionOutcome(false, "unused")
        override fun cancelArrivalAlarm() = ConnectorActionOutcome(false, "unused")
    }

    private class FakeTransport : ConnectorGatewayTransport {
        var snapshotCount = 0
        override fun publishSnapshot(payload: String) { snapshotCount += 1 }
        override fun fetchPendingCommands(): String = "{\"commands\":[]}"
        override fun publishReceipt(payload: String) = Unit
    }

    @Test fun missingSessionFailsClosedWithoutTransport() {
        var transportCreated = false
        val runtime = ConnectorRuntime(
            processor = ConnectorCommandProcessor(StaticPort()),
            sessionProvider = object : ConnectorSessionProvider {
                override fun currentSession(): ConnectorDeviceSession? = null
            },
            transportFactory = {
                transportCreated = true
                FakeTransport()
            },
            nowEpochSeconds = { 1_000 },
        )

        val status = runtime.syncNow()

        assertEquals(ConnectorRuntimeState.DISCONNECTED, status.state)
        assertEquals(false, transportCreated)
        assertNull(status.lastResult)
    }

    @Test fun expiredSessionFailsClosedBeforeNetwork() {
        var transportCreated = false
        val runtime = ConnectorRuntime(
            processor = ConnectorCommandProcessor(StaticPort()),
            sessionProvider = object : ConnectorSessionProvider {
                override fun currentSession() = ConnectorDeviceSession(
                    baseUrl = "https://connector.example",
                    accessToken = "expired",
                    expiresAtEpochSeconds = 1_020,
                )
            },
            transportFactory = {
                transportCreated = true
                FakeTransport()
            },
            nowEpochSeconds = { 1_000 },
        )

        val status = runtime.syncNow()

        assertEquals(ConnectorRuntimeState.AUTH_EXPIRED, status.state)
        assertEquals(false, transportCreated)
    }

    @Test fun validSessionRunsDeterministicSync() {
        val transport = FakeTransport()
        val runtime = ConnectorRuntime(
            processor = ConnectorCommandProcessor(StaticPort()),
            sessionProvider = object : ConnectorSessionProvider {
                override fun currentSession() = ConnectorDeviceSession(
                    baseUrl = "https://connector.example",
                    accessToken = "device-token",
                    expiresAtEpochSeconds = 2_000,
                )
            },
            transportFactory = { transport },
            nowEpochSeconds = { 1_000 },
        )

        val status = runtime.syncNow()

        assertEquals(ConnectorRuntimeState.CONNECTED, status.state)
        assertEquals(1, transport.snapshotCount)
        assertEquals(0, status.lastResult?.processedCommands)
        assertEquals(4L, status.lastResult?.snapshotStateVersionAfter)
    }
}
