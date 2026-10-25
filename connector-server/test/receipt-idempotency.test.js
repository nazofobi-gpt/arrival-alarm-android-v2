import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { InMemoryGatewayStore, SqliteGatewayStore } from "../src/gateway-store.js";
import { D1GatewayStore } from "../src/d1-gateway-store.js";
import { FakeD1 } from "./support/fake-d1.js";

function command(key) {
  return { type: "cancel_arrival_alarm", idempotency_key: key };
}

function receipt(key, status, after) {
  return {
    idempotency_key: key,
    status,
    state_version_before: 7,
    state_version_after: after,
  };
}

function verifyFirstReceipt(store, key) {
  store.enqueueCommand("u", command(key), 7, "device-a");
  const original = store.submitReceipt("u", receipt(key, "APPLIED", 8), "device-a");
  const replay = store.submitReceipt("u", receipt(key, "REJECTED", 77), "device-a");
  assert.deepEqual(replay, original, "retries must return the immutable first receipt");
  assert.deepEqual(store.commandByIdempotencyKey("u", key), original);
  assert.equal(store.pendingCommands("u", "device-a").length, 0);
  assert.throws(
    () => store.submitReceipt("u", receipt(key, "REJECTED", 99), "device-b"),
    /device_mismatch/,
    "a different device cannot even read a completed receipt"
  );
}

test("in-memory device receipt is first-write-wins", () => {
  let clock = 1000;
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => clock++ });
  verifyFirstReceipt(store, "receipt-first-memory");
});

test("SQLite device receipt is first-write-wins after durable reload", () => {
  const folder = mkdtempSync(join(tmpdir(), "arrival-receipt-"));
  const path = join(folder, "gateway.sqlite");
  let store;
  try {
    let clock = 1000;
    store = new SqliteGatewayStore({ path, nowEpochSeconds: () => clock++ });
    verifyFirstReceipt(store, "receipt-first-sqlite");
    const first = store.commandByIdempotencyKey("u", "receipt-first-sqlite");
    store.close();
    store = null;
    const restored = new SqliteGatewayStore({ path, nowEpochSeconds: () => clock++ });
    store = restored;
    assert.deepEqual(restored.commandByIdempotencyKey("u", "receipt-first-sqlite"), first);
  } finally {
    store?.close();
    rmSync(folder, { recursive: true, force: true });
  }
});

test("D1 receipt retries and simultaneous stale reads cannot overwrite the first completion", async () => {
  let clock = 1000;
  const store = new D1GatewayStore(new FakeD1(), { nowEpochSeconds: () => clock++ });
  const key = "receipt-first-d1";
  await store.enqueueCommand("u", command(key), 7, "device-a");
  const a = receipt(key, "APPLIED", 8);
  const b = receipt(key, "REJECTED", 77);
  const [left, right] = await Promise.all([
    store.submitReceipt("u", a, "device-a"),
    store.submitReceipt("u", b, "device-a"),
  ]);
  const persisted = await store.commandByIdempotencyKey("u", key);
  assert.equal(persisted.status, "completed");
  assert.deepEqual(left, persisted);
  assert.deepEqual(right, persisted);
  const retry = await store.submitReceipt("u", b, "device-a");
  assert.deepEqual(retry, persisted);
  assert.equal((await store.pendingCommands("u", "device-a")).length, 0);
  await assert.rejects(
    () => store.submitReceipt("u", b, "device-b"),
    /device_mismatch/
  );
});
