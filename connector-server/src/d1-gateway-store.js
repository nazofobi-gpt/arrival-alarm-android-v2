function clone(value) {
  return structuredClone(value);
}

function validateSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== "object") throw new Error("snapshot_required");
  if (snapshot.schema_version !== 1) throw new Error("unsupported_schema_version");
  if (!Number.isInteger(snapshot.state_version) || snapshot.state_version < 1) {
    throw new Error("invalid_state_version");
  }
  if (!Number.isFinite(snapshot.source_updated_at_epoch_seconds)) {
    throw new Error("invalid_source_timestamp");
  }
  if (typeof snapshot.journey_phase !== "string" || !snapshot.journey_phase) {
    throw new Error("invalid_journey_phase");
  }
  return snapshot;
}

function envelopeFromRow(row) {
  if (!row) return null;
  return {
    command_id: row.command_id,
    expected_state_version: Number(row.expected_state_version),
    user_confirmed: Boolean(row.user_confirmed),
    queued_at_epoch_seconds: Number(row.queued_at_epoch_seconds),
    status: row.status,
    device_id: row.device_id ?? null,
    command: JSON.parse(row.command_json),
    receipt: row.receipt_json ? JSON.parse(row.receipt_json) : null,
    ...(row.completed_at_epoch_seconds == null
      ? {}
      : { completed_at_epoch_seconds: Number(row.completed_at_epoch_seconds) }),
  };
}

export class D1GatewayStore {
  constructor(db, { nowEpochSeconds = () => Math.floor(Date.now() / 1000) } = {}) {
    if (!db?.prepare) throw new Error("d1_binding_required");
    this.db = db;
    this.nowEpochSeconds = nowEpochSeconds;
  }

  async putSnapshot(userId, snapshot, deviceId = null) {
    validateSnapshot(snapshot);
    const receivedAt = this.nowEpochSeconds();
    await this.db
      .prepare(`
        INSERT INTO snapshots(user_id, payload_json, received_at_epoch_seconds, device_id)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
          payload_json = excluded.payload_json,
          received_at_epoch_seconds = excluded.received_at_epoch_seconds,
          device_id = excluded.device_id
      `)
      .bind(userId, JSON.stringify(snapshot), receivedAt, deviceId)
      .run();

    return {
      ...clone(snapshot),
      gateway_received_at_epoch_seconds: receivedAt,
      gateway_device_id: deviceId,
    };
  }

  async getSnapshot(userId) {
    const row = await this.db
      .prepare(`
        SELECT payload_json, received_at_epoch_seconds, device_id
        FROM snapshots
        WHERE user_id = ?
      `)
      .bind(userId)
      .first();

    if (!row) return null;
    return {
      ...JSON.parse(row.payload_json),
      gateway_received_at_epoch_seconds: Number(row.received_at_epoch_seconds),
      gateway_device_id: row.device_id ?? null,
    };
  }

  async enqueueCommand(userId, command, expectedStateVersion, deviceId = null) {
    if (!command?.type || !command?.idempotency_key) {
      throw new Error("invalid_command");
    }

    const commandId = crypto.randomUUID();
    const queuedAt = this.nowEpochSeconds();
    await this.db
      .prepare(`
        INSERT OR IGNORE INTO commands(
          command_id, user_id, idempotency_key, expected_state_version,
          user_confirmed, queued_at_epoch_seconds, status, device_id,
          command_json, receipt_json, completed_at_epoch_seconds
        ) VALUES (?, ?, ?, ?, 1, ?, 'queued', ?, ?, NULL, NULL)
      `)
      .bind(
        commandId,
        userId,
        command.idempotency_key,
        expectedStateVersion,
        queuedAt,
        deviceId,
        JSON.stringify(command)
      )
      .run();

    return this.commandByIdempotencyKey(userId, command.idempotency_key);
  }

  async pendingCommands(userId, deviceId = null) {
    const statement =
      deviceId == null
        ? this.db.prepare(`
            SELECT * FROM commands
            WHERE user_id = ? AND status = 'queued'
            ORDER BY queued_at_epoch_seconds, command_id
          `).bind(userId)
        : this.db.prepare(`
            SELECT * FROM commands
            WHERE user_id = ? AND status = 'queued'
              AND (device_id IS NULL OR device_id = ?)
            ORDER BY queued_at_epoch_seconds, command_id
          `).bind(userId, deviceId);

    const result = await statement.all();
    return (result.results ?? []).map(envelopeFromRow);
  }

  async submitReceipt(userId, receipt, deviceId = null) {
    const key = receipt?.idempotency_key;
    const existing = key
      ? await this.commandByIdempotencyKey(userId, key)
      : null;
    if (!existing) throw new Error("command_not_found");
    if (
      existing.device_id != null &&
      deviceId != null &&
      existing.device_id !== deviceId
    ) {
      throw new Error("device_mismatch");
    }

    await this.db
      .prepare(`
        UPDATE commands
        SET status = 'completed',
            receipt_json = ?,
            completed_at_epoch_seconds = ?
        WHERE user_id = ? AND idempotency_key = ?
      `)
      .bind(JSON.stringify(receipt), this.nowEpochSeconds(), userId, key)
      .run();

    return this.commandByIdempotencyKey(userId, key);
  }

  async commandByIdempotencyKey(userId, idempotencyKey) {
    const row = await this.db
      .prepare(`
        SELECT * FROM commands
        WHERE user_id = ? AND idempotency_key = ?
      `)
      .bind(userId, idempotencyKey)
      .first();
    return envelopeFromRow(row);
  }
}
