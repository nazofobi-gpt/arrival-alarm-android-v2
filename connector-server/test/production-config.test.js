import test from "node:test";
import assert from "node:assert/strict";
import { deviceOAuthMetadataFromEnv, validateProductionEnvironment } from "../src/production-config.js";

function valid(overrides = {}) {
  return {
    NODE_ENV: "production",
    MCP_PUBLIC_BASE_URL: "https://connector.example",
    OAUTH_AUTHORIZATION_SERVER: "https://auth.example",
    OAUTH_ISSUER: "https://auth.example",
    OAUTH_AUDIENCE: "arrival-alarm",
    OAUTH_JWKS_URL: "https://auth.example/.well-known/jwks.json",
    GATEWAY_SQLITE_PATH: "/data/arrival-alarm.db",
    DEVICE_OAUTH_AUTHORIZATION_ENDPOINT: "https://auth.example/authorize",
    DEVICE_OAUTH_TOKEN_ENDPOINT: "https://auth.example/token",
    DEVICE_OAUTH_REVOCATION_ENDPOINT: "https://auth.example/revoke",
    DEVICE_OAUTH_CLIENT_ID: "arrival-alarm-android",
    DEVICE_OAUTH_SCOPES: "arrival.device offline_access",
    DEVICE_OAUTH_RESOURCE: "https://connector.example/device",
    DEVICE_OAUTH_DEVICE_ID_PARAMETER: "device_id",
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


test("device OAuth metadata is a public PKCE client contract with fixed app redirect", () => {
  const metadata = deviceOAuthMetadataFromEnv(valid());

  assert.equal(metadata.authorization_endpoint, "https://auth.example/authorize");
  assert.equal(metadata.token_endpoint, "https://auth.example/token");
  assert.equal(metadata.revocation_endpoint, "https://auth.example/revoke");
  assert.equal(metadata.client_id, "arrival-alarm-android");
  assert.deepEqual(metadata.scopes, ["arrival.device", "offline_access"]);
  assert.equal(metadata.redirect_uri, "com.nazofobi.arrivalalarm://oauth/callback");
  assert.equal(metadata.resource, "https://connector.example/device");
  assert.equal(metadata.device_id_parameter, "device_id");
});

test("production rejects device OAuth without required device scope or HTTPS endpoints", () => {
  assert.throws(
    () =>
      validateProductionEnvironment(
        valid({ DEVICE_OAUTH_SCOPES: "offline_access" })
      ),
    /device_oauth_scope_missing/
  );

  assert.throws(
    () =>
      validateProductionEnvironment(
        valid({ DEVICE_OAUTH_TOKEN_ENDPOINT: "http://auth.example/token" })
      ),
    /https_required/
  );
});
