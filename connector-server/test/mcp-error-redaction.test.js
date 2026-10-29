import test from "node:test";
import assert from "node:assert/strict";
import { mcpErrorResult } from "../src/mcp-server.js";

test("MCP tool errors redact unexpected provider and infrastructure details", () => {
  for (const error of [
    new Error("production_oauth_not_configured"),
    new Error("database connection failed at secret-internal-host"),
    new Error("Bearer secret-live-token"),
    new SyntaxError('Unexpected token in {"access_token":"sensitive"}'),
    new TypeError("internal credential value"),
    "unsafe backend message",
    null,
  ]) {
    assert.deepEqual(mcpErrorResult(error), {
      isError: true,
      content: [{ type: "text", text: "connector_error" }],
    });
  }
});

test("MCP tool errors retain documented actionable gateway codes", () => {
  for (const code of [
    "unauthorized",
    "insufficient_scope",
    "device_state_unavailable",
    "stale_device_state",
    "state_version_mismatch",
    "invalid_command",
  ]) {
    assert.deepEqual(mcpErrorResult(new Error(code)), {
      isError: true,
      content: [{ type: "text", text: code }],
    });
  }
});

test("MCP auth challenges are preserved only for recognized auth failures", () => {
  const url = "https://gateway.example/.well-known/oauth-protected-resource";
  const unauthorized = mcpErrorResult(new Error("unauthorized"), url);
  assert.match(unauthorized._meta["mcp/www_authenticate"][0], /error="invalid_token"/);
  assert.equal(unauthorized.content[0].text, "unauthorized");

  const insufficient = mcpErrorResult(new Error("insufficient_scope"), url);
  assert.match(insufficient._meta["mcp/www_authenticate"][0], /error="insufficient_scope"/);
  assert.equal(insufficient.content[0].text, "insufficient_scope");

  const unknown = mcpErrorResult(new Error("database private connection string"), url);
  assert.equal(unknown.content[0].text, "connector_error");
  assert.equal("_meta" in unknown, false);
});

test("MCP authentication challenges reject unsafe metadata URLs without changing public error codes", () => {
  const unsafe = [
    'https://gateway.example/metadata" , error="injected',
    "https://user:pass@gateway.example/metadata",
    "https://gateway.example/metadata#",
    "https://gateway.example/metadata#private",
    "https://gateway.example/metadata\r\nX-Injected: true",
    "https://gateway.example/metadata\\spoof",
    "http://remote.example/metadata",
    "javascript:alert(1)",
    " https://gateway.example/metadata",
    null,
  ];
  for (const value of unsafe) {
    const result = mcpErrorResult(new Error("unauthorized"), value);
    assert.equal(result.content[0].text, "unauthorized");
    assert.equal("_meta" in result, false, String(value));
  }
});

test("MCP authentication challenges preserve canonical safe HTTPS and local-loopback metadata", () => {
  for (const value of [
    "https://gateway.example/.well-known/oauth-protected-resource?test=%22",
    "http://localhost:8787/.well-known/oauth-protected-resource",
    "http://127.0.0.1:8787/.well-known/oauth-protected-resource",
  ]) {
    const result = mcpErrorResult(new Error("insufficient_scope"), value);
    assert.equal(result._meta["mcp/www_authenticate"][0],
      `Bearer resource_metadata="${value}", error="insufficient_scope", error_description="The connected account needs additional permission for this tool."`);
  }
});
