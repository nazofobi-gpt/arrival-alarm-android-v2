package com.nazofobi.arrivalalarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorSyncClientTest {
    private class FakePort : ConnectorActionPort {
        var writes = 0
        var current = ArrivalAlarmConnectorSnapshot(
            stateVersion = 7,
            capturedAtEpochSeconds = 1_000,
            sourceUpdatedAtEpochSeconds = 995,
            source = "test-device",
            journeyPhase = JourneyPhase.DESTINATION_SELECTED,
            alarmArmed = false,
        )

        override fun readSnapshot(): ArrivalAlarmConnectorSnapshot = current

        private fun applied(message: String): ConnectorActionOutcome {
            writes += 1
            current = current.copy(
                stateVersion = current.stateVersion + 1,
                sourceUpdatedAtEpochSeconds = current.capturedAtEpochSeconds,
                journeyPhase = JourneyPhase.ARMED,
                alarmArmed = true,
            )
            return ConnectorActionOutcome(true, message, "action-$writes")
        }

        override fun setOrigin(point: MapPoint) = applied("origin")
        override fun setDestination(point: MapPoint) = applied("destination")
        override fun selectJourney(routeId: String) = applied("journey")
        override fun setBoardingStop(stop: ConnectorStopState) = applied("boarding")
        override fun armArrivalAlarm() = applied("armed")
        override fun cancelArrivalAlarm() = applied("cancelled")
    }

    private class FakeTransport(
        var pending: String,
    ) : ConnectorGatewayTransport {
        val snapshots = mutableListOf<String>()
        val receipts = mutableListOf<String>()

        override fun publishSnapshot(payload: String) {
            snapshots += payload
        }

        override fun fetchPendingCommands(): String = pending

        override fun publishReceipt(payload: String) {
            receipts += payload
        }
    }

    private fun pendingCommand(
        idempotencyKey: String = "command-sync-1",
        queuedAt: Long = 1_000,
    ) = """
        {
          "commands": [
            {
              "command_id": "gateway-command-1",
              "expected_state_version": 7,
              "user_confirmed": true,
              "queued_at_epoch_seconds": $queuedAt,
              "status": "queued",
              "command": {
                "type": "arm_arrival_alarm",
                "idempotency_key": "$idempotencyKey"
              }
            }
          ]
        }
    """.trimIndent()

    @Test fun syncPublishesStateExecutesCommandAndPublishesReceipt() {
        val port = FakePort()
        val transport = FakeTransport(pendingCommand())
        val client = ConnectorSyncClient(
            processor = ConnectorCommandProcessor(port),
            transport = transport,
            nowEpochSeconds = { 1_000 },
        )

        val result = client.syncOnce()

        assertEquals(1, result.processedCommands)
        assertEquals(1, result.appliedCommands)
        assertEquals(8, result.snapshotStateVersionAfter)
        assertEquals(1, port.writes)
        assertEquals(2, transport.snapshots.size)
        assertEquals(1, transport.receipts.size)
        assertEquals(
            8L,
            JSONObject(transport.snapshots.last()).getLong("state_version"),
        )
        assertEquals(
            "APPLIED",
            JSONObject(transport.receipts.single()).getString("status"),
        )
    }

    @Test fun duplicateGatewayDeliveryDoesNotRepeatDeviceSideEffect() {
        val port = FakePort()
        val transport = FakeTransport(pendingCommand())
        val client = ConnectorSyncClient(
            processor = ConnectorCommandProcessor(port),
            transport = transport,
            nowEpochSeconds = { 1_000 },
        )

        client.syncOnce()
        val duplicate = client.syncOnce()

        assertEquals(1, port.writes)
        assertEquals(0, duplicate.appliedCommands)
        assertTrue(JSONObject(transport.receipts.last()).getBoolean("duplicate"))
    }

    @Test fun expiredCommandFailsClosedAndStillReturnsReceipt() {
        val port = FakePort()
        val transport = FakeTransport(pendingCommand(queuedAt = 800))
        val client = ConnectorSyncClient(
            processor = ConnectorCommandProcessor(port),
            transport = transport,
            nowEpochSeconds = { 1_000 },
            maxCommandAgeSeconds = 120,
        )

        val result = client.syncOnce()

        assertEquals(1, result.processedCommands)
        assertEquals(0, result.appliedCommands)
        assertEquals(0, port.writes)
        assertEquals(
            "REJECTED_EXPIRED_COMMAND",
            JSONObject(transport.receipts.single()).getString("status"),
        )
    }

    @Test fun httpTransportRejectsCleartextGatewayConfiguration() {
        val error = runCatching {
            HttpConnectorGatewayTransport(
                baseUrl = "http://example.invalid",
                bearerTokenProvider = { "secret" },
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
