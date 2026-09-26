import test from "node:test";
import assert from "node:assert/strict";
import { createCloudflareWorker } from "../src/cloudflare-worker.js";
import { FakeD1 } from "./d1-gateway.test.js";

function env() {
  return {
    DB: new FakeD1(),
    OAUTH_AUTHORIZATION_SERVER: "https://tenant.example/",
    OAUTH_ISSUER: "https://tenant.example/",
    OAUTH_AUDIENCE: "https://arrival-alarm-api",
    OAUTH_JWKS_URL: "https://tenant.example/.well-known/jwks.json",
    DEVICE_OAUTH_ISSUER: "https://tenant.example/",
    DEVICE_OAUTH_AUDIENCE: "https://arrival-alarm-api",
    DEVICE_OAUTH_JWKS_URL: "https://tenant.example/.well-known/jwks.json",
    DEVICE_OAUTH_AUTHORIZATION_ENDPOINT: "https://tenant.example/authorize",
    DEVICE_OAUTH_TOKEN_ENDPOINT: "https://tenant.example/oauth/token",
    DEVICE_OAUTH_REVOCATION_ENDPOINT: "https://tenant.example/oauth/revoke",
    DEVICE_OAUTH_CLIENT_ID: "arrival-android",
    DEVICE_OAUTH_SCOPES: "arrival.device offline_access",
    DEVICE_OAUTH_RESOURCE: "https://arrival-alarm-api",
    DEVICE_OAUTH_DEVICE_ID_PARAMETER: "ext-device_id",
    DEVICE_OAUTH_DEVICE_ID_CLAIM: "https://arrival-alarm.app/device_id",
  };
}

test("free Worker health identifies Cloudflare+D1 runtime", async () => {
  const worker = createCloudflareWorker();
  const response = await worker.fetch(new Request("https://free.example/"), env());
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body, {
    service: "arrival-alarm-connector",
    status: "ok",
    runtime: "cloudflare-workers-free",
    store: "d1",
    auth_mode: "oauth-jwt",
  });
});

test("free Worker publishes exact protected-resource metadata from request origin", async () => {
  const worker = createCloudflareWorker();
  const response = await worker.fetch(
    new Request("https://free.example/.well-known/oauth-protected-resource"),
    env()
  );
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.resource, "https://free.example/mcp");
  assert.deepEqual(body.authorization_servers, ["https://tenant.example"]);
  assert.deepEqual(body.scopes_supported, ["arrival.read", "arrival.write"]);
});

test("free Worker publishes Android Auth0-compatible public client metadata", async () => {
  const worker = createCloudflareWorker();
  const response = await worker.fetch(
    new Request("https://free.example/.well-known/arrival-alarm-device-oauth"),
    env()
  );
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.authorization_endpoint, "https://tenant.example/authorize");
  assert.equal(body.token_endpoint, "https://tenant.example/oauth/token");
  assert.equal(body.revocation_endpoint, "https://tenant.example/oauth/revoke");
  assert.equal(body.client_id, "arrival-android");
  assert.equal(body.redirect_uri, "com.nazofobi.arrivalalarm://oauth/callback");
  assert.equal(body.device_id_parameter, "ext-device_id");
  assert.ok(body.scopes.includes("arrival.device"));
});
