import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  InMemoryGatewayStore,
  SqliteGatewayStore,
  createGatewayStoreFromEnv,
} from "../src/gateway-store.js";

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
    ...overrides,
  };
}

test("SQLite store survives process-style reopen with snapshot, queue and receipt", () => {
  const dir = mkdtempSync(join(tmpdir(), "arrival-gateway-"));
  const dbPath = join(dir, "gateway.db");
  try {
    const first = new SqliteGatewayStore({
      path: dbPath,
      nowEpochSeconds: () => 1000,
    });
    first.putSnapshot("user-1", snapshot(), "phone-a");
    const queued = first.enqueueCommand(
      "user-1",
      { type: "arm_arrival_alarm", idempotency_key: "command-durable-1" },
      7,
      "phone-a",
    );
    assert.equal(queued.status, "queued");
    first.close();

    const second = new SqliteGatewayStore({
      path: dbPath,
      nowEpochSeconds: () => 1010,
    });
    const restored = second.getSnapshot("user-1");
    assert.equal(restored.state_version, 7);
    assert.equal(restored.gateway_device_id, "phone-a");
    assert.equal(restored.gateway_received_at_epoch_seconds, 1000);
    assert.equal(second.pendingCommands("user-1", "phone-a").length, 1);
    assert.equal(second.pendingCommands("user-1", "phone-b").length, 0);

    second.submitReceipt(
      "user-1",
      {
        idempotency_key: "command-durable-1",
        status: "APPLIED",
        state_version_before: 7,
        state_version_after: 8,
        action_id: "action-1",
      },
      "phone-a",
    );
    second.close();

    const third = new SqliteGatewayStore({
      path: dbPath,
      nowEpochSeconds: () => 1020,
    });
    const result = third.commandByIdempotencyKey("user-1", "command-durable-1");
    assert.equal(result.status, "completed");
    assert.equal(result.receipt.status, "APPLIED");
    assert.equal(result.receipt.action_id, "action-1");
    assert.equal(third.pendingCommands("user-1", "phone-a").length, 0);
    third.close();
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("SQLite store preserves idempotent queue identity across reopen", () => {
  const dir = mkdtempSync(join(tmpdir(), "arrival-gateway-"));
  const dbPath = join(dir, "gateway.db");
  try {
    const first = new SqliteGatewayStore({ path: dbPath, nowEpochSeconds: () => 1000 });
    const command = {
      type: "cancel_arrival_alarm",
      idempotency_key: "command-durable-duplicate",
    };
    const initial = first.enqueueCommand("user-1", command, 7, "phone-a");
    first.close();

    const second = new SqliteGatewayStore({ path: dbPath, nowEpochSeconds: () => 2000 });
    const duplicate = second.enqueueCommand("user-1", command, 7, "phone-a");
    assert.equal(duplicate.command_id, initial.command_id);
    assert.equal(duplicate.queued_at_epoch_seconds, 1000);
    assert.equal(second.pendingCommands("user-1", "phone-a").length, 1);
    second.close();
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("production store factory fails closed without durable path", () => {
  assert.throws(
    () => createGatewayStoreFromEnv({ NODE_ENV: "production" }),
    /production_durable_store_not_configured/,
  );
});

test("development store factory keeps in-memory fallback", () => {
  const store = createGatewayStoreFromEnv({ NODE_ENV: "development" });
  assert.ok(store instanceof InMemoryGatewayStore);
  store.close();
});
