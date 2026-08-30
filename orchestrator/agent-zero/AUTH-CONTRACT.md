# Agent Zero external API authentication contract

JARVIS calls Agent Zero's external API using the `X-API-KEY` header. Current upstream Agent Zero validates that value against `settings["mcp_server_token"]`, which is derived from the persistent runtime identity and optional web-auth credentials.

For the packaged JARVIS stack, `bootstrap.py` generates `AGENT_ZERO_RUNTIME_ID` and the matching `AGENT_ZERO_API_KEY`. The Compose `agent-zero-bootstrap` service also persists the same identity as `A0_PERSISTENT_RUNTIME_ID` in Agent Zero's own `/a0/usr/.env` before Agent Zero starts. This matters because Agent Zero loads that file with environment override enabled during startup; relying only on the container process environment can therefore allow a pre-existing/persisted `.env` value to change the token Agent Zero actually accepts.

The persistent identity is intentionally stored in the Agent Zero data volume so restarts preserve the external API contract. The JARVIS-facing API token is a separate credential and is never sent to Agent Zero.
