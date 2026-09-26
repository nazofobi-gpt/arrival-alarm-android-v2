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

## Production security gate

Production mode intentionally fails closed until these are supplied by a production integration layer:

1. OAuth 2.1 user identity for the MCP endpoint.
2. Device-bound authentication for Android state/command sync.
3. Durable per-user state, command and receipt storage.
4. HTTPS deployment and secret storage outside the repository.
5. Revoke/disconnect handling.

Do not replace these gates with a hard-coded bearer token, API key in the APK, or anonymous public endpoints.
