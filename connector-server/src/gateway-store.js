import { randomUUID } from "node:crypto";
import { DatabaseSync } from "node:sqlite";

const clone = (value) => structuredClone(value);

export function validateSnapshot(snapshot) {
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

export class InMemoryGatewayStore {
  constructor({ nowEpochSeconds = () => Math.floor(Date.now() / 1000) } = {}) {
    this.nowEpochSeconds = nowEpochSeconds;
    this.snapshots = new Map();
    this.commands = new Map();
  }

  putSnapshot(userId, snapshot, deviceId = null) {
    validateSnapshot(snapshot);
    const stored = {
      ...clone(snapshot),
      gateway_received_at_epoch_seconds: this.nowEpochSeconds(),
      gateway_device_id: deviceId,
    };
    this.snapshots.set(userId, stored);
    return clone(stored);
  }

  getSnapshot(userId) {
    const value = this.snapshots.get(userId);
    return value ? clone(value) : null;
  }

  enqueueCommand(userId, command, expectedStateVersion, deviceId = null) {
    if (!command?.type || !command?.idempotency_key) {
      throw new Error("invalid_command");
    }
    const queue = this.commands.get(userId) ?? [];
    const existing = queue.find((item) => item.command.idempotency_key === command.idempotency_key);
    if (existing) return clone(existing);

    const envelope = {
      command_id: randomUUID(),
      expected_state_version: expectedStateVersion,
      user_confirmed: true,
      queued_at_epoch_seconds: this.nowEpochSeconds(),
      status: "queued",
      device_id: deviceId,
      command: clone(command),
      receipt: null,
    };
    queue.push(envelope);
    this.commands.set(userId, queue);
    return clone(envelope);
  }

  pendingCommands(userId, deviceId = null) {
    return clone(
      (this.commands.get(userId) ?? []).filter(
        (item) =>
          item.status === "queued" &&
          (item.device_id == null || deviceId == null || item.device_id === deviceId)
      )
    );
  }

  submitReceipt(userId, receipt, deviceId = null) {
    const queue = this.commands.get(userId) ?? [];
    const item = queue.find((candidate) =>
      candidate.command.idempotency_key === receipt?.idempotency_key
    );
    if (!item) throw new Error("command_not_found");
    if (item.device_id != null && deviceId != null && item.device_id !== deviceId) {
      throw new Error("device_mismatch");
    }
    item.status = "completed";
    item.receipt = clone(receipt);
    item.completed_at_epoch_seconds = this.nowEpochSeconds();
    return clone(item);
  }

  commandByIdempotencyKey(userId, idempotencyKey) {
    const item = (this.commands.get(userId) ?? []).find(
      (candidate) => candidate.command.idempotency_key === idempotencyKey
    );
    return item ? clone(item) : null;
  }

  close() {}
}

export class SqliteGatewayStore {
  constructor({
    path,
    nowEpochSeconds = () => Math.floor(Date.now() / 1000),
  }) {
    if (!path || typeof path !== "string") throw new Error("sqlite_path_required");
    this.nowEpochSeconds = nowEpochSeconds;
    this.db = new DatabaseSync(path, {
      open: true,
      timeout: 5_000,
      allowExtension: false,
    });
    this.db.exec("PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;");
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS snapshots (
        user_id TEXT PRIMARY KEY,
        payload_json TEXT NOT NULL,
        received_at_epoch_seconds INTEGER NOT NULL,
        device_id TEXT
      ) STRICT;

      CREATE TABLE IF NOT EXISTS commands (
        command_id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        idempotency_key TEXT NOT NULL,
        expected_state_version INTEGER NOT NULL,
        user_confirmed INTEGER NOT NULL,
        queued_at_epoch_seconds INTEGER NOT NULL,
        status TEXT NOT NULL,
        device_id TEXT,
        command_json TEXT NOT NULL,
        receipt_json TEXT,
        completed_at_epoch_seconds INTEGER,
        UNIQUE(user_id, idempotency_key)
      ) STRICT;

      CREATE INDEX IF NOT EXISTS commands_user_status
        ON commands(user_id, status, queued_at_epoch_seconds);
    `);

    this.upsertSnapshot = this.db.prepare(`
      INSERT INTO snapshots(user_id, payload_json, received_at_epoch_seconds, device_id)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(user_id) DO UPDATE SET
        payload_json = excluded.payload_json,
        received_at_epoch_seconds = excluded.received_at_epoch_seconds,
        device_id = excluded.device_id
    `);
    this.selectSnapshot = this.db.prepare(`
      SELECT payload_json, received_at_epoch_seconds, device_id
      FROM snapshots WHERE user_id = ?
    `);
    this.selectCommand = this.db.prepare(`
      SELECT * FROM commands WHERE user_id = ? AND idempotency_key = ?
    `);
    this.insertCommand = this.db.prepare(`
      INSERT INTO commands(
        command_id, user_id, idempotency_key, expected_state_version,
        user_confirmed, queued_at_epoch_seconds, status, device_id,
        command_json, receipt_json, completed_at_epoch_seconds
      ) VALUES (?, ?, ?, ?, 1, ?, 'queued', ?, ?, NULL, NULL)
    `);
    this.selectPendingForAnyDevice = this.db.prepare(`
      SELECT * FROM commands
      WHERE user_id = ? AND status = 'queued'
      ORDER BY queued_at_epoch_seconds, command_id
    `);
    this.selectPendingForDevice = this.db.prepare(`
      SELECT * FROM commands
      WHERE user_id = ? AND status = 'queued'
        AND (device_id IS NULL OR device_id = ?)
      ORDER BY queued_at_epoch_seconds, command_id
    `);
    this.completeCommand = this.db.prepare(`
      UPDATE commands
      SET status = 'completed', receipt_json = ?, completed_at_epoch_seconds = ?
      WHERE user_id = ? AND idempotency_key = ?
    `);
  }

  putSnapshot(userId, snapshot, deviceId = null) {
    validateSnapshot(snapshot);
    const receivedAt = this.nowEpochSeconds();
    this.upsertSnapshot.run(userId, JSON.stringify(snapshot), receivedAt, deviceId);
    return {
      ...clone(snapshot),
      gateway_received_at_epoch_seconds: receivedAt,
      gateway_device_id: deviceId,
    };
  }

  getSnapshot(userId) {
    const row = this.selectSnapshot.get(userId);
    if (!row) return null;
    return {
      ...JSON.parse(row.payload_json),
      gateway_received_at_epoch_seconds: Number(row.received_at_epoch_seconds),
      gateway_device_id: row.device_id ?? null,
    };
  }

  enqueueCommand(userId, command, expectedStateVersion, deviceId = null) {
    if (!command?.type || !command?.idempotency_key) {
      throw new Error("invalid_command");
    }
    const existing = this.commandByIdempotencyKey(userId, command.idempotency_key);
    if (existing) return existing;

    const commandId = randomUUID();
    const queuedAt = this.nowEpochSeconds();
    try {
      this.insertCommand.run(
        commandId,
        userId,
        command.idempotency_key,
        expectedStateVersion,
        queuedAt,
        deviceId,
        JSON.stringify(command),
      );
    } catch (error) {
      const concurrent = this.commandByIdempotencyKey(userId, command.idempotency_key);
      if (concurrent) return concurrent;
      throw error;
    }
    return this.commandByIdempotencyKey(userId, command.idempotency_key);
  }

  pendingCommands(userId, deviceId = null) {
    const rows = deviceId == null
      ? this.selectPendingForAnyDevice.all(userId)
      : this.selectPendingForDevice.all(userId, deviceId);
    return rows.map(envelopeFromRow);
  }

  submitReceipt(userId, receipt, deviceId = null) {
    const key = receipt?.idempotency_key;
    const existing = key ? this.commandByIdempotencyKey(userId, key) : null;
    if (!existing) throw new Error("command_not_found");
    if (existing.device_id != null && deviceId != null && existing.device_id !== deviceId) {
      throw new Error("device_mismatch");
    }
    this.completeCommand.run(
      JSON.stringify(receipt),
      this.nowEpochSeconds(),
      userId,
      key,
    );
    return this.commandByIdempotencyKey(userId, key);
  }

  commandByIdempotencyKey(userId, idempotencyKey) {
    return envelopeFromRow(this.selectCommand.get(userId, idempotencyKey));
  }

  close() {
    this.db.close();
  }
}

export function createGatewayStoreFromEnv(
  env = process.env,
  { nowEpochSeconds = () => Math.floor(Date.now() / 1000) } = {},
) {
  const sqlitePath = env.GATEWAY_SQLITE_PATH?.trim();
  if (sqlitePath) {
    return new SqliteGatewayStore({ path: sqlitePath, nowEpochSeconds });
  }
  if (env.NODE_ENV === "production") {
    throw new Error("production_durable_store_not_configured");
  }
  return new InMemoryGatewayStore({ nowEpochSeconds });
}
