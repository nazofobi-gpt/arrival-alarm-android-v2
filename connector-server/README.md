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
- `GATEWAY_SQLITE_PATH` (persistent writable SQLite file, for example `/data/arrival-alarm.db`)

MCP access tokens are verified against issuer, audience and JWKS. Read tools require `arrival.read`; write tools require `arrival.write`.

Device sync uses the same verifier by default, or separate values via `DEVICE_OAUTH_ISSUER`, `DEVICE_OAUTH_AUDIENCE` and `DEVICE_OAUTH_JWKS_URL`. Device tokens must contain the `arrival.device` scope and a non-empty `device_id` claim.

Production uses the configured SQLite store for durable per-user snapshots, queued commands and receipts. Startup fails closed when `GATEWAY_SQLITE_PATH` is missing in production.

Production still requires deployment-specific infrastructure before end-to-end acceptance:

1. An authorization server/client registration and device-token issuance flow.
2. A persistent volume for the SQLite database (or a later compatible managed store for multi-instance deployment).
3. HTTPS hosting and secret/config storage outside the repository.
4. Revoke/disconnect handling and operational audit/retention policy.

Do not replace these gates with a hard-coded bearer token, API key in the APK, or anonymous public endpoints.


## Production container

The connector includes a non-root Node 22 container. Build it from `connector-server/`:

```sh
docker build -t arrival-alarm-connector .
```

Production startup validates the required configuration before listening. Use `.env.example` as the contract and inject real values through the hosting platform's secret/config system; never commit tokens or private keys.

A persistent volume must back `/data` so `GATEWAY_SQLITE_PATH=/data/arrival-alarm.db` survives restarts. The image exposes port 8787 and includes a local health check. TLS should terminate at the hosting platform/reverse proxy; `MCP_PUBLIC_BASE_URL`, OAuth issuer/authorization/JWKS endpoints must still be public HTTPS URLs.

The container does not include an authorization server. Production still needs an OAuth 2.1 provider that supports the MCP/ChatGPT authorization-code + PKCE flow and a device-token issuance/refresh/revoke path.


## Android device OAuth provisioning

The Android app is a public OAuth client. It never contains a client secret. Set the public connector URL at build time with either the Gradle property or environment variable `CONNECTOR_BASE_URL`; production values must be HTTPS.

The app fetches `/.well-known/arrival-alarm-device-oauth` from that gateway, opens the provider authorization endpoint in the system browser, uses Authorization Code + PKCE S256 + state, and receives the callback at:

`com.nazofobi.arrivalalarm://oauth/callback`

Register that redirect URI for `DEVICE_OAUTH_CLIENT_ID`. The provider integration must issue an access token with scope `arrival.device`, a stable user `sub` matching the ChatGPT-side identity, and the app-supplied device identifier in the configured `device_id` claim. The app requests `offline_access` by default so expired device access tokens can be refreshed without storing credentials in the APK.

Required public-client deployment variables are documented in `.env.example`: authorization, token and revocation endpoints, client ID, scopes, and the optional RFC 8707 resource indicator. Access tokens, refresh tokens and the pending PKCE verifier are encrypted with Android Keystore before persistence. Disconnect attempts RFC 7009 revocation before clearing the local session.

A verified HTTPS Android App Link should replace the private-use callback after the final production domain and signing certificate are fixed; until then the package-based callback remains protected by PKCE and transaction-bound state.
