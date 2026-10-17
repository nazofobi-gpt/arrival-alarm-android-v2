package com.nazofobi.arrivalalarm

data class ConnectorQueuedCommand(
    val commandId: String,
    val expectedStateVersion: Long,
    val userConfirmed: Boolean,
    val queuedAtEpochSeconds: Long?,
    val command: ArrivalAlarmConnectorCommand,
)

data class ConnectorSyncResult(
    val snapshotStateVersionBefore: Long,
    val processedCommands: Int,
    val appliedCommands: Int,
    val snapshotStateVersionAfter: Long,
)

/**
 * One deterministic device↔gateway synchronization cycle.
 *
 * The device publishes the latest canonical snapshot, fetches allow-listed commands,
 * executes them through [ConnectorCommandProcessor], uploads receipts, and republishes
 * state after any command so read tools immediately observe the resulting version.
 */
class ConnectorSyncClient(
    private val processor: ConnectorCommandProcessor,
    private val transport: ConnectorGatewayTransport,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val maxCommandAgeSeconds: Long = 120,
    private val maxFutureClockSkewSeconds: Long = 30,
) {
    init {
        require(maxCommandAgeSeconds >= 0) { "Command age budget must be nonnegative" }
        require(maxFutureClockSkewSeconds >= 0) { "Future clock-skew budget must be nonnegative" }
    }

    /** Compare nonnegative epoch seconds without a signed-Long subtraction overflow. */
    private fun isOutsideCommandTimeWindow(queuedAt: Long?, now: Long): Boolean {
        if (queuedAt == null || queuedAt < 0 || now < 0) return true
        return if (queuedAt > now) {
            queuedAt - now > maxFutureClockSkewSeconds
        } else {
            now - queuedAt > maxCommandAgeSeconds
        }
    }

    @Synchronized
    fun syncOnce(): ConnectorSyncResult {
        val before = processor.snapshot()
        transport.publishSnapshot(ConnectorJsonCodec.encodeSnapshot(before))

        val envelopes = ConnectorJsonCodec.decodePendingCommands(
            transport.fetchPendingCommands()
        )
        val receipts = envelopes.map { envelope ->
            val now = nowEpochSeconds()
            val queuedAt = envelope.queuedAtEpochSeconds
            val receipt = if (isOutsideCommandTimeWindow(queuedAt, now)) {
                val current = processor.snapshot()
                ConnectorCommandReceipt(
                    idempotencyKey = envelope.command.idempotencyKey,
                    commandType = envelope.command::class.simpleName ?: "UnknownCommand",
                    status = ConnectorCommandStatus.REJECTED_EXPIRED_COMMAND,
                    message = "Connector command timestamp is outside the allowed device time window",
                    stateVersionBefore = current.stateVersion,
                    stateVersionAfter = current.stateVersion,
                )
            } else {
                processor.execute(
                    command = envelope.command,
                    userConfirmed = envelope.userConfirmed,
                    expectedStateVersion = envelope.expectedStateVersion,
                    nowEpochSeconds = now,
                )
            }
            transport.publishReceipt(ConnectorJsonCodec.encodeReceipt(receipt))
            receipt
        }

        val after = processor.snapshot()
        if (envelopes.isNotEmpty()) {
            transport.publishSnapshot(ConnectorJsonCodec.encodeSnapshot(after))
        }

        return ConnectorSyncResult(
            snapshotStateVersionBefore = before.stateVersion,
            processedCommands = receipts.size,
            appliedCommands = receipts.count {
                it.status == ConnectorCommandStatus.APPLIED && !it.duplicate
            },
            snapshotStateVersionAfter = after.stateVersion,
        )
    }
}

interface ConnectorGatewayTransport {
    fun publishSnapshot(payload: String)
    fun fetchPendingCommands(): String
    fun publishReceipt(payload: String)
}
