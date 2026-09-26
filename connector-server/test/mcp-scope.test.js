import test from "node:test";
import assert from "node:assert/strict";
import { createScopedGatewayService } from "../src/mcp-server.js";

function fakeService() {
  return {
    getCurrentJourney: (userId) => ({ userId, kind: "journey" }),
    getTripProgress: (userId) => ({ userId, kind: "progress" }),
    getAlarmState: (userId) => ({ userId, kind: "alarm" }),
    getCommandResult: (userId, key) => ({ userId, key }),
    queueWrite: (userId, command, version) => ({ userId, command, version }),
  };
}

test("read-only scope can read but cannot queue writes", () => {
  const service = createScopedGatewayService(fakeService(), ["arrival.read"]);

  assert.equal(service.getCurrentJourney("user-1").kind, "journey");
  assert.throws(
    () => service.queueWrite("user-1", { type: "set_origin" }, 7),
    /insufficient_scope/
  );
});

test("write-only scope can queue writes but cannot read journey state", () => {
  const service = createScopedGatewayService(fakeService(), ["arrival.write"]);

  assert.equal(
    service.queueWrite("user-1", { type: "arm_arrival_alarm" }, 7).version,
    7
  );
  assert.throws(
    () => service.getCurrentJourney("user-1"),
    /insufficient_scope/
  );
  assert.throws(() => service.assertRead(), /insufficient_scope/);
});

test("combined scope supports read and write connector operations", () => {
  const service = createScopedGatewayService(
    fakeService(),
    new Set(["arrival.read", "arrival.write"])
  );

  assert.equal(service.getTripProgress("user-1").kind, "progress");
  assert.equal(
    service.queueWrite("user-1", { type: "cancel_arrival_alarm" }, 8).version,
    8
  );
});
