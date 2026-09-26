# G-153 production acceptance

Run this only against the deployed production/staging connector after OAuth configuration is complete.

For the current G-153 scope, the deployment target is the zero-cost stack in `FREE_INFRASTRUCTURE.md`: Cloudflare Workers Free + D1 Free + Auth0 Free. A passing acceptance run must not depend on a paid Render service, paid disk, or another billable fallback.

## Public preflight

```sh
cd connector-server
CONNECTOR_BASE_URL=https://connector.example.com npm run preflight:production
```

The probe is read-only. It verifies:

- connector health reports `oauth-jwt`
- RFC 9728 protected-resource metadata points at the exact `/mcp` resource
- authorization-server discovery resolves and the issuer matches exactly
- OAuth authorization/token endpoints use HTTPS
- PKCE `S256` is advertised
- Android device-OAuth metadata is present, HTTPS-only and includes `arrival.device`
- the Android redirect URI is exactly `com.nazofobi.arrivalalarm://oauth/callback`

Set `PROBE_REQUIRE_WORKSPACE_OIDC=true` to additionally require `openid`, `email` and a UserInfo endpoint for ChatGPT workspace-domain readiness.

## Authenticated read probe

Provide short-lived test credentials through environment variables or GitHub Actions secrets. Never put tokens in command history, workflow inputs, repository files, or logs.

- `PROBE_USER_BEARER_TOKEN`: lists the real MCP tools and calls the read-only profile/journey/progress/alarm tools. It fails if the tool annotations or OAuth scopes do not match the expected contract.
- `PROBE_DEVICE_BEARER_TOKEN`: calls only `GET /device/commands` to verify the device-bound token without mutating app state.

The manual GitHub Actions workflow `Production Connector Preflight` reads the optional secrets `PRODUCTION_MCP_USER_TOKEN` and `PRODUCTION_DEVICE_TOKEN`. Without those secrets it still performs the full public metadata/OAuth preflight.

## Physical acceptance that remains manual

The automated probe intentionally does **not** queue write tools, forge GPS state, or publish synthetic device snapshots. Final G-153 acceptance still requires a real Android device and ChatGPT:

1. Build/install Android with the real HTTPS `CONNECTOR_BASE_URL`.
2. Complete browser PKCE login and verify encrypted session provisioning.
3. Confirm `get_current_journey`, `get_trip_progress` and `get_alarm_state` answer from live device state.
4. In ChatGPT, configure the app permission mode so writes require the intended user approval and verify the host actually shows that approval before a write call.
5. Approve one allowlisted write and verify command -> gateway -> device -> receipt -> fresh-state readback.
6. Repeat the read/write round trip while an armed journey is continuing in the location foreground service.
7. Exercise access-token refresh and disconnect/revocation.
8. Record the exact deployment version, Android build, device model/API level, tool result, receipt and readback evidence in the canonical project record.

Only after those checks pass should G-153 be marked verified and G-149 unblocked.
