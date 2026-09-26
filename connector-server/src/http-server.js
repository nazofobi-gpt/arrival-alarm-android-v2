import { createServer } from "node:http";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { InMemoryGatewayStore } from "./gateway-store.js";
import { GatewayService } from "./gateway-service.js";
import { createArrivalAlarmMcpServer } from "./mcp-server.js";

const store = new InMemoryGatewayStore();
const service = new GatewayService(store);
const port = Number(process.env.PORT ?? 8787);
const MCP_PATH = "/mcp";
const MAX_BODY_BYTES = 256 * 1024;

function bearer(req) {
  const value = req.headers.authorization ?? "";
  return value.startsWith("Bearer ") ? value.slice(7) : null;
}

function resolveMcpUser(req) {
  if (process.env.NODE_ENV === "production") {
    throw new Error("production_oauth_not_configured");
  }
  if (process.env.ALLOW_UNAUTHENTICATED_DEV === "true") {
    return process.env.DEV_USER_ID ?? "dev-user";
  }
  const expected = process.env.DEV_USER_BEARER_TOKEN;
  if (!expected || bearer(req) !== expected) throw new Error("unauthorized");
  return process.env.DEV_USER_ID ?? "dev-user";
}

function resolveDeviceUser(req) {
  if (process.env.NODE_ENV === "production") {
    throw new Error("production_device_auth_not_configured");
  }
  const expected = process.env.DEV_DEVICE_BEARER_TOKEN;
  if (!expected || bearer(req) !== expected) throw new Error("unauthorized");
  return process.env.DEV_USER_ID ?? "dev-user";
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

function sendAuthError(res, error) {
  const message = error instanceof Error ? error.message : "unauthorized";
  const status = message === "unauthorized" ? 401 : 503;
  sendJson(res, status, { error: message });
}

const httpServer = createServer(async (req, res) => {
  if (!req.url) {
    res.writeHead(400).end("Missing URL");
    return;
  }
  const url = new URL(req.url, "http://" + (req.headers.host ?? "localhost"));

  if (req.method === "GET" && url.pathname === "/") {
    sendJson(res, 200, {
      service: "arrival-alarm-connector",
      status: "ok",
      auth_mode: process.env.NODE_ENV === "production" ? "oauth-required" : "development",
    });
    return;
  }

  if (req.method === "POST" && url.pathname === "/device/state") {
    let userId;
    try {
      userId = resolveDeviceUser(req);
      const snapshot = await readJson(req);
      sendJson(res, 200, store.putSnapshot(userId, snapshot));
    } catch (error) {
      sendAuthError(res, error);
    }
    return;
  }

  if (req.method === "GET" && url.pathname === "/device/commands") {
    try {
      const userId = resolveDeviceUser(req);
      sendJson(res, 200, { commands: store.pendingCommands(userId) });
    } catch (error) {
      sendAuthError(res, error);
    }
    return;
  }

  if (req.method === "POST" && url.pathname === "/device/receipt") {
    try {
      const userId = resolveDeviceUser(req);
      const receipt = await readJson(req);
      sendJson(res, 200, store.submitReceipt(userId, receipt));
    } catch (error) {
      sendAuthError(res, error);
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
    let userId;
    try {
      userId = resolveMcpUser(req);
    } catch (error) {
      sendAuthError(res, error);
      return;
    }

    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");

    const mcpServer = createArrivalAlarmMcpServer({ service, userId });
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
