package com.nazofobi.arrivalalarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorDeviceSyncTest {
    private class FakeRemote(
        var commandsJson: String,
    ) : ConnectorRemoteTransport {
        val snapshots = mutableListOf<String>()
        val receipts = mutableListOf<String>()

        override fun pushSnapshot(snapshotJson: String) {
            snapshots += snapshotJson
        }

        override fun fetchCommands(): String = commandsJson

        override fun pushReceipt(receiptJson: String) {
            receipts += receiptJson
        }
    }

    private fun preparedPort(now: Long = 1_000L): JourneyConnectorActionPort {
        val controller = JourneyController()
        val port = JourneyConnectorActionPort(
            controller = controller,
            nowEpochSeconds = { now },
        )
        port.setOrigin(MapPoint(52.66, 8.23, "Lohne"))
        port.setDestination(MapPoint(53.08, 8.81, "Bremen Hbf"))
        return port
    }

    @Test fun syncPostsStateExecutesConfirmedWritePostsReceiptAndFreshState() {
        val port = preparedPort()
        val processor = ConnectorCommandProcessor(port)
        val expectedVersion = processor.snapshot().stateVersion
        val remote = FakeRemote(
            commandsJson = """
                {"commands":[{
                  "command_id":"remote-1",
                  "expected_state_version":$expectedVersion,
                  "user_confirmed":true,
                  "status":"queued",
                  "command":{
                    "type":"arm_arrival_alarm",
                    "idempotency_key":"command-arm-001"
                  }
                }]}
            """.trimIndent()
        )
        val engine = ConnectorDeviceSyncEngine(processor, remote) { 1_000L }

        val result = engine.syncOnce()

        assertEquals(1, result.commandsReceived)
        assertEquals(1, result.commandsApplied)
        assertEquals(1, result.receiptsPosted)
        assertEquals(2, remote.snapshots.size)
        assertEquals(1, remote.receipts.size)
        assertEquals("APPLIED", JSONObject(remote.receipts.single()).getString("status"))
        assertTrue(JSONObject(remote.snapshots.last()).getBoolean("alarm_armed"))
    }

    @Test fun unconfirmedRemoteWriteIsRejectedAtDeviceBoundary() {
        val port = preparedPort()
        val processor = ConnectorCommandProcessor(port)
        val expectedVersion = processor.snapshot().stateVersion
        val remote = FakeRemote(
            """{"commands":[{
              "command_id":"remote-2",
              "expected_state_version":$expectedVersion,
              "user_confirmed":false,
              "status":"queued",
              "command":{
                "type":"arm_arrival_alarm",
                "idempotency_key":"command-arm-002"
              }
            }]}"""
        )

        val result = ConnectorDeviceSyncEngine(processor, remote) { 1_000L }.syncOnce()

        assertEquals(0, result.commandsApplied)
        assertEquals(1, remote.snapshots.size)
        assertFalse(processor.snapshot().alarmArmed)
        assertEquals(
            "REJECTED_CONFIRMATION_REQUIRED",
            JSONObject(remote.receipts.single()).getString("status")
        )
    }

    @Test fun sharedReceiptStorePreventsDuplicateSideEffectAcrossProcessorRecreation() {
        val port = preparedPort()
        val receiptStore = InMemoryConnectorReceiptStore()
        val firstProcessor = ConnectorCommandProcessor(port, receiptStore)
        val expectedVersion = firstProcessor.snapshot().stateVersion
        val commandJson = """
            {"commands":[{
              "command_id":"remote-3",
              "expected_state_version":$expectedVersion,
              "user_confirmed":true,
              "status":"queued",
              "command":{
                "type":"arm_arrival_alarm",
                "idempotency_key":"command-arm-restart"
              }
            }]}
        """.trimIndent()
        val firstRemote = FakeRemote(commandJson)
        ConnectorDeviceSyncEngine(firstProcessor, firstRemote) { 1_000L }.syncOnce()
        val versionAfterFirst = firstProcessor.snapshot().stateVersion

        val recreatedProcessor = ConnectorCommandProcessor(port, receiptStore)
        val secondRemote = FakeRemote(commandJson)
        ConnectorDeviceSyncEngine(recreatedProcessor, secondRemote) { 1_000L }.syncOnce()

        assertEquals(versionAfterFirst, recreatedProcessor.snapshot().stateVersion)
        val duplicateReceipt = JSONObject(secondRemote.receipts.single())
        assertTrue(duplicateReceipt.getBoolean("duplicate"))
        assertEquals("APPLIED", duplicateReceipt.getString("status"))
    }

    @Test fun completedGatewayCommandsAreIgnored() {
        val processor = ConnectorCommandProcessor(preparedPort())
        val remote = FakeRemote(
            """{"commands":[{
              "command_id":"remote-complete",
              "expected_state_version":3,
              "user_confirmed":true,
              "status":"completed",
              "command":{
                "type":"arm_arrival_alarm",
                "idempotency_key":"command-complete"
              }
            }]}"""
        )

        val result = ConnectorDeviceSyncEngine(processor, remote) { 1_000L }.syncOnce()

        assertEquals(0, result.commandsReceived)
        assertEquals(0, remote.receipts.size)
        assertFalse(processor.snapshot().alarmArmed)
    }

    @Test fun productionTransportRejectsPlainHttpGateway() {
        val error = runCatching {
            HttpConnectorRemoteTransport(
                baseUrl = "http://example.com",
                credentialProvider = object : ConnectorCredentialProvider {
                    override fun bearerToken() = "secret"
                },
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
