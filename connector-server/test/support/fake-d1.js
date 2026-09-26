export class FakeD1Statement {
  constructor(db, sql) {
    this.db = db;
    this.sql = sql.replace(/\s+/g, " ").trim();
    this.args = [];
  }

  bind(...args) {
    this.args = args;
    return this;
  }

  async run() {
    if (this.sql.startsWith("INSERT INTO snapshots")) {
      const [userId, payloadJson, receivedAt, deviceId] = this.args;
      this.db.snapshots.set(userId, {
        payload_json: payloadJson,
        received_at_epoch_seconds: receivedAt,
        device_id: deviceId,
      });
      return { success: true };
    }

    if (this.sql.startsWith("INSERT OR IGNORE INTO commands")) {
      const [
        commandId,
        userId,
        idempotencyKey,
        expectedStateVersion,
        queuedAt,
        deviceId,
        commandJson,
      ] = this.args;
      const key = userId + "|" + idempotencyKey;
      if (!this.db.commands.has(key)) {
        this.db.commands.set(key, {
          command_id: commandId,
          user_id: userId,
          idempotency_key: idempotencyKey,
          expected_state_version: expectedStateVersion,
          user_confirmed: 1,
          queued_at_epoch_seconds: queuedAt,
          status: "queued",
          device_id: deviceId,
          command_json: commandJson,
          receipt_json: null,
          completed_at_epoch_seconds: null,
        });
      }
      return { success: true };
    }

    if (this.sql.startsWith("UPDATE commands")) {
      const [receiptJson, completedAt, userId, idempotencyKey] = this.args;
      const key = userId + "|" + idempotencyKey;
      const row = this.db.commands.get(key);
      if (row) {
        row.status = "completed";
        row.receipt_json = receiptJson;
        row.completed_at_epoch_seconds = completedAt;
      }
      return { success: true };
    }

    throw new Error("Unsupported fake D1 run SQL: " + this.sql);
  }

  async first() {
    if (this.sql.includes("FROM snapshots")) {
      return this.db.snapshots.get(this.args[0]) ?? null;
    }
    if (this.sql.includes("FROM commands")) {
      return this.db.commands.get(this.args[0] + "|" + this.args[1]) ?? null;
    }
    throw new Error("Unsupported fake D1 first SQL: " + this.sql);
  }

  async all() {
    if (!this.sql.includes("FROM commands")) {
      throw new Error("Unsupported fake D1 all SQL: " + this.sql);
    }
    const [userId, deviceId] = this.args;
    const rows = [...this.db.commands.values()]
      .filter((row) => {
        if (row.user_id !== userId || row.status !== "queued") return false;
        if (deviceId == null) return true;
        return row.device_id == null || row.device_id === deviceId;
      })
      .sort(
        (a, b) =>
          a.queued_at_epoch_seconds - b.queued_at_epoch_seconds ||
          a.command_id.localeCompare(b.command_id)
      );
    return { success: true, results: rows };
  }
}

export class FakeD1 {
  constructor() {
    this.snapshots = new Map();
    this.commands = new Map();
  }

  prepare(sql) {
    return new FakeD1Statement(this, sql);
  }
}
