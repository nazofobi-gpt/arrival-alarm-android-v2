import test from "node:test";
import assert from "node:assert/strict";
import { InMemoryGatewayStore } from "../src/gateway-store.js";
import { D1GatewayStore } from "../src/d1-gateway-store.js";
import { GatewayService } from "../src/gateway-service.js";
import { AsyncGatewayService } from "../src/async-gateway-service.js";
import { FakeD1 } from "./support/fake-d1.js";

const gpsSnapshot = {
  schema_version: 1,
  state_version: 7,
  captured_at_epoch_seconds: 1000,
  source_updated_at_epoch_seconds: 1000,
  stale_after_seconds: 120,
  source: "arrival-alarm-domain",
  journey_phase: "ARMED",
  alarm_armed: true,
  active_trip: null,
  origin: { name: "Lohne", latitude: 52.66, longitude: 8.23 },
  destination: { name: "Bremen Hbf", latitude: 53.08, longitude: 8.81 },
};

test("in-memory connector exposes GPS-only destination without inventing a route", () => {
  const store = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  store.putSnapshot("user-1", gpsSnapshot, "phone-1");
  const service = new GatewayService(store, { nowEpochSeconds: () => 1000 });
  const journey = service.getCurrentJourney("user-1");
  assert.equal(journey.stale, false);
  assert.equal(journey.journey_phase, "ARMED");
  assert.equal(journey.active_trip, null);
  assert.deepEqual(journey.origin, gpsSnapshot.origin);
  assert.deepEqual(journey.destination, gpsSnapshot.destination);
});

test("D1 connector preserves GPS-only origin and destination across persisted state reads", async () => {
  const store = new D1GatewayStore(new FakeD1(), { nowEpochSeconds: () => 1000 });
  await store.putSnapshot("user-1", gpsSnapshot, "phone-1");
  const service = new AsyncGatewayService(store, { nowEpochSeconds: () => 1000 });
  const journey = await service.getCurrentJourney("user-1");
  assert.equal(journey.active_trip, null);
  assert.equal(journey.stale, false);
  assert.deepEqual(journey.origin, gpsSnapshot.origin);
  assert.deepEqual(journey.destination, gpsSnapshot.destination);
});

test("both gateways expose legacy selected-trip endpoints but never invent missing targets", async () => {
  const memory = new InMemoryGatewayStore({ nowEpochSeconds: () => 1000 });
  const d1 = new D1GatewayStore(new FakeD1(), { nowEpochSeconds: () => 1000 });
  const legacy = {
    ...gpsSnapshot,
    origin: undefined,
    destination: undefined,
    active_trip: {
      trip_id: "trip-1",
      origin: gpsSnapshot.origin,
      destination: gpsSnapshot.destination,
    },
  };
  memory.putSnapshot("user-1", legacy);
  await d1.putSnapshot("user-1", legacy);
  const sync = new GatewayService(memory, { nowEpochSeconds: () => 1000 });
  const asyncService = new AsyncGatewayService(d1, { nowEpochSeconds: () => 1000 });
  for (const journey of [sync.getCurrentJourney("user-1"), await asyncService.getCurrentJourney("user-1")]) {
    assert.equal(journey.active_trip.trip_id, "trip-1");
    assert.equal(journey.destination.name, "Bremen Hbf");
    assert.equal(journey.origin.name, "Lohne");
  }

  const missing = { ...gpsSnapshot, origin: undefined, destination: undefined };
  memory.putSnapshot("user-1", missing);
  await d1.putSnapshot("user-1", missing);
  assert.equal(sync.getCurrentJourney("user-1").destination, null);
  assert.equal((await asyncService.getCurrentJourney("user-1")).destination, null);
});
