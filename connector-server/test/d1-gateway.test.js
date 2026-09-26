import test from "node:test";
import assert from "node:assert/strict";
import { AsyncGatewayService } from "../src/async-gateway-service.js";
import { D1GatewayStore } from "../src/d1-gateway-store.js";

class FakeD1Statement {
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

class FakeD1 {
  constructor() {
    this.snapshots = new Map();
    this.commands = new Map();
  }

  prepare(sql) {
    return new FakeD1Statement(this, sql);
  }
}

function snapshot(overrides = {}) {
  return {
    schema_version: 1,
    state_version: 7,
    captured_at_epoch_seconds: 1000,
    source_updated_at_epoch_seconds: 995,
    stale_after_seconds: 120,
    source: "arrival-alarm-domain",
    journey_phase: "ARMED",
    alarm_armed: true,
    active_trip: {
      trip_id: "trip-1",
      route_id: "route-1",
      line: "RE 9",
      direction: "Bremen Hbf",
      previous_stop: { id: "a", name: "Diepholz" },
      current_stop: { id: "b", name: "Barnstorf" },
      next_stop: { id: "c", name: "Twistringen" },
      timing_basis: "REALTIME",
      progress_updated_at_epoch_seconds: 990,
    },
    ...overrides,
  };
}

test("D1 persists device-bound snapshot and derives the same read state", async () => {
  const db = new FakeD1();
  const store = new D1GatewayStore(db, { nowEpochSeconds: () => 1000 });
  const service = new AsyncGatewayService(store, { nowEpochSeconds: () => 1000 });

  await store.putSnapshot("user-1", snapshot(), "phone-1");

  const journey = await service.getCurrentJourney("user-1");
  const progress = await service.getTripProgress("user-1");
  const alarm = await service.getAlarmState("user-1");

  assert.equal(journey.active_trip.line, "RE 9");
  assert.equal(progress.next_stop.name, "Twistringen");
  assert.equal(alarm.alarm_armed, true);
  assert.equal(journey.stale, false);
  assert.equal((await store.getSnapshot("user-1")).gateway_device_id, "phone-1");
});

test("D1 duplicate idempotency key queues exactly one command pinned to the device", async () => {
  const db = new FakeD1();
  const store = new D1GatewayStore(db, { nowEpochSeconds: () => 1000 });
  const service = new AsyncGatewayService(store, { nowEpochSeconds: () => 1000 });
  await store.putSnapshot("user-1", snapshot(), "phone-a");

  const command = {
    type: "arm_arrival_alarm",
    idempotency_key: "edge-command-1",
  };
  const first = await service.queueWrite("user-1", command, 7);
  const second = await service.queueWrite("user-1", command, 7);

  assert.equal(first.command_id, second.command_id);
  assert.equal((await store.pendingCommands("user-1", "phone-a")).length, 1);
  assert.equal((await store.pendingCommands("user-1", "phone-b")).length, 0);
});

test("D1 receipt rejects the wrong device and closes the correct command", async () => {
  const db = new FakeD1();
  const store = new D1GatewayStore(db, { nowEpochSeconds: () => 1000 });
  const service = new AsyncGatewayService(store, { nowEpochSeconds: () => 1000 });
  await store.putSnapshot("user-1", snapshot(), "phone-a");
  await service.queueWrite(
    "user-1",
    { type: "cancel_arrival_alarm", idempotency_key: "edge-receipt-1" },
    7
  );

  await assert.rejects(
    () =>
      store.submitReceipt(
        "user-1",
        {
          idempotency_key: "edge-receipt-1",
          status: "APPLIED",
          state_version_before: 7,
          state_version_after: 8,
        },
        "phone-b"
      ),
    /device_mismatch/
  );

  await store.submitReceipt(
    "user-1",
    {
      idempotency_key: "edge-receipt-1",
      status: "APPLIED",
      state_version_before: 7,
      state_version_after: 8,
    },
    "phone-a"
  );

  const result = await service.getCommandResult("user-1", "edge-receipt-1");
  assert.equal(result.status, "completed");
  assert.equal(result.receipt.status, "APPLIED");
  assert.equal((await store.pendingCommands("user-1", "phone-a")).length, 0);
});

test("async gateway rejects stale state before D1 write", async () => {
  const db = new FakeD1();
  const store = new D1GatewayStore(db, { nowEpochSeconds: () => 1000 });
  const service = new AsyncGatewayService(store, { nowEpochSeconds: () => 5000 });
  await store.putSnapshot("user-1", snapshot(), "phone-a");

  await assert.rejects(
    () =>
      service.queueWrite(
        "user-1",
        { type: "arm_arrival_alarm", idempotency_key: "edge-stale-1" },
        7
      ),
    /stale_device_state/
  );
  assert.equal((await store.pendingCommands("user-1", "phone-a")).length, 0);
});

export { FakeD1 };
