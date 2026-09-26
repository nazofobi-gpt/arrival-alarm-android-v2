import test from "node:test";
import assert from "node:assert/strict";
import {
  authContextFromPayload,
  createJwtAccessVerifier,
  scopesFromPayload,
} from "../src/auth.js";

test("scope parser accepts OAuth scope string and scp arrays", () => {
  const scopes = scopesFromPayload({
    scope: "arrival.read profile",
    scp: ["arrival.write", "arrival.device"],
  });
  assert.deepEqual(
    [...scopes].sort(),
    ["arrival.device", "arrival.read", "arrival.write", "profile"]
  );
});

test("read-only token is accepted for read policy and rejected for write policy", () => {
  const payload = { sub: "user-1", scope: "arrival.read" };
  const read = authContextFromPayload(payload, {
    requiredScopes: ["arrival.read"],
  });
  assert.equal(read.userId, "user-1");
  assert.throws(
    () =>
      authContextFromPayload(payload, {
        requiredScopes: ["arrival.write"],
      }),
    /insufficient_scope/
  );
});

test("device policy requires both device scope and a bound device id", () => {
  assert.throws(
    () =>
      authContextFromPayload(
        { sub: "user-1", scope: "arrival.device" },
        { requiredScopes: ["arrival.device"], requireDeviceId: true }
      ),
    /unauthorized/
  );

  const auth = authContextFromPayload(
    { sub: "user-1", scope: "arrival.device", device_id: "phone-1" },
    { requiredScopes: ["arrival.device"], requireDeviceId: true }
  );
  assert.equal(auth.deviceId, "phone-1");
});

test("jwt verifier validates issuer audience before returning scoped context", async () => {
  const calls = [];
  const verifier = createJwtAccessVerifier({
    issuer: "https://issuer.example",
    audience: "arrival-alarm",
    jwksUrl: "https://issuer.example/jwks.json",
    jwksResolver: async () => {
      throw new Error("should not be called by fake verifier");
    },
    jwtVerifyImpl: async (token, key, options) => {
      calls.push({ token, key, options });
      return {
        payload: {
          sub: "user-42",
          scope: "arrival.read arrival.write",
        },
      };
    },
  });

  const auth = await verifier("signed-token", {
    requiredAnyScopes: ["arrival.read", "arrival.write"],
  });

  assert.equal(auth.userId, "user-42");
  assert.equal(auth.scopes.has("arrival.write"), true);
  assert.equal(calls[0].options.issuer, "https://issuer.example");
  assert.equal(calls[0].options.audience, "arrival-alarm");
});
