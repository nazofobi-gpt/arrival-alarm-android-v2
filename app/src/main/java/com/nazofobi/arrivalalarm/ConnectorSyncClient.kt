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
) {
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
            val receipt = if (queuedAt != null && now - queuedAt > maxCommandAgeSeconds) {
                val current = processor.snapshot()
                ConnectorCommandReceipt(
                    idempotencyKey = envelope.command.idempotencyKey,
                    commandType = envelope.command::class.simpleName ?: "UnknownCommand",
                    status = ConnectorCommandStatus.REJECTED_EXPIRED_COMMAND,
                    message = "Connector command expired before device execution",
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
