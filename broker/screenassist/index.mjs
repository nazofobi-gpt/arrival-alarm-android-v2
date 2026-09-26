import { createHash, timingSafeEqual } from "node:crypto";

const OPENAI_CLIENT_SECRETS_URL = "https://api.openai.com/v1/realtime/client_secrets";
const DEFAULT_MODEL = "gpt-realtime-2.1";
const MIN_SECRET_LIFETIME_SECONDS = 30;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "Pragma": "no-cache",
    },
  });
}

function bearerToken(request) {
  const value = request.headers.get("authorization") ?? "";
  if (!value.toLowerCase().startsWith("bearer ")) return null;
  const token = value.slice(7).trim();
  return token || null;
}

function tokenMatchesSha256(token, expectedHex) {
  if (!/^[0-9a-f]{64}$/i.test(expectedHex)) return false;
  const actual = createHash("sha256").update(token, "utf8").digest();
  const expected = Buffer.from(expectedHex, "hex");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

function readConfig(env) {
  const apiKey = env.OPENAI_API_KEY?.trim();
  const callerTokenSha256 = env.SCREEN_ASSIST_CALLER_TOKEN_SHA256?.trim();
  const model = env.OPENAI_REALTIME_MODEL?.trim() || DEFAULT_MODEL;
  const safetyIdentifier = env.OPENAI_SAFETY_IDENTIFIER?.trim() || null;

  if (!apiKey || !callerTokenSha256) return null;
  if (!/^[0-9a-f]{64}$/i.test(callerTokenSha256)) return null;

  return { apiKey, callerTokenSha256, model, safetyIdentifier };
}

function normalizeClientSecret(payload, nowEpochSeconds) {
  if (!payload || typeof payload !== "object") return null;
  if ("api_key" in payload || "apiKey" in payload) return null;

  const value = typeof payload.value === "string" ? payload.value : "";
  const expiresAt = Number(payload.expires_at ?? payload.expiresAt ?? 0);

  if (!value || !Number.isFinite(expiresAt)) return null;
  if (expiresAt - nowEpochSeconds < MIN_SECRET_LIFETIME_SECONDS) return null;

  return { value, expires_at: Math.trunc(expiresAt) };
}

export function createScreenAssistBroker({
  env = process.env,
  fetchImpl = globalThis.fetch,
  nowEpochSeconds = () => Math.floor(Date.now() / 1000),
} = {}) {
  return {
    async fetch(request) {
      if (request.method !== "POST") {
        return json({ error: "method_not_allowed" }, 405);
      }

      const config = readConfig(env);
      if (!config) {
        return json({ error: "broker_not_configured" }, 503);
      }

      const callerToken = bearerToken(request);
      if (!callerToken || !tokenMatchesSha256(callerToken, config.callerTokenSha256)) {
        return json({ error: "unauthorized" }, 401);
      }

      const headers = {
        Authorization: `Bearer ${config.apiKey}`,
        "Content-Type": "application/json",
      };
      if (config.safetyIdentifier) {
        headers["OpenAI-Safety-Identifier"] = config.safetyIdentifier;
      }

      let upstream;
      try {
        upstream = await fetchImpl(OPENAI_CLIENT_SECRETS_URL, {
          method: "POST",
          headers,
          body: JSON.stringify({
            session: {
              type: "realtime",
              model: config.model,
              instructions:
                "You are an on-device screen guidance assistant. Describe the visible UI accurately and give one concise next action at a time. Never claim that you clicked, typed, submitted, purchased, or changed another app. The user performs external-app actions.",
            },
          }),
        });
      } catch {
        return json({ error: "openai_unreachable" }, 502);
      }

      if (!upstream.ok) {
        return json({ error: "openai_client_secret_failed" }, 502);
      }

      let payload;
      try {
        payload = await upstream.json();
      } catch {
        return json({ error: "invalid_openai_response" }, 502);
      }

      const secret = normalizeClientSecret(payload, nowEpochSeconds());
      if (!secret) {
        return json({ error: "invalid_openai_client_secret" }, 502);
      }

      return json(secret);
    },
  };
}

export default createScreenAssistBroker();
