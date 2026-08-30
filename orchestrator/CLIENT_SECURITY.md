# JARVIS client security handoff

This is an Orchestrator-side contract for future phone and desktop clients. It does not modify Android, application, UI, or frontend code.

## Preferred authentication

For ordinary HTTP endpoints, send the JARVIS credential as:

```text
Authorization: Bearer <token>
```

For both Orchestrator WebSockets (`/v1/events` and `/v1/input`), native phone/desktop clients should use the same `Authorization: Bearer <token>` header during the WebSocket handshake.

The Orchestrator still accepts the legacy `?token=<token>` WebSocket query parameter for compatibility. New native integrations should not use query-token authentication when they can set headers, because full URLs are more likely than headers to be copied into access logs, diagnostics, browser history, crash reports, or support bundles.

If both an Authorization header and a query token are supplied, the Authorization header takes precedence. This prevents a bad or revoked header credential from silently falling back to a different URL credential.

## Transport requirements

The packaged Orchestrator binds to localhost by default. If it is later exposed across a LAN, VPN, reverse proxy, or public network, terminate TLS and use `https://` / `wss://`; do not send JARVIS credentials over plaintext remote transport.

A public `session_id` is routing information, not a secret. Authentication still scopes that public name to the authenticated principal internally, so different users may safely use the same public session name without sharing state.

## Credential handling

- Do not hard-code production credentials in client source code or repository files.
- Store device credentials in the platform's protected credential storage when application implementation begins.
- Do not include credentials in telemetry, error messages, command payloads, or persisted conversation history.
- Rotate a credential if it is exposed in logs or support artifacts.

## Reconnect interaction

Authentication failure and event-history recovery are separate conditions. WebSocket close code `4401` means authentication failed. HTTP `410 Gone` from event history means the remembered event cursor is no longer retained. A `410` must not cause credential rotation or command replay; follow `CLIENT_RECOVERY.md` instead.
