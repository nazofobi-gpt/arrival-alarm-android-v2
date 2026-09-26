import { createRemoteJWKSet, jwtVerify } from "jose";

export function bearerToken(req) {
  const value = req.headers.authorization ?? "";
  return value.startsWith("Bearer ") ? value.slice(7).trim() : null;
}

export function scopesFromPayload(payload) {
  const values = new Set();
  const add = (value) => {
    if (typeof value === "string") {
      for (const item of value.split(/\s+/)) {
        if (item) values.add(item);
      }
    } else if (Array.isArray(value)) {
      for (const item of value) {
        if (typeof item === "string" && item.trim()) values.add(item.trim());
      }
    }
  };
  add(payload.scope);
  add(payload.scp);
  return values;
}

export function authContextFromPayload(
  payload,
  { requiredScopes = [], requiredAnyScopes = [], requireDeviceId = false } = {}
) {
  const userId = typeof payload.sub === "string" ? payload.sub.trim() : "";
  if (!userId) throw new Error("unauthorized");

  const scopes = scopesFromPayload(payload);
  for (const scope of requiredScopes) {
    if (!scopes.has(scope)) throw new Error("insufficient_scope");
  }
  if (
    requiredAnyScopes.length > 0 &&
    !requiredAnyScopes.some((scope) => scopes.has(scope))
  ) {
    throw new Error("insufficient_scope");
  }

  const rawDeviceId = payload.device_id ?? payload.deviceId;
  const deviceId = typeof rawDeviceId === "string" ? rawDeviceId.trim() : null;
  if (requireDeviceId && !deviceId) throw new Error("unauthorized");

  return { userId, deviceId, scopes };
}

export function createJwtAccessVerifier({
  issuer,
  audience,
  jwksUrl,
  jwtVerifyImpl = jwtVerify,
  jwksResolver = null,
}) {
  if (!issuer || !audience || !jwksUrl) {
    throw new Error("production_oauth_not_configured");
  }
  const jwks = jwksResolver ?? createRemoteJWKSet(new URL(jwksUrl), {
    timeoutDuration: 5_000,
    cooldownDuration: 30_000,
    cacheMaxAge: 10 * 60 * 1_000,
  });

  return async function verifyAccessToken(token, policy = {}) {
    if (!token) throw new Error("unauthorized");
    let payload;
    try {
      ({ payload } = await jwtVerifyImpl(token, jwks, { issuer, audience }));
    } catch {
      throw new Error("unauthorized");
    }
    return authContextFromPayload(payload, policy);
  };
}
