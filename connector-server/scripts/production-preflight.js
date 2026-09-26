import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const EXPECTED_READ_TOOLS = new Set([
  "get_profile",
  "get_current_journey",
  "get_trip_progress",
  "get_alarm_state",
  "get_command_result",
]);

const EXPECTED_WRITE_TOOLS = new Set([
  "set_origin",
  "set_destination",
  "select_journey",
  "set_boarding_stop",
  "arm_arrival_alarm",
  "cancel_arrival_alarm",
]);

function requiredBaseUrl() {
  const raw = (process.env.CONNECTOR_BASE_URL || process.argv[2] || "").trim();
  assert.ok(raw, "CONNECTOR_BASE_URL (or first CLI argument) is required");
  const url = new URL(raw);
  assert.equal(url.protocol, "https:", "Production connector must use HTTPS");
  assert.ok(url.hostname);
  url.pathname = url.pathname.replace(/\/$/, "");
  url.search = "";
  url.hash = "";
  return url.toString().replace(/\/$/, "");
}

async function json(url, { token, expectedStatus = 200 } = {}) {
  const headers = { accept: "application/json" };
  if (token) headers.authorization = "Bearer " + token;

  const response = await fetch(url, {
    headers,
    redirect: "error",
    signal: AbortSignal.timeout(10_000),
  });
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    throw new Error("Non-JSON response from " + url + " (" + response.status + ")");
  }
  assert.equal(
    response.status,
    expectedStatus,
    "Unexpected HTTP status from " + url + ": " + response.status
  );
  return body;
}

function assertHttps(value, label) {
  const url = new URL(value);
  assert.equal(url.protocol, "https:", label + " must use HTTPS");
  assert.ok(url.hostname, label + " must have a hostname");
}

