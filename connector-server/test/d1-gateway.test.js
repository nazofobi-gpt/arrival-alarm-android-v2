import test from "node:test";
import assert from "node:assert/strict";
import { AsyncGatewayService } from "../src/async-gateway-service.js";
import { D1GatewayStore } from "../src/d1-gateway-store.js";

import { FakeD1 } from "./support/fake-d1.js";

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
