import test from "node:test";
import assert from "node:assert/strict";
import { classifyHttpError } from "../src/http-error-status.js";

test("Node device payload errors report actionable client status without OAuth challenge", () => {
  assert.deepEqual(classifyHttpError(new Error("request_too_large")), {
    status: 413,
    code: "request_too_large",
    challenge: null,
  });
  assert.deepEqual(classifyHttpError(new SyntaxError('Unexpected token in {"token":"secret"}')), {
    status: 400,
    code: "invalid_json",
    challenge: null,
  });
});

test("Node auth errors retain their distinct OAuth challenge and status", () => {
  assert.deepEqual(classifyHttpError(new Error("unauthorized")), {
    status: 401,
    code: "unauthorized",
    challenge: "invalid_token",
  });
  assert.deepEqual(classifyHttpError(new Error("insufficient_scope")), {
    status: 403,
    code: "insufficient_scope",
    challenge: "insufficient_scope",
  });
});

test("unexpected provider failures report 503 without leaking infrastructure details", () => {
  for (const error of [
    new Error("production_oauth_not_configured"),
    new Error("database connection failed at secret-internal-host"),
    new TypeError("private credential value"),
  ]) {
    assert.deepEqual(classifyHttpError(error), {
      status: 503,
      code: "service_unavailable",
      challenge: null,
    });
  }
});
