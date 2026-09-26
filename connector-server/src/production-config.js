function required(env, name) {
  const value = env[name]?.trim();
  if (!value) throw new Error("production_config_missing_" + name.toLowerCase());
  return value;
}

function httpsUrl(value, name) {
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error("production_config_invalid_" + name.toLowerCase());
  }
  if (parsed.protocol !== "https:" || !parsed.hostname) {
    throw new Error("production_config_https_required_" + name.toLowerCase());
  }
  return parsed.toString().replace(/\/$/, "");
}

/**
 * Fail fast on deployment mistakes instead of discovering them on the first
 * authenticated MCP/device request.
 */
export function validateProductionEnvironment(env = process.env) {
  if (env.NODE_ENV !== "production") return { production: false };

  const publicBaseUrl = httpsUrl(
    required(env, "MCP_PUBLIC_BASE_URL"),
    "MCP_PUBLIC_BASE_URL"
  );
  const authorizationServer = httpsUrl(
    required(env, "OAUTH_AUTHORIZATION_SERVER"),
    "OAUTH_AUTHORIZATION_SERVER"
  );
  const issuer = httpsUrl(required(env, "OAUTH_ISSUER"), "OAUTH_ISSUER");
  const audience = required(env, "OAUTH_AUDIENCE");
  const jwksUrl = httpsUrl(required(env, "OAUTH_JWKS_URL"), "OAUTH_JWKS_URL");
  const sqlitePath = required(env, "GATEWAY_SQLITE_PATH");

  const deviceOverrides = [
    env.DEVICE_OAUTH_ISSUER?.trim(),
    env.DEVICE_OAUTH_AUDIENCE?.trim(),
    env.DEVICE_OAUTH_JWKS_URL?.trim(),
  ];
  if (deviceOverrides.some(Boolean) && !deviceOverrides.every(Boolean)) {
    throw new Error("production_config_incomplete_device_oauth_override");
  }
  if (deviceOverrides.every(Boolean)) {
    httpsUrl(deviceOverrides[0], "DEVICE_OAUTH_ISSUER");
    httpsUrl(deviceOverrides[2], "DEVICE_OAUTH_JWKS_URL");
  }

  return {
    production: true,
    publicBaseUrl,
    authorizationServer,
    issuer,
    audience,
    jwksUrl,
    sqlitePath,
  };
}
