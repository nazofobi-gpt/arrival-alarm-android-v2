import { WebStandardStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js";
import { bearerToken, createJwtAccessVerifier } from "./auth.js";
import { AsyncGatewayService } from "./async-gateway-service.js";
import { D1GatewayStore } from "./d1-gateway-store.js";
import { createArrivalAlarmMcpServer } from "./mcp-server.js";
import { deviceOAuthMetadataFromEnv } from "./production-config.js";

const MCP_PATH = "/mcp";
const CONNECTOR_SCOPES = ["arrival.read", "arrival.write"];
const DEVICE_SCOPE = "arrival.device";
const MAX_BODY_BYTES = 256 * 1024;
const verifierCache = new Map();

function normalizedHttps(value, name, { preserveTrailingSlash = false } = {}) {
  const raw = typeof value === "string" ? value.trim() : "";
  let parsed;
  try {
    parsed = new URL(raw);
  } catch {
    throw new Error("worker_invalid_" + name);
  }
  if (parsed.protocol !== "https:" || !parsed.hostname) {
    throw new Error("worker_https_required_" + name);
  }
  const canonical = parsed.toString();
  return preserveTrailingSlash ? canonical : canonical.replace(/\/$/, "");
}

function publicBaseUrl(request, env) {
  const configured = env.MCP_PUBLIC_BASE_URL?.trim();
  if (configured) return normalizedHttps(configured, "public_base_url");
  const origin = new URL(request.url).origin;
  return normalizedHttps(origin, "request_origin");
}

function resourceMetadata(request, env) {
  const base = publicBaseUrl(request, env);
  const authorizationServer = normalizedHttps(
    env.OAUTH_AUTHORIZATION_SERVER,
    "authorization_server",
    { preserveTrailingSlash: true }
  );
  return {
    resource: base + MCP_PATH,
    authorization_servers: [authorizationServer],
    scopes_supported: CONNECTOR_SCOPES,
    bearer_methods_supported: ["header"],
  };
}

function resourceMetadataUrl(request, env) {
  return publicBaseUrl(request, env) + "/.well-known/oauth-protected-resource";
}

function verifierFor(env, { device = false } = {}) {
  const issuer = normalizedHttps(
    (device ? env.DEVICE_OAUTH_ISSUER : null) || env.OAUTH_ISSUER,
    device ? "device_oauth_issuer" : "oauth_issuer",
    { preserveTrailingSlash: true }
  );
  const audience =
    ((device ? env.DEVICE_OAUTH_AUDIENCE : null) || env.OAUTH_AUDIENCE || "").trim();
  if (!audience) throw new Error("worker_oauth_audience_required");
  const jwksUrl = normalizedHttps(
    (device ? env.DEVICE_OAUTH_JWKS_URL : null) || env.OAUTH_JWKS_URL,
    device ? "device_oauth_jwks_url" : "oauth_jwks_url"
  );
  const key = [device ? "device" : "mcp", issuer, audience, jwksUrl].join("|");
  let verifier = verifierCache.get(key);
  if (!verifier) {
    verifier = createJwtAccessVerifier({ issuer, audience, jwksUrl });
    verifierCache.set(key, verifier);
  }
  return verifier;
}

async function resolveMcpAuth(request, env) {
  resourceMetadata(request, env);
  return verifierFor(env)(bearerToken(request), {
    requiredAnyScopes: CONNECTOR_SCOPES,
  });
}

async function resolveDeviceAuth(request, env) {
  return verifierFor(env, { device: true })(bearerToken(request), {
    requiredScopes: [DEVICE_SCOPE],
    requireDeviceId: true,
    deviceIdClaim: env.DEVICE_OAUTH_DEVICE_ID_CLAIM?.trim() || null,
  });
}

async function readJson(request) {
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.byteLength > MAX_BODY_BYTES) throw new Error("request_too_large");
  if (bytes.byteLength === 0) return {};
  return JSON.parse(new TextDecoder().decode(bytes));
}

function json(value, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      ...extraHeaders,
    },
  });
}

