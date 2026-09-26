import { randomUUID } from "node:crypto";

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
}
