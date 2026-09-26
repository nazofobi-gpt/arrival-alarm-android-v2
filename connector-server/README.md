# Arrival Alarm connector server

This service is the external connector boundary between ChatGPT and the Android app. It does not run an AI model and it does not contain an OpenAI API key.

## Interfaces

- `/mcp`: MCP Streamable HTTP endpoint exposed to ChatGPT.
- `POST /device/state`: Android uploads the versioned connector snapshot.
- `GET /device/commands`: Android receives allow-listed queued commands.
- `POST /device/receipt`: Android posts the domain command receipt.

All write commands use an idempotency key and an expected app state version. The gateway rejects stale device state before queueing a write; the Android command processor independently re-checks confirmation, freshness and state version before applying the command.

## Development

Run with Node 22:

```sh
npm install
npm test
DEV_USER_ID=dev-user \
DEV_USER_BEARER_TOKEN=local-mcp-token \
DEV_DEVICE_BEARER_TOKEN=local-device-token \
node src/http-server.js
```

`ALLOW_UNAUTHENTICATED_DEV=true` may only be used for isolated local MCP development. Device endpoints still require `DEV_DEVICE_BEARER_TOKEN`.

## Production authentication

The server has a provider-neutral JWT/OIDC resource-server gate. In production, configure:

- `MCP_PUBLIC_BASE_URL`
- `OAUTH_AUTHORIZATION_SERVER`
- `OAUTH_ISSUER`
- `OAUTH_AUDIENCE`
- `OAUTH_JWKS_URL`

MCP access tokens are verified against issuer, audience and JWKS. Read tools require `arrival.read`; write tools require `arrival.write`.

Device sync uses the same verifier by default, or separate values via `DEVICE_OAUTH_ISSUER`, `DEVICE_OAUTH_AUDIENCE` and `DEVICE_OAUTH_JWKS_URL`. Device tokens must contain the `arrival.device` scope and a non-empty `device_id` claim.

Production still requires deployment-specific infrastructure before end-to-end acceptance:

1. An authorization server/client registration and device-token issuance flow.
2. Durable per-user state, command and receipt storage.
3. HTTPS hosting and secret/config storage outside the repository.
4. Revoke/disconnect handling and operational audit/retention policy.

Do not replace these gates with a hard-coded bearer token, API key in the APK, or anonymous public endpoints.
