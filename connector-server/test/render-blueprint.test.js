import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import YAML from "yaml";

function blueprint() {
  const url = new URL("../../render.yaml", import.meta.url);
  return YAML.parse(fs.readFileSync(url, "utf8"));
}

function envByKey(service) {
  return new Map(
    (service.envVars ?? [])
      .filter((entry) => entry?.key)
      .map((entry) => [entry.key, entry])
  );
}

test("Render Blueprint provisions the connector with durable Frankfurt storage", () => {
  const config = blueprint();
  assert.deepEqual(config.previews, { generation: "off" });
  assert.equal(config.services?.length, 1);

  const service = config.services[0];
  assert.equal(service.type, "web");
  assert.equal(service.name, "arrival-alarm-connector");
  assert.equal(service.runtime, "node");
  assert.equal(service.branch, "main");
  assert.equal(service.rootDir, "connector-server");
  assert.equal(service.region, "frankfurt");
  assert.equal(service.plan, "0.5c-512mb");
  assert.equal(service.numInstances, 1);
  assert.equal(service.autoDeployTrigger, "checksPass");
  assert.equal(service.healthCheckPath, "/");

  assert.deepEqual(service.disk, {
    name: "connector-data",
    mountPath: "/var/data",
    sizeGB: 1,
  });
});

test("Render Blueprint keeps OAuth values outside git and wires durable gateway state", () => {
  const service = blueprint().services[0];
  const env = envByKey(service);

  assert.equal(env.get("NODE_ENV")?.value, "production");
  assert.equal(
    env.get("GATEWAY_SQLITE_PATH")?.value,
    "/var/data/arrival-alarm.db"
  );
  assert.deepEqual(env.get("MCP_PUBLIC_BASE_URL")?.fromService, {
    type: "web",
    name: "arrival-alarm-connector",
    envVarKey: "RENDER_EXTERNAL_URL",
  });

  for (const key of [
    "OAUTH_AUTHORIZATION_SERVER",
    "OAUTH_ISSUER",
    "OAUTH_AUDIENCE",
    "OAUTH_JWKS_URL",
    "DEVICE_OAUTH_AUTHORIZATION_ENDPOINT",
    "DEVICE_OAUTH_TOKEN_ENDPOINT",
    "DEVICE_OAUTH_REVOCATION_ENDPOINT",
    "DEVICE_OAUTH_CLIENT_ID",
  ]) {
    assert.equal(env.get(key)?.sync, false, key);
    assert.equal("value" in env.get(key), false, key + " must not be committed");
  }

  assert.equal(
    env.get("DEVICE_OAUTH_SCOPES")?.value,
    "arrival.device offline_access"
  );
  assert.equal(env.get("DEVICE_OAUTH_DEVICE_ID_PARAMETER")?.value, "device_id");
});

test("Render Blueprint does not accidentally configure ephemeral SQLite", () => {
  const service = blueprint().services[0];
  const env = envByKey(service);
  const sqlitePath = env.get("GATEWAY_SQLITE_PATH")?.value;

  assert.ok(sqlitePath.startsWith(service.disk.mountPath + "/"));
  assert.notEqual(service.plan, "free");
});
