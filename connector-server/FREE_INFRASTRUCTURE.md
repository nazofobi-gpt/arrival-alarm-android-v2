# Free production infrastructure

G-153 production hosting is intentionally designed for **$0/month operation** at personal-project scale. The paid Render Blueprint was removed.

## Target architecture

- **Cloudflare Workers Free**: public HTTPS gateway and stateless MCP endpoint.
- **Cloudflare D1 Free**: durable journey snapshots, command queue, idempotency records and receipts.
- **Auth0 Free**: OAuth/OIDC issuer for ChatGPT and Android.
- **Android app**: no API key or client secret. Access/refresh tokens and pending PKCE verifier remain Android-Keystore encrypted.

Current free-tier quotas are external-service limits, not application guarantees. If a free quota is exhausted, the service should fail rather than silently enabling paid capacity.

## Cloudflare setup

From `connector-server/`:

1. Create/sign in to a Cloudflare Free account and authenticate Wrangler.
2. Copy `wrangler.free.jsonc.example` to `wrangler.free.jsonc`.
3. Create the free D1 database:
   `npx wrangler d1 create arrival-alarm-connector`
4. Put the returned database id into the local `wrangler.free.jsonc`.
5. Apply schema:
   `npx wrangler d1 migrations apply arrival-alarm-connector --remote --config wrangler.free.jsonc`
6. After OAuth values below are configured, deploy:
   `npx wrangler deploy --config wrangler.free.jsonc`

The generated `*.workers.dev` URL is already public HTTPS. A paid custom domain is not required.

`wrangler.free.jsonc` is local account configuration and is gitignored. Do not commit tenant-specific values.

## Auth0 Free setup

Create one Auth0 API/resource server:

- Identifier / audience: `https://arrival-alarm-api`
- Scopes: `arrival.read`, `arrival.write`, `arrival.device`
- Allow offline access for the Android refresh-token flow.
- Set the tenant **Default Audience** to `https://arrival-alarm-api` so MCP clients that do not send Auth0's non-standard `audience` parameter still receive API access tokens.

Create an Android **Native** application:

- Grant: Authorization Code + PKCE.
- Refresh Token enabled.
- Callback: `com.nazofobi.arrivalalarm://oauth/callback`.
- No client secret in the APK.

Deploy `auth0/device-binding-action.js` as a Post Login Action and set its Action secret `ARRIVAL_ANDROID_CLIENT_ID` to that Native application's client id. Bind the Action to the Login flow.

The Android authorize request uses `ext-device_id`. The Action copies the current device binding into the access-token claim `https://arrival-alarm.app/device_id`; the gateway verifies that claim before accepting device state/command/receipt traffic. The free MVP intentionally supports one active Android device binding per user.

For ChatGPT, create a separate OAuth application/client when the ChatGPT callback/client-registration details are known. Give it only `arrival.read` and `arrival.write` access to the same API. No Auth0 client secret belongs in Android or this repository.

## Worker variables

Fill the public values in local `wrangler.free.jsonc`:

- `OAUTH_AUTHORIZATION_SERVER`, `OAUTH_ISSUER`, `OAUTH_JWKS_URL`
- `OAUTH_AUDIENCE=https://arrival-alarm-api`
- Android equivalents plus `DEVICE_OAUTH_CLIENT_ID`
- `DEVICE_OAUTH_DEVICE_ID_PARAMETER=ext-device_id`
- `DEVICE_OAUTH_DEVICE_ID_CLAIM=https://arrival-alarm.app/device_id`

For Auth0, `DEVICE_OAUTH_AUDIENCE` is also `https://arrival-alarm-api`. The gateway advertises it to Android, which sends it on the authorization request.

## Verification

Before deploying, CI executes:

- connector contract/unit tests
- D1 idempotency/device-binding tests
- Cloudflare Worker public metadata tests
- `wrangler deploy --dry-run` to prove the Worker bundle builds
- existing Android unit/build and API 36 instrumentation

After deployment:

`CONNECTOR_BASE_URL=https://<worker>.<account>.workers.dev npm run preflight:production`

Then perform the existing physical G-153 acceptance in `PRODUCTION_ACCEPTANCE.md`.

## Free-tier operating constraints

This architecture is for personal/light usage. The application must remain fail-closed when an external free quota is exhausted. Do not add a credit card, paid Worker plan, paid D1 plan, paid Auth0 plan, or another billable service as part of G-153 without a new explicit user decision.
