package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorIdempotencyStoreTest {
    private class FakePort : ConnectorActionPort {
        var writes = 0
        var current = ArrivalAlarmConnectorSnapshot(
            stateVersion = 7,
            capturedAtEpochSeconds = 1_000,
            sourceUpdatedAtEpochSeconds = 1_000,
            source = "test",
            journeyPhase = JourneyPhase.DESTINATION_SELECTED,
        )

        override fun readSnapshot(): ArrivalAlarmConnectorSnapshot = current

        private fun applied(message: String): ConnectorActionOutcome {
            writes += 1
            current = current.copy(stateVersion = current.stateVersion + 1)
            return ConnectorActionOutcome(true, message, "action-$writes")
        }

        override fun setOrigin(point: MapPoint) = applied("origin")
        override fun setDestination(point: MapPoint) = applied("destination")
        override fun selectJourney(routeId: String) = applied("journey")
        override fun setBoardingStop(stop: ConnectorStopState) = applied("boarding")
        override fun armArrivalAlarm() = applied("armed")
        override fun cancelArrivalAlarm() = applied("cancelled")
    }

    @Test fun completedReceiptPreventsReplayAcrossProcessorRecreation() {
        val port = FakePort()
        val store = InMemoryConnectorIdempotencyStore()
        val command = ArrivalAlarmConnectorCommand.ArmArrivalAlarm("persisted-command")

        val first = ConnectorCommandProcessor(
            port = port,
            idempotencyStore = store,
        ).execute(
            command = command,
            userConfirmed = true,
            expectedStateVersion = 7,
            nowEpochSeconds = 1_000,
        )

        val recreated = ConnectorCommandProcessor(
            port = port,
            idempotencyStore = store,
        ).execute(
            command = command,
            userConfirmed = true,
            expectedStateVersion = 7,
            nowEpochSeconds = 1_000,
        )

        assertEquals(ConnectorCommandStatus.APPLIED, first.status)
        assertEquals(1, port.writes)
        assertTrue(recreated.duplicate)
        assertEquals(first.actionId, recreated.actionId)
        assertEquals(first.stateVersionAfter, recreated.stateVersionAfter)
    }

    @Test fun unfinishedClaimFailsClosedInsteadOfRepeatingSideEffect() {
        val port = FakePort()
        val store = InMemoryConnectorIdempotencyStore()
        store.begin(
            ConnectorIdempotencyEntry(
                idempotencyKey = "indeterminate-command",
                commandType = "ArmArrivalAlarm",
                stateVersionBefore = 7,
            )
        )

        val receipt = ConnectorCommandProcessor(
            port = port,
            idempotencyStore = store,
        ).execute(
            command = ArrivalAlarmConnectorCommand.ArmArrivalAlarm("indeterminate-command"),
            userConfirmed = true,
            expectedStateVersion = 7,
            nowEpochSeconds = 1_000,
        )

        assertEquals(
            ConnectorCommandStatus.REJECTED_IDEMPOTENCY_RECOVERY_REQUIRED,
            receipt.status,
        )
        assertEquals(0, port.writes)
        assertEquals(7, receipt.stateVersionBefore)
    }
}
