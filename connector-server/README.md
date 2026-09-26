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

The canonical production target is the free Cloudflare Worker in `src/cloudflare-worker.js`, backed by D1. The Node server remains a portable/local fallback and requires `GATEWAY_SQLITE_PATH` when it is run in production mode.

MCP access tokens are verified against issuer, audience and JWKS. Read tools require `arrival.read`; write tools require `arrival.write`.

Device sync uses the same verifier by default, or separate values via `DEVICE_OAUTH_ISSUER`, `DEVICE_OAUTH_AUDIENCE` and `DEVICE_OAUTH_JWKS_URL`. Device tokens must contain `arrival.device` plus a device-binding claim. `DEVICE_OAUTH_DEVICE_ID_CLAIM` supports namespaced claims such as the Auth0 Free profile.

The free production path uses D1 for durable per-user snapshots, queued commands and receipts. It does not require a paid persistent volume. OAuth configuration and account-specific values remain outside the repository.

Production still requires external account setup and physical acceptance, but G-153 must not introduce a paid hosting dependency without a new explicit user decision.

Do not replace these gates with a hard-coded bearer token, API key in the APK, or anonymous public endpoints.


## Optional portable Node container

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



## Free production target

The supported production target for G-153 is **Cloudflare Workers Free + D1 Free + Auth0 Free**. No Render service or paid persistent disk is part of the target architecture.

See `FREE_INFRASTRUCTURE.md` for account setup, D1 migration, Auth0 scopes/device binding, Worker deployment, free-tier constraints and acceptance steps. The checked-in `wrangler.free.jsonc.example` contains no secret; copy it to the gitignored `wrangler.free.jsonc` for real account values.
