package com.nazofobi.arrivalalarm

/**
 * Provider-neutral contract exposed to a future remote gateway / ChatGPT connector.
 * The connector never receives raw database access; it can only read a bounded snapshot
 * and invoke explicitly allow-listed commands through [ConnectorActionPort].
 */
data class ConnectorStopState(
    val id: String? = null,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class ConnectorTripState(
    val journeyId: String? = null,
    val tripId: String? = null,
    val routeId: String? = null,
    val line: String? = null,
    val direction: String? = null,
    val origin: ConnectorStopState? = null,
    val destination: ConnectorStopState? = null,
    val boardingStop: ConnectorStopState? = null,
    val previousStop: ConnectorStopState? = null,
    val currentStop: ConnectorStopState? = null,
    val nextStop: ConnectorStopState? = null,
)

data class ArrivalAlarmConnectorSnapshot(
    val schemaVersion: Int = 1,
    val stateVersion: Long,
    val capturedAtEpochSeconds: Long,
    val sourceUpdatedAtEpochSeconds: Long,
    val staleAfterSeconds: Long = 120,
    val source: String,
    val journeyPhase: JourneyPhase,
    val activeTrip: ConnectorTripState? = null,
    val distanceToDestinationMeters: Double? = null,
    val alarmArmed: Boolean = false,
) {
    fun isStale(nowEpochSeconds: Long = capturedAtEpochSeconds): Boolean =
        sourceUpdatedAtEpochSeconds <= 0L ||
            nowEpochSeconds - sourceUpdatedAtEpochSeconds > staleAfterSeconds
}

sealed interface ArrivalAlarmConnectorCommand {
    val idempotencyKey: String

    data class SetOrigin(
        val point: MapPoint,
        override val idempotencyKey: String,
    ) : ArrivalAlarmConnectorCommand

    data class SetDestination(
        val point: MapPoint,
        override val idempotencyKey: String,
    ) : ArrivalAlarmConnectorCommand

    data class SelectJourney(
        val routeId: String,
        override val idempotencyKey: String,
    ) : ArrivalAlarmConnectorCommand

    data class SetBoardingStop(
        val stop: ConnectorStopState,
        override val idempotencyKey: String,
    ) : ArrivalAlarmConnectorCommand

    data class ArmArrivalAlarm(
        override val idempotencyKey: String,
    ) : ArrivalAlarmConnectorCommand

    data class CancelArrivalAlarm(
        override val idempotencyKey: String,
    ) : ArrivalAlarmConnectorCommand
}

data class ConnectorActionOutcome(
    val applied: Boolean,
    val message: String,
    val actionId: String? = null,
)

interface ConnectorActionPort {
    fun readSnapshot(): ArrivalAlarmConnectorSnapshot
    fun setOrigin(point: MapPoint): ConnectorActionOutcome
    fun setDestination(point: MapPoint): ConnectorActionOutcome
    fun selectJourney(routeId: String): ConnectorActionOutcome
    fun setBoardingStop(stop: ConnectorStopState): ConnectorActionOutcome
    fun armArrivalAlarm(): ConnectorActionOutcome
    fun cancelArrivalAlarm(): ConnectorActionOutcome
}

enum class ConnectorCommandStatus {
    APPLIED,
    REJECTED_CONFIRMATION_REQUIRED,
    REJECTED_STALE_STATE,
    REJECTED_STATE_VERSION,
    REJECTED_EXPIRED_COMMAND,
    REJECTED_DOMAIN,
}

data class ConnectorCommandReceipt(
    val idempotencyKey: String,
    val commandType: String,
    val status: ConnectorCommandStatus,
    val message: String,
    val stateVersionBefore: Long,
    val stateVersionAfter: Long,
    val actionId: String? = null,
    val duplicate: Boolean = false,
)

/**
 * Enforces connector-side safety before delegating to the app's own domain/state machine.
 *
 * Write commands always require explicit user confirmation. A stale snapshot or an optional
 * optimistic state-version mismatch fails closed. Successful writes are idempotent.
 */
class ConnectorCommandProcessor(
    private val port: ConnectorActionPort,
    private val maxReceipts: Int = 128,
) {
    private val successfulReceipts = LinkedHashMap<String, ConnectorCommandReceipt>()

    fun snapshot(): ArrivalAlarmConnectorSnapshot = port.readSnapshot()

    fun execute(
        command: ArrivalAlarmConnectorCommand,
        userConfirmed: Boolean,
        expectedStateVersion: Long? = null,
        nowEpochSeconds: Long,
    ): ConnectorCommandReceipt {
        successfulReceipts[command.idempotencyKey]?.let {
            return it.copy(duplicate = true)
        }

        val before = port.readSnapshot()
        if (!userConfirmed) {
            return rejected(
                command,
                ConnectorCommandStatus.REJECTED_CONFIRMATION_REQUIRED,
                "Kullanıcı onayı gerekli",
                before,
            )
        }
        if (before.isStale(nowEpochSeconds)) {
            return rejected(
                command,
                ConnectorCommandStatus.REJECTED_STALE_STATE,
                "Uygulama durumu güncel değil; write uygulanmadı",
                before,
            )
        }
        if (expectedStateVersion != null && expectedStateVersion != before.stateVersion) {
            return rejected(
                command,
                ConnectorCommandStatus.REJECTED_STATE_VERSION,
                "Uygulama durumu değişti; komut fresh state ile yeniden hazırlanmalı",
                before,
            )
        }

        val outcome = when (command) {
            is ArrivalAlarmConnectorCommand.SetOrigin -> port.setOrigin(command.point)
            is ArrivalAlarmConnectorCommand.SetDestination -> port.setDestination(command.point)
            is ArrivalAlarmConnectorCommand.SelectJourney -> port.selectJourney(command.routeId)
            is ArrivalAlarmConnectorCommand.SetBoardingStop -> port.setBoardingStop(command.stop)
            is ArrivalAlarmConnectorCommand.ArmArrivalAlarm -> port.armArrivalAlarm()
            is ArrivalAlarmConnectorCommand.CancelArrivalAlarm -> port.cancelArrivalAlarm()
        }
        val after = port.readSnapshot()
        val receipt = ConnectorCommandReceipt(
            idempotencyKey = command.idempotencyKey,
            commandType = command::class.simpleName ?: "UnknownCommand",
            status = if (outcome.applied) ConnectorCommandStatus.APPLIED else ConnectorCommandStatus.REJECTED_DOMAIN,
            message = outcome.message,
            stateVersionBefore = before.stateVersion,
            stateVersionAfter = after.stateVersion,
            actionId = outcome.actionId,
        )
        if (outcome.applied) remember(receipt)
        return receipt
    }

    private fun rejected(
        command: ArrivalAlarmConnectorCommand,
        status: ConnectorCommandStatus,
        message: String,
        snapshot: ArrivalAlarmConnectorSnapshot,
    ) = ConnectorCommandReceipt(
        idempotencyKey = command.idempotencyKey,
        commandType = command::class.simpleName ?: "UnknownCommand",
        status = status,
        message = message,
        stateVersionBefore = snapshot.stateVersion,
        stateVersionAfter = snapshot.stateVersion,
    )

    private fun remember(receipt: ConnectorCommandReceipt) {
        successfulReceipts[receipt.idempotencyKey] = receipt
        while (successfulReceipts.size > maxReceipts.coerceAtLeast(1)) {
            successfulReceipts.remove(successfulReceipts.keys.first())
        }
    }
}
