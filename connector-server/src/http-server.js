import { createServer } from "node:http";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { bearerToken, createJwtAccessVerifier } from "./auth.js";
import { createGatewayStoreFromEnv } from "./gateway-store.js";
import { GatewayService } from "./gateway-service.js";
import { createArrivalAlarmMcpServer } from "./mcp-server.js";

const store = createGatewayStoreFromEnv();
const service = new GatewayService(store);
const port = Number(process.env.PORT ?? 8787);
const MCP_PATH = "/mcp";
const MAX_BODY_BYTES = 256 * 1024;
const CONNECTOR_SCOPES = ["arrival.read", "arrival.write"];
const DEVICE_SCOPE = "arrival.device";

let mcpVerifier = null;
let deviceVerifier = null;

function publicBaseUrl(req) {
  const configured = process.env.MCP_PUBLIC_BASE_URL?.replace(/\/$/, "");
  if (configured) return configured;
  if (process.env.NODE_ENV === "production") {
    throw new Error("production_public_base_url_not_configured");
  }
  const proto = req.headers["x-forwarded-proto"] ?? "http";
  return proto + "://" + (req.headers.host ?? "localhost:" + port);
}

function resourceMetadata(req) {
  const base = publicBaseUrl(req);
  const authorizationServer = process.env.OAUTH_AUTHORIZATION_SERVER?.replace(/\/$/, "");
  if (process.env.NODE_ENV === "production" && !authorizationServer) {
    throw new Error("production_oauth_not_configured");
  }
  return {
    resource: base + MCP_PATH,
    authorization_servers: authorizationServer ? [authorizationServer] : [],
    scopes_supported: CONNECTOR_SCOPES,
    bearer_methods_supported: ["header"],
  };
}

function resourceMetadataUrl(req) {
  return publicBaseUrl(req) + "/.well-known/oauth-protected-resource";
}

function oauthConfig({ device = false } = {}) {
  const issuer = (device ? process.env.DEVICE_OAUTH_ISSUER : null) ?? process.env.OAUTH_ISSUER;
  const audience = (device ? process.env.DEVICE_OAUTH_AUDIENCE : null) ?? process.env.OAUTH_AUDIENCE;
  const jwksUrl = (device ? process.env.DEVICE_OAUTH_JWKS_URL : null) ?? process.env.OAUTH_JWKS_URL;
  return { issuer, audience, jwksUrl };
}

function getMcpVerifier() {
  if (!mcpVerifier) mcpVerifier = createJwtAccessVerifier(oauthConfig());
  return mcpVerifier;
}

function getDeviceVerifier() {
  if (!deviceVerifier) {
    deviceVerifier = createJwtAccessVerifier(oauthConfig({ device: true }));
  }
  return deviceVerifier;
}

async function resolveMcpAuth(req) {
  if (process.env.NODE_ENV === "production") {
    resourceMetadata(req);
    return getMcpVerifier()(bearerToken(req), {
      requiredAnyScopes: CONNECTOR_SCOPES,
    });
  }
  if (process.env.ALLOW_UNAUTHENTICATED_DEV === "true") {
    return {
      userId: process.env.DEV_USER_ID ?? "dev-user",
      deviceId: null,
      scopes: new Set(CONNECTOR_SCOPES),
    };
  }
  const expected = process.env.DEV_USER_BEARER_TOKEN;
  if (!expected || bearerToken(req) !== expected) throw new Error("unauthorized");
  return {
    userId: process.env.DEV_USER_ID ?? "dev-user",
    deviceId: null,
    scopes: new Set(CONNECTOR_SCOPES),
  };
}

async function resolveDeviceAuth(req) {
  if (process.env.NODE_ENV === "production") {
    return getDeviceVerifier()(bearerToken(req), {
      requiredScopes: [DEVICE_SCOPE],
      requireDeviceId: true,
    });
  }
  const expected = process.env.DEV_DEVICE_BEARER_TOKEN;
  if (!expected || bearerToken(req) !== expected) throw new Error("unauthorized");
  return {
    userId: process.env.DEV_USER_ID ?? "dev-user",
    deviceId: "dev-device",
    scopes: new Set([DEVICE_SCOPE]),
  };
}

