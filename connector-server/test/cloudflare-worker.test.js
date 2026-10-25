import test from "node:test";
import assert from "node:assert/strict";
import { createCloudflareWorker } from "../src/cloudflare-worker.js";
import { FakeD1 } from "./support/fake-d1.js";

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
  assert.deepEqual(body.authorization_servers, ["https://tenant.example/"]);
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
  assert.equal(body.audience, "https://arrival-alarm-api");
  assert.ok(body.scopes.includes("arrival.device"));
});


test("Worker rejects OAuth URLs containing credentials or fragments in public metadata", async () => {
  const worker = createCloudflareWorker();
  for (const [key, errorCode] of [
    ["MCP_PUBLIC_BASE_URL", "worker_invalid_public_base_url"],
    ["OAUTH_AUTHORIZATION_SERVER", "worker_invalid_authorization_server"],
  ]) {
    for (const invalidUrl of [
      "https://user:secret@tenant.example/oauth",
      "https://tenant.example/oauth#access-token",
    ]) {
      const response = await worker.fetch(
        new Request("https://free.example/.well-known/oauth-protected-resource"),
        { ...env(), [key]: invalidUrl }
      );
      assert.equal(response.status, 503, key);
      assert.deepEqual(await response.json(), { error: errorCode });
    }
  }
});

test("Worker rejects unsafe OAuth issuer and JWKS URLs before verifying bearer tokens", async () => {
  const worker = createCloudflareWorker();
  for (const [key, expectedError, device] of [
    ["OAUTH_ISSUER", "worker_invalid_oauth_issuer", false],
    ["OAUTH_JWKS_URL", "worker_invalid_oauth_jwks_url", false],
    ["DEVICE_OAUTH_ISSUER", "worker_invalid_device_oauth_issuer", true],
    ["DEVICE_OAUTH_JWKS_URL", "worker_invalid_device_oauth_jwks_url", true],
  ]) {
    for (const invalidUrl of [
      "https://user:secret@tenant.example/keys",
      "https://tenant.example/keys#fragment",
    ]) {
      const endpoint = device ? "/device/state" : "/mcp";
      const response = await worker.fetch(
        new Request("https://free.example" + endpoint, {
          method: "POST",
          headers: {
            authorization: "Bearer invalid-token",
            "content-type": "application/json",
          },
          body: "{}",
        }),
        { ...env(), [key]: invalidUrl }
      );
      assert.equal(response.status, 503, key);
      assert.deepEqual(await response.json(), { error: expectedError });
    }
  }
});

test("Worker preserves legitimate HTTPS query parameters in authorization server metadata", async () => {
  const worker = createCloudflareWorker();
  const response = await worker.fetch(
    new Request("https://free.example/.well-known/oauth-protected-resource"),
    {
      ...env(),
      OAUTH_AUTHORIZATION_SERVER: "https://tenant.example/authorize?prompt=consent",
    }
  );
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.authorization_servers, [
    "https://tenant.example/authorize?prompt=consent",
  ]);
});
