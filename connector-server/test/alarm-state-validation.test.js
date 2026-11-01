import test from "node:test";
import assert from "node:assert/strict";
import { InMemoryGatewayStore } from "../src/gateway-store.js";
import { D1GatewayStore } from "../src/d1-gateway-store.js";
import { GatewayService } from "../src/gateway-service.js";
import { AsyncGatewayService } from "../src/async-gateway-service.js";
import { FakeD1 } from "./support/fake-d1.js";

const snapshot = (alarm_armed) => ({
  schema_version: 1,
  state_version: 7,
  source_updated_at_epoch_seconds: 995,
  stale_after_seconds: 120,
  journey_phase: "ARMED",
  alarm_armed,
});

const invalidAlarmStates = ["false", "true", 0, 1, [], {}];

test("in-memory gateway rejects malformed alarm state without replacing valid data", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  const gateway = new GatewayService(store, { nowEpochSeconds: () => 1000 });
  store.putSnapshot("user", snapshot(false));
  assert.equal(gateway.getAlarmState("user").alarm_armed, false);

  for (const alarmState of invalidAlarmStates) {
    assert.throws(() => store.putSnapshot("user", snapshot(alarmState)), /invalid_alarm_armed/);
    assert.equal(gateway.getAlarmState("user").alarm_armed, false);
  }

  store.putSnapshot("user", snapshot(true));
  assert.equal(gateway.getAlarmState("user").alarm_armed, true);
});

test("D1 gateway rejects malformed alarm state without overwriting the last snapshot", async () => {
  const store = new D1GatewayStore(new FakeD1(), { nowEpochSeconds: () => 1000 });
  const gateway = new AsyncGatewayService(store, { nowEpochSeconds: () => 1000 });
  await store.putSnapshot("user", snapshot(false));
  assert.equal((await gateway.getAlarmState("user")).alarm_armed, false);

  for (const alarmState of invalidAlarmStates) {
    await assert.rejects(store.putSnapshot("user", snapshot(alarmState)), /invalid_alarm_armed/);
    assert.equal((await gateway.getAlarmState("user")).alarm_armed, false);
  }

  await store.putSnapshot("user", snapshot(true));
  assert.equal((await gateway.getAlarmState("user")).alarm_armed, true);
});

test("optional absent alarm state remains compatible with both gateway stores", async () => {
  const withoutAlarm = snapshot(undefined);
  delete withoutAlarm.alarm_armed;
  const memory = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  memory.putSnapshot("user", withoutAlarm);
  assert.equal(new GatewayService(memory, { nowEpochSeconds: () => 1000 }).getAlarmState("user").alarm_armed, false);

  const d1 = new D1GatewayStore(new FakeD1(), { nowEpochSeconds: () => 1000 });
  await d1.putSnapshot("user", withoutAlarm);
  assert.equal((await new AsyncGatewayService(d1, { nowEpochSeconds: () => 1000 }).getAlarmState("user")).alarm_armed, false);
});
