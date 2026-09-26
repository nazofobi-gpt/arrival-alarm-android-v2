const DEVICE_OAUTH_REDIRECT_URI = "com.nazofobi.arrivalalarm://oauth/callback";

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

export function deviceOAuthMetadataFromEnv(env = process.env) {
  const authorizationEndpoint = httpsUrl(
    required(env, "DEVICE_OAUTH_AUTHORIZATION_ENDPOINT"),
    "DEVICE_OAUTH_AUTHORIZATION_ENDPOINT"
  );
  const tokenEndpoint = httpsUrl(
    required(env, "DEVICE_OAUTH_TOKEN_ENDPOINT"),
    "DEVICE_OAUTH_TOKEN_ENDPOINT"
  );
  const revocationEndpoint = httpsUrl(
    required(env, "DEVICE_OAUTH_REVOCATION_ENDPOINT"),
    "DEVICE_OAUTH_REVOCATION_ENDPOINT"
  );
  const clientId = required(env, "DEVICE_OAUTH_CLIENT_ID");
  const scopes = (env.DEVICE_OAUTH_SCOPES?.trim() || "arrival.device offline_access")
    .split(/\s+/)
    .filter(Boolean);
  if (!scopes.includes("arrival.device")) {
    throw new Error("production_config_device_oauth_scope_missing");
  }

  const deviceIdParameter =
    env.DEVICE_OAUTH_DEVICE_ID_PARAMETER?.trim() || "device_id";
  if (!/^[A-Za-z0-9_.-]{1,64}$/.test(deviceIdParameter)) {
    throw new Error("production_config_device_oauth_parameter_invalid");
  }
  const reservedAuthorizationParameters = new Set([
    "response_type",
    "client_id",
    "redirect_uri",
    "scope",
    "state",
    "code_challenge",
    "code_challenge_method",
    "resource",
  ]);
  if (reservedAuthorizationParameters.has(deviceIdParameter)) {
    throw new Error("production_config_device_oauth_parameter_reserved");
  }

  const rawResource = env.DEVICE_OAUTH_RESOURCE?.trim();
  const resource = rawResource
    ? httpsUrl(rawResource, "DEVICE_OAUTH_RESOURCE")
    : null;

  return {
    authorization_endpoint: authorizationEndpoint,
    token_endpoint: tokenEndpoint,
    revocation_endpoint: revocationEndpoint,
    client_id: clientId,
    scopes,
    redirect_uri: DEVICE_OAUTH_REDIRECT_URI,
    resource,
    audience: env.DEVICE_OAUTH_AUDIENCE?.trim() || null,
    device_id_parameter: deviceIdParameter,
  };
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
  const deviceOAuth = deviceOAuthMetadataFromEnv(env);

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
    deviceOAuth,
  };
}
