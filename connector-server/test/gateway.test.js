import test from "node:test";
import assert from "node:assert/strict";
import { InMemoryGatewayStore } from "../src/gateway-store.js";
import { GatewayService } from "../src/gateway-service.js";

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
    },
    ...overrides,
  };
}

test("read tools can derive current journey and stop progress from one snapshot", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  const service = new GatewayService(store, { nowEpochSeconds: () => 1000 });
  store.putSnapshot("user-1", snapshot());

  const journey = service.getCurrentJourney("user-1");
  const progress = service.getTripProgress("user-1");
  const alarm = service.getAlarmState("user-1");

  assert.equal(journey.active_trip.line, "RE 9");
  assert.equal(progress.previous_stop.name, "Diepholz");
  assert.equal(progress.current_stop.name, "Barnstorf");
  assert.equal(progress.next_stop.name, "Twistringen");
  assert.equal(alarm.alarm_armed, true);
  assert.equal(journey.stale, false);
});

test("write queue rejects stale device heartbeat", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  const service = new GatewayService(store, { nowEpochSeconds: () => 5000 });
  store.putSnapshot("user-1", snapshot({ source_updated_at_epoch_seconds: 1000 }));

  assert.throws(
    () =>
      service.queueWrite(
        "user-1",
        { type: "arm_arrival_alarm", idempotency_key: "command-123" },
        7
      ),
    /stale_device_state/
  );
  assert.equal(store.pendingCommands("user-1").length, 0);
});

test("recent device heartbeat keeps unchanged domain state writable", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 5000 });
  const service = new GatewayService(store, { nowEpochSeconds: () => 5000 });
  store.putSnapshot("user-1", snapshot({ source_updated_at_epoch_seconds: 1000 }));

  const queued = service.queueWrite(
    "user-1",
    { type: "arm_arrival_alarm", idempotency_key: "command-heartbeat" },
    7
  );

  assert.equal(queued.status, "queued");
  assert.equal(store.pendingCommands("user-1").length, 1);
});

test("write queue rejects optimistic state-version mismatch", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  const service = new GatewayService(store, { nowEpochSeconds: () => 1000 });
  store.putSnapshot("user-1", snapshot());

  assert.throws(
    () =>
      service.queueWrite(
        "user-1",
        { type: "set_destination", idempotency_key: "command-456" },
        6
      ),
    /state_version_mismatch/
  );
  assert.equal(store.pendingCommands("user-1").length, 0);
});

test("same idempotency key queues exactly one device side effect", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  const service = new GatewayService(store, { nowEpochSeconds: () => 1000 });
  store.putSnapshot("user-1", snapshot());

  const command = {
    type: "cancel_arrival_alarm",
    idempotency_key: "command-duplicate",
  };
  const first = service.queueWrite("user-1", command, 7);
  const second = service.queueWrite("user-1", command, 7);

  assert.equal(first.command_id, second.command_id);
  assert.equal(store.pendingCommands("user-1").length, 1);
});

test("device receipt closes queued command and is readable by connector", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  const service = new GatewayService(store, { nowEpochSeconds: () => 1000 });
  store.putSnapshot("user-1", snapshot());

  service.queueWrite(
    "user-1",
    { type: "arm_arrival_alarm", idempotency_key: "command-receipt" },
    7
  );
  store.submitReceipt("user-1", {
    idempotency_key: "command-receipt",
    status: "APPLIED",
    state_version_before: 7,
    state_version_after: 8,
    action_id: "action-1",
  });

  const result = service.getCommandResult("user-1", "command-receipt");
  assert.equal(result.status, "completed");
  assert.equal(result.receipt.status, "APPLIED");
  assert.equal(store.pendingCommands("user-1").length, 0);
});

test("snapshot validation rejects unsupported wire versions", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  assert.throws(
    () => store.putSnapshot("user-1", snapshot({ schema_version: 2 })),
    /unsupported_schema_version/
  );
});