function authError(request, env, error) {
  const message = error instanceof Error ? error.message : "unauthorized";
  const authFailure = message === "unauthorized";
  const scopeFailure = message === "insufficient_scope";
  const status = authFailure ? 401 : scopeFailure ? 403 : 503;
  const headers = {};

  if (authFailure || scopeFailure) {
    const oauthError = scopeFailure ? "insufficient_scope" : "invalid_token";
    try {
      headers["WWW-Authenticate"] =
        'Bearer resource_metadata="' +
        resourceMetadataUrl(request, env) +
        '", error="' +
        oauthError +
        '"';
    } catch {
      // Keep original auth error when public metadata is misconfigured.
    }
  }
  return json({ error: message }, status, headers);
}

function withMcpCors(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Expose-Headers", "Mcp-Session-Id");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

async function handleMcp(request, env, service) {
  let auth;
  try {
    auth = await resolveMcpAuth(request, env);
  } catch (error) {
    return authError(request, env, error);
  }

  const server = createArrivalAlarmMcpServer({
    service,
    userId: auth.userId,
    scopes: auth.scopes,
    resourceMetadataUrl: resourceMetadataUrl(request, env),
  });
  const transport = new WebStandardStreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
    enableJsonResponse: true,
  });

  await server.connect(transport);
  try {
    return withMcpCors(await transport.handleRequest(request));
  } finally {
    await transport.close();
    await server.close();
  }
}

export function createCloudflareWorker() {
  return {
    async fetch(request, env) {
      const url = new URL(request.url);
      const store = new D1GatewayStore(env.DB);
      const service = new AsyncGatewayService(store);

      if (request.method === "GET" && url.pathname === "/") {
        return json({
          service: "arrival-alarm-connector",
          status: "ok",
          runtime: "cloudflare-workers-free",
          store: "d1",
          auth_mode: "oauth-jwt",
        });
      }

      if (
        request.method === "GET" &&
        url.pathname === "/.well-known/oauth-protected-resource"
      ) {
        try {
          return json(resourceMetadata(request, env));
        } catch (error) {
          return authError(request, env, error);
        }
      }

      if (
        request.method === "GET" &&
        url.pathname === "/.well-known/arrival-alarm-device-oauth"
      ) {
        try {
          return json(deviceOAuthMetadataFromEnv(env));
        } catch (error) {
          const message =
            error instanceof Error ? error.message : "device_oauth_not_configured";
          return json({ error: message }, 503);
        }
      }

      if (request.method === "POST" && url.pathname === "/device/state") {
        try {
          const auth = await resolveDeviceAuth(request, env);
          const snapshot = await readJson(request);
          return json(await store.putSnapshot(auth.userId, snapshot, auth.deviceId));
        } catch (error) {
          return authError(request, env, error);
        }
      }

      if (request.method === "GET" && url.pathname === "/device/commands") {
        try {
          const auth = await resolveDeviceAuth(request, env);
          return json({
            commands: await store.pendingCommands(auth.userId, auth.deviceId),
          });
        } catch (error) {
          return authError(request, env, error);
        }
      }

      if (request.method === "POST" && url.pathname === "/device/receipt") {
        try {
          const auth = await resolveDeviceAuth(request, env);
          const receipt = await readJson(request);
          return json(
            await store.submitReceipt(auth.userId, receipt, auth.deviceId)
          );
        } catch (error) {
          return authError(request, env, error);
        }
      }

      if (request.method === "OPTIONS" && url.pathname === MCP_PATH) {
        return new Response(null, {
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
            "Access-Control-Allow-Headers":
              "content-type, mcp-session-id, mcp-protocol-version, authorization",
            "Access-Control-Expose-Headers": "Mcp-Session-Id",
          },
        });
      }

      if (
        url.pathname === MCP_PATH &&
        ["POST", "GET", "DELETE"].includes(request.method)
      ) {
        return handleMcp(request, env, service);
      }

      return new Response("Not Found", { status: 404 });
    },
  };
}

export default createCloudflareWorker();
