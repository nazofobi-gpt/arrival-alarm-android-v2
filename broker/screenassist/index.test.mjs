import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import test from "node:test";
import { createScreenAssistBroker } from "./index.mjs";

function sha256(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function request(token = "device-test-token", method = "POST") {
  return new Request("https://broker.example.test/", {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

function env(overrides = {}) {
  return {
    OPENAI_API_KEY: "server-key-placeholder",
    SCREEN_ASSIST_CALLER_TOKEN_SHA256: sha256("device-test-token"),
    OPENAI_REALTIME_MODEL: "gpt-realtime-2.1",
    OPENAI_SAFETY_IDENTIFIER: "screen-assist-test-user",
    ...overrides,
  };
}

test("fails closed when broker secrets are not configured", async () => {
  const broker = createScreenAssistBroker({ env: {} });
  const response = await broker.fetch(request());
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: "broker_not_configured" });
});

test("rejects missing or invalid caller token before contacting OpenAI", async () => {
  let called = false;
  const broker = createScreenAssistBroker({
    env: env(),
    fetchImpl: async () => {
      called = true;
      throw new Error("must not be called");
    },
  });

  assert.equal((await broker.fetch(request(null))).status, 401);
  assert.equal((await broker.fetch(request("wrong-token"))).status, 401);
  assert.equal(called, false);
});

test("mints only the short-lived Realtime client secret shape", async () => {
  let upstreamRequest = null;
  const broker = createScreenAssistBroker({
    env: env(),
    nowEpochSeconds: () => 1_000,
    fetchImpl: async (url, init) => {
      upstreamRequest = { url, init };
      return new Response(
        JSON.stringify({
          value: "ek_test_ephemeral",
          expires_at: 1_120,
          ignored: "not-forwarded",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    },
  });

  const response = await broker.fetch(request());
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.deepEqual(await response.json(), {
    value: "ek_test_ephemeral",
    expires_at: 1_120,
  });

  assert.equal(
    upstreamRequest.url,
    "https://api.openai.com/v1/realtime/client_secrets",
  );
  assert.equal(
    upstreamRequest.init.headers.Authorization,
    "Bearer server-key-placeholder",
  );
  assert.equal(
    upstreamRequest.init.headers["OpenAI-Safety-Identifier"],
    "screen-assist-test-user",
  );

  const body = JSON.parse(upstreamRequest.init.body);
  assert.equal(body.session.type, "realtime");
  assert.equal(body.session.model, "gpt-realtime-2.1");
  assert.match(body.session.instructions, /Never claim that you clicked/);
});

test("does not proxy upstream error bodies or long-lived credentials", async () => {
  const broker = createScreenAssistBroker({
    env: env(),
    fetchImpl: async () =>
      new Response(
        JSON.stringify({
          error: { message: "sensitive-upstream-detail" },
          api_key: "must-not-leak",
        }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      ),
  });

  const response = await broker.fetch(request());
  assert.equal(response.status, 502);
  const body = JSON.stringify(await response.json());
  assert.doesNotMatch(body, /sensitive-upstream-detail|must-not-leak/);
});

test("rejects malformed or near-expiry client secrets", async () => {
  for (const payload of [
    { value: "", expires_at: 2_000 },
    { value: "ek_test", expires_at: 1_020 },
    { value: "ek_test", expires_at: 2_000, api_key: "bad" },
  ]) {
    const broker = createScreenAssistBroker({
      env: env(),
      nowEpochSeconds: () => 1_000,
      fetchImpl: async () =>
        new Response(JSON.stringify(payload), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
    });

    const response = await broker.fetch(request());
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), {
      error: "invalid_openai_client_secret",
    });
  }
});

test("allows POST only", async () => {
  const broker = createScreenAssistBroker({ env: env() });
  const response = await broker.fetch(request("device-test-token", "GET"));
  assert.equal(response.status, 405);
});
