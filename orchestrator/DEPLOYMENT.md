# JARVIS Orchestrator deployment

This directory is a standalone backend workstream. It does not require the Android application project to run.

## What runs

- **JARVIS Orchestrator** — FastAPI service on port `8000`.
- **Valkey** — shared live state, durable event history, Agent Zero context mapping, and cross-process session locks.
- **Agent Zero** — optional MIT-licensed worker container exposed on host port `5080` when the `agent-zero` Compose profile is enabled.

Agent Zero is intentionally an adapter behind JARVIS rather than the public client protocol. Phone and desktop clients talk only to the JARVIS Orchestrator.

## 1. Start the core without Agent Zero

This is useful for health checks and client integration while the worker is not configured.

```bash
cd orchestrator
export JARVIS_API_TOKEN='replace-with-a-long-random-token'
docker compose up -d --build
curl http://localhost:8000/health
```

The default runtime is `echo`, so no paid AI provider is required just to run and verify the JARVIS transport/state layer.

## 2. Start the bundled Agent Zero worker

Agent Zero's official Docker image exposes its Web UI on container port 80. The optional Compose profile maps it to host port `5080` and persists `/a0/usr` in a named volume.

```bash
cd orchestrator
docker compose --profile agent-zero up -d --build
```

Open `http://SERVER_IP:5080`, complete Agent Zero onboarding, and configure the model provider you want Agent Zero to use. For a no-subscription commercial baseline, this provider should ultimately be a self-hosted/open model; proprietary providers remain optional.

In Agent Zero, obtain the External API token from **Settings > External Services**. Agent Zero's external API uses that token in the `X-API-KEY` header.

## 3. Enable the real Agent Zero runtime

Create an untracked `.env` file in `orchestrator/` or export these variables in the server environment:

```dotenv
JARVIS_API_TOKEN=replace-with-a-long-random-token
JARVIS_RUNTIME=agent-zero
AGENT_ZERO_URL=http://agent-zero
AGENT_ZERO_API_KEY=replace-with-agent-zero-external-api-token
AGENT_ZERO_LIFETIME_HOURS=24
# Optional: activate a named Agent Zero project on the first message
AGENT_ZERO_PROJECT=
```

Then restart the orchestrator with the Agent Zero profile enabled:

```bash
docker compose --profile agent-zero up -d --build
```

Verify:

```bash
curl http://localhost:8000/health
curl -X POST http://localhost:8000/v1/command \
  -H "Authorization: Bearer $JARVIS_API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"session_id":"primary","text":"Reply with the words orchestrator online."}'
```

The health response should report `runtime: agent-zero`, `state_backend: valkey`, and `session_locking: valkey`.

## Session behavior

JARVIS owns the stable session identifier used by phone and desktop clients. Valkey maps it to Agent Zero's temporary `context_id`. If an Agent Zero context expires, JARVIS removes the stale mapping and retries that request once as a fresh Agent Zero context. Other HTTP failures are not silently retried.

Same-session work is serialized with a Valkey distributed lock, so multiple FastAPI processes or replicas cannot concurrently mutate the same conversation. Different JARVIS sessions can still run in parallel.

## Persistence

Docker volumes:

- `valkey-data` — JARVIS event/session infrastructure.
- `agent-zero-data` — Agent Zero `/a0/usr` state.

Back up both before moving servers or destructive upgrades.

## Network exposure

For initial testing, bind the server only behind a private VPN/mesh or firewall. Do not expose Valkey directly to the public internet. The only client-facing JARVIS service should be the authenticated FastAPI/WebSocket endpoint. Agent Zero's Web UI should also remain private or separately protected.
