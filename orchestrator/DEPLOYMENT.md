# JARVIS Orchestrator deployment

This directory is a standalone backend workstream. It does not require the Android application project to run.

## What runs

- **JARVIS Orchestrator** — FastAPI service on host loopback port `8000`.
- **Valkey** — shared live state, durable event history, Agent Zero context mapping, and cross-process session locks. It is not published to the host network.
- **Agent Zero** — optional MIT-licensed worker container on host loopback port `5080` when the `agent-zero` Compose profile is enabled.
- **Ollama** — optional MIT-licensed local model server on host loopback port `11434` when the `local-ai` Compose profile is enabled.
- **Qwen3-4B** — recommended baseline local model. The upstream Qwen3-4B weights are Apache-2.0 licensed and Ollama provides a `qwen3:4b` package.

Agent Zero is intentionally an adapter behind JARVIS rather than the public client protocol. Phone and desktop clients talk only to the JARVIS Orchestrator.

All published prototype ports bind to `127.0.0.1` by default. This deliberately prevents a zero-configuration development instance from becoming reachable by other LAN/Wi-Fi devices. Remote/mobile exposure belongs behind an intentional authenticated reverse proxy or private network layer rather than changing these safe defaults.

## 1. Start the core without Agent Zero

This is useful for health checks and client integration while the worker is not configured.

```bash
cd orchestrator
export JARVIS_API_TOKEN='replace-with-a-long-random-token'
docker compose up -d --build
curl http://localhost:8000/health
curl http://localhost:8000/ready
```

The default runtime is `echo`, so no paid AI provider is required just to run and verify the JARVIS transport/state layer.

`/health` is a cheap liveness check: it answers when the API process exists. `/ready` is the stronger check used before sending real work: it verifies Valkey and, when the selected runtime exposes a readiness probe, that worker as well.

## 2. Start the bundled Agent Zero worker

Agent Zero's official Docker image exposes its Web UI on container port 80. The optional Compose profile maps it to host loopback port `5080` and persists `/a0/usr` in a named volume.

```bash
cd orchestrator
docker compose --profile agent-zero up -d --build
```

On the machine running Docker, open `http://localhost:5080` and complete Agent Zero onboarding. If the host is remote, use an SSH tunnel/private network rather than changing the Compose binding to a public interface during prototype development.

In Agent Zero, obtain the External API token from **Settings > External Services**. Agent Zero's external API uses that token in the `X-API-KEY` header.

## 3. Add the free local AI model stack

The recommended no-subscription baseline is **Ollama + Qwen3-4B**.

Plain English:

- Ollama is the program that serves an AI model on your own machine/server.
- Qwen3-4B is the actual AI model.
- Agent Zero connects to Ollama instead of requiring OpenAI, Anthropic, or another paid API.

Start Agent Zero and Ollama together:

```bash
cd orchestrator
docker compose --profile agent-zero --profile local-ai up -d --build
```

Download the recommended model into the persistent Ollama volume:

```bash
docker compose --profile local-ai exec ollama ollama pull qwen3:4b
```

Confirm Ollama sees it:

```bash
docker compose --profile local-ai exec ollama ollama list
```

Then open Agent Zero at `http://localhost:5080` and configure its chat/utility model provider for Ollama using:

```text
Base URL: http://ollama:11434
Model: qwen3:4b
```

For this smaller local model, select Agent Zero's bundled **Tiny Local** profile. Agent Zero's own local-model guide recommends that profile for Ollama/Qwen-class small models because it keeps the normal tool-call mechanism but gives the model a simpler execution contract.

If the host has substantially more RAM and Qwen3-4B is not strong enough, `qwen3:8b` is the next straightforward step. Do not make a proprietary model mandatory; Claude/OpenAI/Gemini can remain optional provider adapters.

## 4. Enable the real Agent Zero runtime

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

Then restart the orchestrator with Agent Zero and, if desired, the local AI profile enabled:

```bash
docker compose --profile agent-zero --profile local-ai up -d --build
```

Verify readiness before sending a command:

```bash
curl http://localhost:8000/health
curl http://localhost:8000/ready
curl -X POST http://localhost:8000/v1/command \
  -H "Authorization: Bearer $JARVIS_API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"session_id":"primary","text":"Reply with the words orchestrator online."}'
```

The readiness response should report `runtime: agent-zero`, `state_backend: valkey`, and `session_locking: valkey`. If Agent Zero itself cannot be reached, `/ready` returns HTTP `503` instead of falsely reporting the system ready.

## Session behavior

JARVIS owns the stable session identifier used by phone and desktop clients. Valkey maps it to Agent Zero's temporary `context_id`. If an Agent Zero context expires, JARVIS removes the stale mapping and retries that request once as a fresh Agent Zero context. Other HTTP failures are not silently retried because blindly retrying an agent operation could duplicate side effects.

Same-session work is serialized with a Valkey distributed lock, so multiple FastAPI processes or replicas cannot concurrently mutate the same conversation. Different JARVIS sessions can still run in parallel.

Session lifecycle controls are exposed through JARVIS rather than Agent Zero-specific client routes:

```bash
# Clear the worker conversation while preserving the JARVIS session identity.
curl -X POST http://localhost:8000/v1/sessions/primary/reset \
  -H "Authorization: Bearer $JARVIS_API_TOKEN"

# Terminate the worker context and clear its JARVIS mapping.
curl -X DELETE http://localhost:8000/v1/sessions/primary \
  -H "Authorization: Bearer $JARVIS_API_TOKEN"
```

Live event WebSocket clients must subscribe to an explicit session so telemetry from unrelated sessions is not broadcast to them:

```text
ws://localhost:8000/v1/events?session_id=primary&token=YOUR_TOKEN
```

## Automated prototype evidence

The orchestrator CI has three independent layers:

1. Python behavior tests for routing, identity/session isolation, Valkey locking/history behavior, Agent Zero API behavior, lifecycle operations, and readiness.
2. A Docker standalone smoke test that boots the packaged Orchestrator + Valkey stack and sends real HTTP commands through it.
3. An Agent Zero adapter Docker harness (`compose.ci-agent-zero.yaml`) that places the real `AgentZeroRuntime` behind the packaged Orchestrator and a deterministic wire-protocol stub. It proves Docker networking, worker-aware readiness, HTTP dispatch, Valkey-backed Agent Zero context persistence, and reuse across consecutive turns without requiring a paid model or multi-gigabyte model download on every CI run.

The CI stub is test infrastructure only; it is not part of the mandatory product runtime and is never selected by the normal Compose file.

## Persistence

Docker volumes:

- `valkey-data` — JARVIS event/session infrastructure.
- `agent-zero-data` — Agent Zero `/a0/usr` state.
- `ollama-data` — downloaded local model files.

Back up the state volumes before moving servers or destructive upgrades. Ollama model files can also be downloaded again if needed.

## Network exposure

The packaged prototype intentionally publishes JARVIS, Agent Zero, and Ollama only on `127.0.0.1`. Valkey is not published at all. Do not expose Valkey directly to the public internet.

For a later remote/mobile deployment, keep these internal services private and put only the authenticated JARVIS HTTP/WebSocket interface behind a deliberate TLS reverse proxy/private-network entry point. Agent Zero's Web UI and Ollama should remain private or separately protected.
