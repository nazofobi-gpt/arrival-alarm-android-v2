# Screen Assist Realtime broker

Server-side token broker for the Android **AI Ekran Asistanı**.

## Security contract

- The standard `OPENAI_API_KEY` exists only in the server environment.
- The endpoint fails closed unless caller authentication is configured.
- The Android client receives only the short-lived Realtime client secret `value` and `expires_at`.
- Responses use `Cache-Control: no-store`; upstream error bodies are not proxied.
- Do **not** put `OPENAI_API_KEY` or a permanent broker caller credential in the Android source, APK, GitHub repository, logs, or screenshots.

The current implementation supports a SHA-256 checked bearer credential through
`SCREEN_ASSIST_CALLER_TOKEN_SHA256`. This is suitable only for controlled/private acceptance when the
actual caller token is provisioned outside source control and stored securely on the device. A public
Play release must replace this private-test credential with per-user/device authentication or attestation
before the broker is exposed as a production service.

## Required environment

- `OPENAI_API_KEY` — server-only project API key.
- `SCREEN_ASSIST_CALLER_TOKEN_SHA256` — lowercase/uppercase hex SHA-256 of the private-test bearer token.
- `OPENAI_REALTIME_MODEL` — optional; defaults to `gpt-realtime-2.1`.
- `OPENAI_SAFETY_IDENTIFIER` — recommended stable privacy-preserving identifier.

## Neon Function

The module exports the Neon Functions `fetch(request)` contract directly and can be deployed on Node.js 24.

Example source deployment:

```bash
neon functions deploy screenassist --src broker/screenassist/index.mjs \
  --env OPENAI_API_KEY="$OPENAI_API_KEY" \
  --env SCREEN_ASSIST_CALLER_TOKEN_SHA256="$SCREEN_ASSIST_CALLER_TOKEN_SHA256" \
  --env OPENAI_SAFETY_IDENTIFIER="$OPENAI_SAFETY_IDENTIFIER"
```

After deployment, configure the Android build with the function HTTPS invocation URL through
`SCREEN_ASSIST_BROKER_URL`. Do not enable capture until the authenticated broker request path succeeds.

## Test

```bash
node --test broker/screenassist/index.test.mjs
```