async function readJson(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new Error("request_too_large");
    chunks.push(chunk);
  }
  const text = Buffer.concat(chunks).toString("utf8");
  if (!text) return {};
  return JSON.parse(text);
}

function sendJson(res, status, value) {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(value));
}

function sendAuthError(req, res, error) {
  const message = error instanceof Error ? error.message : "unauthorized";
  const authFailure = message === "unauthorized";
  const scopeFailure = message === "insufficient_scope";
  const status = authFailure ? 401 : scopeFailure ? 403 : 503;
  if (authFailure || scopeFailure) {
    try {
      const oauthError = scopeFailure ? "insufficient_scope" : "invalid_token";
      res.setHeader(
        "WWW-Authenticate",
        'Bearer resource_metadata="' + resourceMetadataUrl(req) +
          '", error="' + oauthError + '"'
      );
    } catch {
      // Preserve the original authentication error if metadata cannot be built.
    }
  }
  sendJson(res, status, { error: message });
}

const httpServer = createServer(async (req, res) => {
  if (!req.url) {
    res.writeHead(400).end("Missing URL");
    return;
  }
  const url = new URL(req.url, "http://" + (req.headers.host ?? "localhost"));

  if (req.method === "GET" && url.pathname === "/.well-known/oauth-protected-resource") {
    try {
      sendJson(res, 200, resourceMetadata(req));
    } catch (error) {
      sendAuthError(req, res, error);
    }
    return;
  }

  if (req.method === "GET" && url.pathname === "/") {
    sendJson(res, 200, {
      service: "arrival-alarm-connector",
      status: "ok",
      auth_mode: process.env.NODE_ENV === "production" ? "oauth-jwt" : "development",
    });
    return;
  }

  if (req.method === "POST" && url.pathname === "/device/state") {
    try {
      const auth = await resolveDeviceAuth(req);
      const snapshot = await readJson(req);
      sendJson(res, 200, store.putSnapshot(auth.userId, snapshot, auth.deviceId));
    } catch (error) {
      sendAuthError(req, res, error);
    }
    return;
  }

  if (req.method === "GET" && url.pathname === "/device/commands") {
    try {
      const auth = await resolveDeviceAuth(req);
      sendJson(res, 200, { commands: store.pendingCommands(auth.userId, auth.deviceId) });
    } catch (error) {
      sendAuthError(req, res, error);
    }
    return;
  }

  if (req.method === "POST" && url.pathname === "/device/receipt") {
    try {
      const auth = await resolveDeviceAuth(req);
      const receipt = await readJson(req);
      sendJson(res, 200, store.submitReceipt(auth.userId, receipt, auth.deviceId));
    } catch (error) {
      sendAuthError(req, res, error);
    }
    return;
  }

  if (req.method === "OPTIONS" && url.pathname === MCP_PATH) {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "content-type, mcp-session-id, authorization",
      "Access-Control-Expose-Headers": "Mcp-Session-Id",
    });
    res.end();
    return;
  }

  const MCP_METHODS = new Set(["POST", "GET", "DELETE"]);
  if (url.pathname === MCP_PATH && req.method && MCP_METHODS.has(req.method)) {
    let auth;
    try {
      auth = await resolveMcpAuth(req);
    } catch (error) {
      sendAuthError(req, res, error);
      return;
    }

    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");

    const mcpServer = createArrivalAlarmMcpServer({
      service,
      userId: auth.userId,
      scopes: auth.scopes,
      resourceMetadataUrl: resourceMetadataUrl(req),
    });
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
      enableJsonResponse: true,
    });

    res.on("close", () => {
      transport.close();
      mcpServer.close();
    });

    try {
      await mcpServer.connect(transport);
      await transport.handleRequest(req, res);
    } catch (error) {
      console.error("MCP request failed", error);
      if (!res.headersSent) sendJson(res, 500, { error: "internal_server_error" });
    }
    return;
  }

  res.writeHead(404).end("Not Found");
});

httpServer.listen(port, () => {
  console.log("Arrival Alarm connector listening on http://localhost:" + port + MCP_PATH);
});

function shutdown() {
  httpServer.close(() => {
    store.close?.();
    process.exit(0);
  });
}

process.once("SIGTERM", shutdown);
process.once("SIGINT", shutdown);
