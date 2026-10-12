package com.nazofobi.arrivalalarm

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun httpGatewayRejectsEmbeddedSecretsAndInvalidUrisWithoutEchoingThem() {
        val riskyUrls = listOf(
            "https://user:private-secret@gateway.invalid/base",
            "https://gateway.invalid/base?token=private-secret",
            "https://gateway.invalid/base#private-secret",
            "https://gateway.invalid/%private-secret",
        )
        for (baseUrl in riskyUrls) {
            val failure = runCatching {
                HttpConnectorGatewayTransport(baseUrl, bearerTokenProvider = { "device-secret" })
            }.exceptionOrNull()
            assertTrue("Expected invalid URL to be rejected: ${baseUrl.substringBefore('?')}", failure is IllegalArgumentException)
            assertFalse(failure.toString().contains("private-secret"))
            assertFalse(failure.toString().contains("device-secret"))
        }
    }

    @Test fun gatewayNeverFollowsHttpRedirectsWithDeviceBearerCredentials() {
        var disconnected = false
        lateinit var opened: HttpURLConnection
        val transport = HttpConnectorGatewayTransport(
            baseUrl = "https://gateway.invalid",
            bearerTokenProvider = { "private-device-token" },
            connectionFactory = { url ->
                object : HttpURLConnection(url) {
                    override fun connect() = Unit
                    override fun usingProxy(): Boolean = false
                    override fun disconnect() { disconnected = true }
                    override fun getResponseCode(): Int = 302
                }.also { opened = it }
            },
        )
        val failure = runCatching { transport.fetchPendingCommands() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("connector_http_302", failure?.message)
        assertFalse(opened.instanceFollowRedirects)
        assertTrue(disconnected)
    }

    @Test fun gatewayRejectsUnlimitedOrUnreasonableNetworkTimeouts() {
        for (timeout in listOf(-1, 0, 60_001)) {
            assertTrue(runCatching {
                HttpConnectorGatewayTransport(
                    baseUrl = "https://gateway.invalid",
                    bearerTokenProvider = { "test-device-token" },
                    connectTimeoutMillis = timeout,
                )
            }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching {
                HttpConnectorGatewayTransport(
                    baseUrl = "https://gateway.invalid",
                    bearerTokenProvider = { "test-device-token" },
                    readTimeoutMillis = timeout,
                )
            }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun gatewayNetworkAndTimeoutFailuresCannotLeakUrlsOrCoordinates() {
        val sensitive = "token=private-device-token&latitude=52.666123"
        val failures = listOf(
            IOException("https://gateway.invalid?$sensitive failed") to "connector_network_error",
            SocketTimeoutException("https://gateway.invalid?$sensitive timed out") to "connector_timeout",
        )
        for ((cause, expectedMessage) in failures) {
            val transport = HttpConnectorGatewayTransport(
                baseUrl = "https://gateway.invalid",
                bearerTokenProvider = { "private-device-token" },
                connectionFactory = { throw cause },
            )
            val failure = runCatching { transport.fetchPendingCommands() }.exceptionOrNull()
            assertEquals(expectedMessage, failure?.message)
            assertTrue(failure is IOException)
            assertEquals(null, failure?.cause)
            assertFalse(failure.toString().contains("private-device-token"))
            assertFalse(failure.toString().contains("52.666123"))
        }
    }
}
