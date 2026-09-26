import test from "node:test";
import assert from "node:assert/strict";
import { createScopedGatewayService, mcpErrorResult } from "../src/mcp-server.js";

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


test("insufficient scope emits ChatGPT MCP OAuth reauthorization challenge", () => {
  const result = mcpErrorResult(
    new Error("insufficient_scope"),
    "https://connector.example/.well-known/oauth-protected-resource"
  );

  assert.equal(result.isError, true);
  const challenges = result._meta?.["mcp/www_authenticate"];
  assert.equal(Array.isArray(challenges), true);
  assert.equal(challenges.length, 1);
  assert.match(challenges[0], /resource_metadata="https:\/\/connector\.example\/\.well-known\/oauth-protected-resource"/);
  assert.match(challenges[0], /error="insufficient_scope"/);
  assert.match(challenges[0], /error_description="[^"]+"/);
});

test("domain errors do not trigger OAuth linking UI", () => {
  const result = mcpErrorResult(
    new Error("stale_device_state"),
    "https://connector.example/.well-known/oauth-protected-resource"
  );

  assert.equal(result.isError, true);
  assert.equal(result._meta, undefined);
});
