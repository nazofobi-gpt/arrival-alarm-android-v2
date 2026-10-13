package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorContractTest {
    private class FakePort(
        var current: ArrivalAlarmConnectorSnapshot,
    ) : ConnectorActionPort {
        var writes = 0

        override fun readSnapshot(): ArrivalAlarmConnectorSnapshot = current

        private fun applied(message: String): ConnectorActionOutcome {
            writes++
            current = current.copy(
                stateVersion = current.stateVersion + 1,
                sourceUpdatedAtEpochSeconds = current.capturedAtEpochSeconds,
            )
            return ConnectorActionOutcome(true, message, "action-" + writes)
        }

        override fun setOrigin(point: MapPoint) = applied("origin:" + point.label)
        override fun setDestination(point: MapPoint) = applied("destination:" + point.label)
        override fun selectJourney(routeId: String) = applied("journey:" + routeId)
        override fun setBoardingStop(stop: ConnectorStopState) = applied("boarding:" + stop.name)
        override fun armArrivalAlarm() = applied("armed")
        override fun cancelArrivalAlarm() = applied("cancelled")
    }

    private fun snapshot(
        now: Long = 1_000,
        sourceUpdatedAt: Long = 995,
        stateVersion: Long = 7,
    ) = ArrivalAlarmConnectorSnapshot(
        stateVersion = stateVersion,
        capturedAtEpochSeconds = now,
        sourceUpdatedAtEpochSeconds = sourceUpdatedAt,
        source = "app-domain",
        journeyPhase = JourneyPhase.DESTINATION_SELECTED,
        activeTrip = ConnectorTripState(
            routeId = "route-1",
            line = "RE 1",
            direction = "Bremen Hbf",
            previousStop = ConnectorStopState(name = "Diepholz"),
            currentStop = ConnectorStopState(name = "Barnstorf"),
            nextStop = ConnectorStopState(name = "Twistringen"),
        ),
        alarmArmed = false,
    )

    @Test fun snapshotReadIsSideEffectFree() {
        val port = FakePort(snapshot())
        val processor = ConnectorCommandProcessor(port)

        val state = processor.snapshot()

        assertEquals("RE 1", state.activeTrip?.line)
        assertEquals("Twistringen", state.activeTrip?.nextStop?.name)
        assertEquals(0, port.writes)
        assertFalse(state.isStale(1_000))
    }

    @Test fun everyWriteRequiresExplicitConfirmation() {
        val port = FakePort(snapshot())
        val processor = ConnectorCommandProcessor(port)

        val receipt = processor.execute(
            ArrivalAlarmConnectorCommand.SetDestination(
                MapPoint(53.0834, 8.8137, "Bremen Hbf"),
                "cmd-1",
            ),
            userConfirmed = false,
            nowEpochSeconds = 1_000,
        )

        assertEquals(ConnectorCommandStatus.REJECTED_CONFIRMATION_REQUIRED, receipt.status)
        assertEquals(0, port.writes)
        assertEquals(7, receipt.stateVersionAfter)
    }

    @Test fun staleStateFailsClosedBeforeDomainWrite() {
        val port = FakePort(snapshot(now = 2_000, sourceUpdatedAt = 1_000))
        val processor = ConnectorCommandProcessor(port)

        val receipt = processor.execute(
            ArrivalAlarmConnectorCommand.ArmArrivalAlarm("cmd-stale"),
            userConfirmed = true,
            nowEpochSeconds = 2_000,
        )

        assertEquals(ConnectorCommandStatus.REJECTED_STALE_STATE, receipt.status)
        assertEquals(0, port.writes)
    }

    @Test fun snapshotFreshnessRejectsFutureInvalidAndOverflowingClocks() {
        val current = snapshot(now = 1_000, sourceUpdatedAt = 880)
        assertFalse(current.isStale()) // The 120-second boundary is inclusive.
        assertTrue(current.copy(sourceUpdatedAtEpochSeconds = 879).isStale())
        assertTrue(current.copy(sourceUpdatedAtEpochSeconds = 1_001).isStale())
        assertTrue(current.copy(sourceUpdatedAtEpochSeconds = Long.MAX_VALUE).isStale())
        assertTrue(current.copy(sourceUpdatedAtEpochSeconds = 1).isStale(Long.MAX_VALUE))
        assertTrue(current.copy(sourceUpdatedAtEpochSeconds = -1).isStale())
        assertTrue(current.copy(staleAfterSeconds = -1).isStale())
        assertTrue(current.isStale(nowEpochSeconds = -1))
    }

    @Test fun confirmedCommandCannotUseFutureDatedSnapshot() {
        val port = FakePort(snapshot(now = 1_000, sourceUpdatedAt = 1_001))
        val receipt = ConnectorCommandProcessor(port).execute(
            ArrivalAlarmConnectorCommand.ArmArrivalAlarm("cmd-future-source"),
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )
        assertEquals(ConnectorCommandStatus.REJECTED_STALE_STATE, receipt.status)
        assertEquals(0, port.writes)
    }

    @Test fun optimisticStateVersionMismatchFailsClosed() {
        val port = FakePort(snapshot(stateVersion = 9))
        val processor = ConnectorCommandProcessor(port)

        val receipt = processor.execute(
            ArrivalAlarmConnectorCommand.SelectJourney("route-2", "cmd-version"),
            userConfirmed = true,
            expectedStateVersion = 8,
            nowEpochSeconds = 1_000,
        )

        assertEquals(ConnectorCommandStatus.REJECTED_STATE_VERSION, receipt.status)
        assertEquals(0, port.writes)
    }

    @Test fun successfulWriteIsIdempotentAndReturnsReceipt() {
        val port = FakePort(snapshot())
        val processor = ConnectorCommandProcessor(port)
        val command = ArrivalAlarmConnectorCommand.ArmArrivalAlarm("cmd-arm")

        val first = processor.execute(
            command,
            userConfirmed = true,
            expectedStateVersion = 7,
            nowEpochSeconds = 1_000,
        )
        val duplicate = processor.execute(
            command,
            userConfirmed = true,
            expectedStateVersion = 7,
            nowEpochSeconds = 1_000,
        )

        assertEquals(ConnectorCommandStatus.APPLIED, first.status)
        assertEquals(7, first.stateVersionBefore)
        assertEquals(8, first.stateVersionAfter)
        assertEquals("action-1", first.actionId)
        assertEquals(1, port.writes)
        assertTrue(duplicate.duplicate)
        assertEquals(first.actionId, duplicate.actionId)
        assertEquals(first.stateVersionAfter, duplicate.stateVersionAfter)
    }

    @Test fun completedIdempotencyKeyCannotAcknowledgeAnotherCommandType() {
        val store = InMemoryConnectorIdempotencyStore()
        val port = FakePort(snapshot())
        val processor = ConnectorCommandProcessor(port, idempotencyStore = store)
        val destination = ArrivalAlarmConnectorCommand.SetDestination(
            MapPoint(53.0834, 8.8137, "Bremen Hbf"), "shared-command-key",
        )

        val first = processor.execute(
            destination, userConfirmed = true, nowEpochSeconds = 1_000,
        )
        val wrongType = processor.execute(
            ArrivalAlarmConnectorCommand.ArmArrivalAlarm("shared-command-key"),
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )

        assertEquals(ConnectorCommandStatus.APPLIED, first.status)
        assertEquals(ConnectorCommandStatus.REJECTED_DOMAIN, wrongType.status)
        assertEquals("ArmArrivalAlarm", wrongType.commandType)
        assertFalse(wrongType.duplicate)
        assertEquals(8, wrongType.stateVersionBefore)
        assertEquals(8, wrongType.stateVersionAfter)
        assertEquals(1, port.writes)
        assertEquals(first, store.get("shared-command-key")?.receipt)

        val originalRetry = processor.execute(
            destination, userConfirmed = true, nowEpochSeconds = 1_000,
        )
        assertTrue(originalRetry.duplicate)
        assertEquals(first.actionId, originalRetry.actionId)
        assertEquals(1, port.writes)
    }

    @Test fun pendingIdempotencyKeyCannotBeReusedForAnotherCommandType() {
        val store = InMemoryConnectorIdempotencyStore()
        store.begin(
            ConnectorIdempotencyEntry(
                idempotencyKey = "pending-shared-key",
                commandType = "SetDestination",
                stateVersionBefore = 7,
            ),
        )
        val port = FakePort(snapshot())
        val processor = ConnectorCommandProcessor(port, idempotencyStore = store)

        val wrongType = processor.execute(
            ArrivalAlarmConnectorCommand.ArmArrivalAlarm("pending-shared-key"),
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )
        assertEquals(ConnectorCommandStatus.REJECTED_DOMAIN, wrongType.status)
        assertFalse(wrongType.duplicate)
        assertEquals(0, port.writes)
        assertEquals("SetDestination", store.get("pending-shared-key")?.commandType)
        assertEquals(null, store.get("pending-shared-key")?.receipt)

        val originalRetry = processor.execute(
            ArrivalAlarmConnectorCommand.SetDestination(
                MapPoint(53.0834, 8.8137, "Bremen Hbf"), "pending-shared-key",
            ),
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )
        assertEquals(
            ConnectorCommandStatus.REJECTED_IDEMPOTENCY_RECOVERY_REQUIRED,
            originalRetry.status,
        )
        assertEquals(0, port.writes)
    }

    @Test fun reusedKeyForDifferentCompletedCommandIsRejected() {
        val port = FakePort(snapshot())
        val processor = ConnectorCommandProcessor(port)
        val destination = ArrivalAlarmConnectorCommand.SetDestination(
            MapPoint(53.0834, 8.8137, "Bremen Hbf"),
            "cross-type-completed",
        )

        val applied = processor.execute(
            destination,
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )
        val rejected = processor.execute(
            ArrivalAlarmConnectorCommand.ArmArrivalAlarm("cross-type-completed"),
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )
        val originalReplay = processor.execute(
            destination,
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )

        assertEquals(ConnectorCommandStatus.APPLIED, applied.status)
        assertEquals(ConnectorCommandStatus.REJECTED_DOMAIN, rejected.status)
        assertEquals("ArmArrivalAlarm", rejected.commandType)
        assertEquals(8L, rejected.stateVersionBefore)
        assertEquals(8L, rejected.stateVersionAfter)
        assertFalse(rejected.duplicate)
        assertEquals(null, rejected.actionId)
        assertEquals(1, port.writes)
        assertTrue(originalReplay.duplicate)
        assertEquals(applied.actionId, originalReplay.actionId)
        assertEquals(1, port.writes)
    }

    @Test fun reusedKeyForDifferentPendingCommandPreservesRecoveryClaim() {
        val port = FakePort(snapshot())
        val store = InMemoryConnectorIdempotencyStore()
        store.begin(
            ConnectorIdempotencyEntry(
                idempotencyKey = "cross-type-pending",
                commandType = "SetDestination",
                stateVersionBefore = 7,
            )
        )
        val processor = ConnectorCommandProcessor(port, idempotencyStore = store)

        val rejected = processor.execute(
            ArrivalAlarmConnectorCommand.ArmArrivalAlarm("cross-type-pending"),
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )
        val pendingOriginal = processor.execute(
            ArrivalAlarmConnectorCommand.SetDestination(
                MapPoint(53.0834, 8.8137, "Bremen Hbf"),
                "cross-type-pending",
            ),
            userConfirmed = true,
            nowEpochSeconds = 1_000,
        )

        assertEquals(ConnectorCommandStatus.REJECTED_DOMAIN, rejected.status)
        assertEquals("ArmArrivalAlarm", rejected.commandType)
        assertEquals(ConnectorCommandStatus.REJECTED_IDEMPOTENCY_RECOVERY_REQUIRED, pendingOriginal.status)
        assertEquals("SetDestination", pendingOriginal.commandType)
        assertEquals(7L, pendingOriginal.stateVersionBefore)
        assertEquals(0, port.writes)
    }
}
