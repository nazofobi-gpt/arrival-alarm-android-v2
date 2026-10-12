package com.nazofobi.arrivalalarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boundary regressions for the device-side command timestamp trust window. */
class ConnectorSyncTimestampWindowTest {
    private class ActionPort : ConnectorActionPort {
        var calls = 0
        private var version = 7L
        override fun readSnapshot() = ArrivalAlarmConnectorSnapshot(
            stateVersion = version,
            capturedAtEpochSeconds = 1_000L,
            sourceUpdatedAtEpochSeconds = 1_000L,
            source = "test",
            journeyPhase = JourneyPhase.DESTINATION_SELECTED,
            alarmArmed = false,
        )
        private fun apply(): ConnectorActionOutcome {
            calls++
            version++
            return ConnectorActionOutcome(true, "applied", "action-$calls")
        }
        override fun setOrigin(point: MapPoint) = apply()
        override fun setDestination(point: MapPoint) = apply()
        override fun selectJourney(routeId: String) = apply()
        override fun setBoardingStop(stop: ConnectorStopState) = apply()
        override fun armArrivalAlarm() = apply()
        override fun cancelArrivalAlarm() = apply()
    }

    private class Gateway(private val queuedAt: Long) : ConnectorGatewayTransport {
        val receipts = mutableListOf<JSONObject>()
        override fun publishSnapshot(payload: String) = Unit
        override fun fetchPendingCommands(): String = """
            {"commands":[{"command_id":"test-id","expected_state_version":7,
            "user_confirmed":true,"queued_at_epoch_seconds":$queuedAt,
            "command":{"type":"arm_arrival_alarm","idempotency_key":"test-key"}}]}
        """.trimIndent()
        override fun publishReceipt(payload: String) {
            receipts += JSONObject(payload)
        }
    }

    private fun executeAt(queuedAt: Long, now: Long = 1_000L): Triple<Int, Int, String> {
        val port = ActionPort()
        val gateway = Gateway(queuedAt)
        val result = ConnectorSyncClient(
            processor = ConnectorCommandProcessor(port),
            transport = gateway,
            nowEpochSeconds = { now },
            maxCommandAgeSeconds = 120L,
            maxFutureClockSkewSeconds = 30L,
        ).syncOnce()
        assertEquals(1, result.processedCommands)
        assertEquals(1, gateway.receipts.size)
        return Triple(result.appliedCommands, port.calls, gateway.receipts.single().getString("status"))
    }

    @Test fun validTimestampWindowIncludesExactBoundaries() {
        assertEquals(Triple(1, 1, "APPLIED"), executeAt(880L))
        assertEquals(Triple(1, 1, "APPLIED"), executeAt(1_000L))
        assertEquals(Triple(1, 1, "APPLIED"), executeAt(1_030L))
    }

    @Test fun expiredAndFutureDatedCommandsFailClosedWithReceipts() {
        for (queuedAt in listOf(879L, 1_031L, Long.MIN_VALUE, -1L, Long.MAX_VALUE)) {
            assertEquals(
                "queuedAt=$queuedAt",
                Triple(0, 0, "REJECTED_EXPIRED_COMMAND"),
                executeAt(queuedAt),
            )
        }
    }

    @Test fun impossibleNegativeDeviceClockRejectsCommand() {
        assertEquals(
            Triple(0, 0, "REJECTED_EXPIRED_COMMAND"),
            executeAt(queuedAt = 0L, now = -1L),
        )
    }

    @Test fun invalidWindowConfigurationIsRejected() {
        val port = ActionPort()
        val gateway = Gateway(1_000L)
        assertTrue(runCatching {
            ConnectorSyncClient(ConnectorCommandProcessor(port), gateway, maxCommandAgeSeconds = -1)
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            ConnectorSyncClient(ConnectorCommandProcessor(port), gateway, maxFutureClockSkewSeconds = -1)
        }.exceptionOrNull() is IllegalArgumentException)
    }
}