async function fetchAuthorizationMetadata(issuer) {
  const normalized = issuer.replace(/\/$/, "");
  const candidates = [
    normalized + "/.well-known/openid-configuration",
    normalized + "/.well-known/oauth-authorization-server",
  ];

  const errors = [];
  for (const url of candidates) {
    try {
      const response = await fetch(url, {
        headers: { accept: "application/json" },
        redirect: "error",
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) {
        errors.push(url + " -> " + response.status);
        continue;
      }
      const body = await response.json();
      return { url, body };
    } catch (error) {
      errors.push(url + " -> " + (error?.message || "request_failed"));
    }
  }
  throw new Error("OAuth discovery failed: " + errors.join("; "));
}

function validateToolContract(tools) {
  const byName = new Map(tools.map((tool) => [tool.name, tool]));
  const expected = new Set([...EXPECTED_READ_TOOLS, ...EXPECTED_WRITE_TOOLS]);
  assert.deepEqual(new Set(byName.keys()), expected, "Unexpected MCP tool surface");

  for (const name of EXPECTED_READ_TOOLS) {
    const tool = byName.get(name);
    assert.equal(tool.annotations?.readOnlyHint, true, name);
    assert.equal(tool.annotations?.destructiveHint, false, name);
    assert.equal(tool.annotations?.openWorldHint, false, name);
    assert.deepEqual(tool.securitySchemes, [
      { type: "oauth2", scopes: ["arrival.read"] },
    ]);
  }

  for (const name of EXPECTED_WRITE_TOOLS) {
    const tool = byName.get(name);
    assert.equal(tool.annotations?.readOnlyHint, false, name);
    assert.equal(tool.annotations?.destructiveHint, false, name);
    assert.equal(tool.annotations?.openWorldHint, false, name);
    assert.deepEqual(tool.securitySchemes, [
      { type: "oauth2", scopes: ["arrival.write"] },
    ]);
  }
}

async function probeMcp(baseUrl, token) {
  const transport = new StreamableHTTPClientTransport(
    new URL(baseUrl + "/mcp"),
    {
      requestInit: {
        headers: {
          Authorization: "Bearer " + token,
        },
      },
    }
  );
  const client = new Client(
    { name: "arrival-production-probe", version: "1.0.0" },
    { capabilities: {} }
  );

  await client.connect(transport);
  try {
    const { tools } = await client.listTools();
    validateToolContract(tools);

    for (const name of [
      "get_profile",
      "get_current_journey",
      "get_trip_progress",
      "get_alarm_state",
    ]) {
      const result = await client.callTool({ name, arguments: {} });
      assert.notEqual(result.isError, true, name + " returned an error");
    }

    return tools.length;
  } finally {
    await client.close();
  }
}

async function main() {
  const baseUrl = requiredBaseUrl();

  const health = await json(baseUrl + "/");
  assert.equal(health?.service, "arrival-alarm-connector");
  assert.equal(health?.status, "ok");
  assert.equal(health?.auth_mode, "oauth-jwt");

  const resource = await json(
    baseUrl + "/.well-known/oauth-protected-resource"
  );
  assert.equal(resource?.resource, baseUrl + "/mcp");
  assert.ok(Array.isArray(resource?.authorization_servers));
  assert.ok(resource.authorization_servers.length > 0);
  assert.ok(resource?.scopes_supported?.includes("arrival.read"));
  assert.ok(resource?.scopes_supported?.includes("arrival.write"));

  const issuer = resource.authorization_servers[0];
  assertHttps(issuer, "authorization server");

  const discovery = await fetchAuthorizationMetadata(issuer);
  const auth = discovery.body;
  assert.equal(auth?.issuer, issuer, "OAuth issuer must match exactly");
  assertHttps(auth?.authorization_endpoint, "authorization endpoint");
  assertHttps(auth?.token_endpoint, "token endpoint");
  assert.ok(
    auth?.code_challenge_methods_supported?.includes("S256"),
    "OAuth provider must advertise PKCE S256"
  );

  const oidcScopes = new Set(auth?.scopes_supported ?? []);
  const workspaceOidcReady =
    oidcScopes.has("openid") &&
    oidcScopes.has("email") &&
    typeof auth?.userinfo_endpoint === "string";
  if (process.env.PROBE_REQUIRE_WORKSPACE_OIDC === "true") {
    assert.ok(
      workspaceOidcReady,
      "Workspace-domain readiness requires openid/email and userinfo_endpoint"
    );
    assertHttps(auth.userinfo_endpoint, "userinfo endpoint");
  }

  const deviceOAuth = await json(
    baseUrl + "/.well-known/arrival-alarm-device-oauth"
  );
  assertHttps(deviceOAuth?.authorization_endpoint, "device authorization endpoint");
  assertHttps(deviceOAuth?.token_endpoint, "device token endpoint");
  assertHttps(deviceOAuth?.revocation_endpoint, "device revocation endpoint");
  assert.equal(
    deviceOAuth?.redirect_uri,
    "com.nazofobi.arrivalalarm://oauth/callback"
  );
  assert.ok(deviceOAuth?.scopes?.includes("arrival.device"));
  assert.ok(deviceOAuth?.client_id);
  if (deviceOAuth?.resource) assertHttps(deviceOAuth.resource, "device OAuth resource");

  const userToken = (process.env.PROBE_USER_BEARER_TOKEN || "").trim();
  let toolCount = null;
  if (userToken) {
    toolCount = await probeMcp(baseUrl, userToken);
  }

  const deviceToken = (process.env.PROBE_DEVICE_BEARER_TOKEN || "").trim();
  let pendingCommandCount = null;
  if (deviceToken) {
    const commands = await json(baseUrl + "/device/commands", {
      token: deviceToken,
    });
    assert.ok(Array.isArray(commands?.commands));
    pendingCommandCount = commands.commands.length;
  }

  const result = {
    status: "pass",
    base_url: baseUrl,
    public_health: true,
    protected_resource_metadata: true,
    authorization_metadata: discovery.url,
    pkce_s256: true,
    workspace_oidc_ready: workspaceOidcReady,
    device_oauth_metadata: true,
    authenticated_mcp_probe: Boolean(userToken),
    tool_count: toolCount,
    authenticated_device_probe: Boolean(deviceToken),
    pending_command_count: pendingCommandCount,
  };
  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(
    JSON.stringify({
      status: "fail",
      error: error instanceof Error ? error.message : String(error),
    })
  );
  process.exitCode = 1;
});
