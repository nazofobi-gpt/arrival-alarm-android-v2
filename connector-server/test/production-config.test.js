import test from "node:test";
import assert from "node:assert/strict";
import { validateProductionEnvironment } from "../src/production-config.js";

function valid(overrides = {}) {
  return {
    NODE_ENV: "production",
    MCP_PUBLIC_BASE_URL: "https://connector.example",
    OAUTH_AUTHORIZATION_SERVER: "https://auth.example",
    OAUTH_ISSUER: "https://auth.example",
    OAUTH_AUDIENCE: "arrival-alarm",
    OAUTH_JWKS_URL: "https://auth.example/.well-known/jwks.json",
    GATEWAY_SQLITE_PATH: "/data/arrival-alarm.db",
    ...overrides,
  };
}

test("development mode does not require production secrets", () => {
  assert.deepEqual(validateProductionEnvironment({ NODE_ENV: "test" }), {
    production: false,
  });
});

test("production requires complete HTTPS OAuth and durable store configuration", () => {
  const result = validateProductionEnvironment(valid());

  assert.equal(result.production, true);
  assert.equal(result.publicBaseUrl, "https://connector.example");
  assert.equal(result.audience, "arrival-alarm");
  assert.equal(result.sqlitePath, "/data/arrival-alarm.db");
});

test("production rejects cleartext public or OAuth endpoints", () => {
  assert.throws(
    () =>
      validateProductionEnvironment(
        valid({ MCP_PUBLIC_BASE_URL: "http://connector.example" })
      ),
    /https_required/
  );
  assert.throws(
    () =>
      validateProductionEnvironment(
        valid({ OAUTH_JWKS_URL: "http://auth.example/jwks.json" })
      ),
    /https_required/
  );
});

test("device OAuth overrides must be supplied as one complete set", () => {
  assert.throws(
    () =>
      validateProductionEnvironment(
        valid({ DEVICE_OAUTH_ISSUER: "https://device-auth.example" })
      ),
    /incomplete_device_oauth_override/
  );

  assert.equal(
    validateProductionEnvironment(
      valid({
        DEVICE_OAUTH_ISSUER: "https://device-auth.example",
        DEVICE_OAUTH_AUDIENCE: "arrival-device",
        DEVICE_OAUTH_JWKS_URL: "https://device-auth.example/jwks.json",
      })
    ).production,
    true
  );
});
