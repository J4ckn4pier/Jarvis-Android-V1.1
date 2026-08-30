# JARVIS Orchestrator deployment

This directory is a standalone backend workstream. It does not require the Android/application project to run.

## What runs

- **JARVIS Orchestrator** — FastAPI service and the only client-facing backend protocol.
- **Valkey** — shared phone/desktop state, durable event history, Agent Zero context mapping, cross-process session locks, and retry-result storage.
- **Agent Zero** — optional MIT-licensed agent worker behind the JARVIS adapter.
- **Ollama** — optional MIT-licensed local model server.
- **Qwen3-4B** — recommended local baseline model; upstream weights are Apache-2.0 licensed.

Agent Zero and Ollama are implementation details behind JARVIS. Phone/desktop clients do not speak their private APIs directly.

All published prototype ports bind to `127.0.0.1` by default. Valkey is not published to the host at all. Remote/mobile exposure belongs behind an intentional authenticated TLS/private-network entry point later; do not make the internal services public merely to test the prototype.

## Fastest full local prototype

From this directory, the packaged full-stack path is now one command:

```bash
sh start-local.sh
```

The launcher performs the previously manual setup steps in order:

1. `bootstrap.py` creates an untracked `.env` with a long random JARVIS API token, a unique Agent Zero persistent runtime identity, the matching Agent Zero external API key, and the real Agent Zero runtime defaults. Existing values are preserved rather than rotated. The file is forced to owner-only permissions (`0600`) on POSIX hosts.
2. Ollama starts locally.
3. The `ollama-model-bootstrap` job waits for Ollama and downloads `qwen3:4b` into the persistent model volume. Re-running the launcher is safe; Ollama reuses the existing model.
4. Agent Zero receives its official `tiny-local` profile through the supported `A0_SET_AGENT_PROFILE` setting.
5. A packaged Agent Zero model preset points chat/utility inference at `http://ollama:11434` using `qwen3:4b`. It is copied only when Agent Zero has no existing `_model_config/presets.yaml`, so later user configuration is not overwritten.
6. Valkey, Agent Zero, Ollama, and JARVIS start together.
7. The launcher waits until JARVIS `/ready` reports success.

The first run can take significantly longer because Qwen3-4B and container images must be downloaded. Subsequent starts reuse the Docker volumes.

When ready, the launcher prints the JARVIS readiness response. To send a command manually:

```bash
set -a
. ./.env
set +a
curl -X POST http://127.0.0.1:8000/v1/command \
  -H "Authorization: Bearer $JARVIS_API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"session_id":"primary","request_id":"manual-1","text":"Reply with the words orchestrator online."}'
```

## Core-only transport/state prototype

The core can still run without an AI worker. This uses the deterministic echo runtime and is useful for testing client integration, authentication, synchronized state, recovery, and telemetry without downloading a model:

```bash
export JARVIS_API_TOKEN='replace-with-a-long-random-token'
docker compose up -d --build
curl http://127.0.0.1:8000/health
curl http://127.0.0.1:8000/ready
```

`/health` means the API process exists. `/ready` is stronger: it verifies Valkey and, for runtimes exposing a readiness probe, the worker as well.

## Manual/advanced configuration

The bootstrap is deliberately non-destructive. If `.env` already contains a supported value, it leaves that value alone. Advanced deployments can therefore override the generated defaults:

```dotenv
JARVIS_API_TOKEN=replace-with-your-secret
JARVIS_RUNTIME=agent-zero
AGENT_ZERO_RUNTIME_ID=replace-with-persistent-runtime-id
AGENT_ZERO_API_KEY=replace-with-matching-external-api-token
AGENT_ZERO_URL=http://agent-zero
AGENT_ZERO_LIFETIME_HOURS=24
# Optional named Agent Zero project for first-message context creation
AGENT_ZERO_PROJECT=
```

Packaged Docker sets `JARVIS_REQUIRE_AUTH=1` by default. A missing JARVIS credential therefore prevents startup instead of silently exposing an unauthenticated backend. Zero-configuration open mode remains only for explicit developer/test use outside that packaged requirement.

### Multi-user authentication and session ownership

`JARVIS_API_TOKEN` is the simple single-owner deployment mode. For a deployment serving more than one person, use `JARVIS_API_KEYS_JSON` instead. It maps a stable principal ID to that principal's bearer token:

```dotenv
JARVIS_API_KEYS_JSON={"alice":"replace-with-long-random-token-a","bob":"replace-with-long-random-token-b"}
```

When `JARVIS_API_KEYS_JSON` is present it is authoritative; do not also rely on `JARVIS_API_TOKEN` as a fallback. The Orchestrator deliberately fails startup rather than guessing when the multi-user identity map is unsafe or malformed. The value must be valid JSON, must be a JSON object, and must contain at least one principal. Principal IDs must be non-empty strings with no leading or trailing whitespace. Tokens must be non-empty strings and must be unique across principals; one bearer token may never identify two owners.

Principal IDs are durable ownership identifiers, not display names. Keep them stable across redeployments. Changing a principal ID changes the namespace used for that person's sessions even if the bearer token stays the same. Phone and desktop clients belonging to the same person should therefore authenticate as the same principal and may use the same public session ID (for example `primary`) to share JARVIS state. Different principals may safely use the same public session ID because the Orchestrator scopes storage, event history, locks, retry records, and worker context by authenticated owner.

Treat bearer tokens as secrets: generate high-entropy random values, keep `.env` out of source control, rotate a token if it is exposed, and expose the JARVIS API remotely only through TLS or a private authenticated network. WebSocket clients should prefer `Authorization: Bearer …`; query-string `?token=` authentication remains only as a compatibility fallback because URLs are more likely to appear in logs and diagnostics.

Agent Zero's external token is not a made-up JARVIS credential. On a fresh packaged stack, `bootstrap.py` reproduces Agent Zero's current upstream token derivation from `A0_PERSISTENT_RUNTIME_ID` with blank optional login/password fields, then gives the same runtime ID to the Agent Zero container. If a deployment intentionally enables different Agent Zero login/password settings, provide the corresponding external API token instead of relying on the fresh-stack derivation.

The full Compose stack can also be managed directly:

```bash
docker compose --profile agent-zero --profile local-ai up -d --build
docker compose --profile agent-zero --profile local-ai ps
```

Agent Zero's Web UI is available only on host loopback `http://127.0.0.1:5080`. Ollama is available only on host loopback `http://127.0.0.1:11434`. JARVIS is available only on host loopback `http://127.0.0.1:8000`.

## Session behavior

JARVIS owns the stable public session identifier shared by phone/desktop clients. It scopes that identifier to the authenticated principal before using internal state, so two users may both call a session `primary` without sharing history, telemetry, locks, worker context, or retry records.

Valkey maps the scoped JARVIS session to Agent Zero's temporary `context_id`. If an Agent Zero context expires, JARVIS removes the stale mapping and retries the request once as a new Agent Zero context. Other HTTP failures are not blindly retried because an agent operation may have side effects.

Same-session work is serialized with a Valkey distributed lock. Different sessions can still run concurrently.

### Phone/client retry contract

Phone and desktop clients may include an optional `request_id` (1–128 characters) on `/v1/command` and `/v1/input`. The client should generate one stable unique ID for each logical user command and reuse that same ID if a connection timeout makes it necessary to retry that command.

JARVIS stores successful request results in Valkey for 24 hours. A retry with the same authenticated user, session, `request_id`, and command text returns the original `task_id` and response without executing the worker again. This prevents common mobile-network retries from duplicating real-world side effects. The same `request_id` may safely be used in a different session because records are session-scoped.

If a client reuses an existing `request_id` with different command text, HTTP returns `409 Conflict`; the WebSocket path returns `code: request_id_conflict`. Clients should generate a new request ID rather than retrying a different command under the old ID.

This is an application-side interface contract only. No Android/application files are modified by the Orchestrator workstream.

Lifecycle controls remain JARVIS-facing rather than exposing Agent Zero internals:

```bash
curl -X POST http://127.0.0.1:8000/v1/sessions/primary/reset \
  -H "Authorization: Bearer $JARVIS_API_TOKEN"

curl -X DELETE http://127.0.0.1:8000/v1/sessions/primary \
  -H "Authorization: Bearer $JARVIS_API_TOKEN"
```

### Reconnect and telemetry recovery contract

Every history event and live telemetry event carries the same stable `event_id`, formatted internally from the task and event sequence. Clients should treat `event_id` as opaque and persist the most recently processed value for each public JARVIS session.

The existing no-cursor history call remains backward compatible and returns the most recent page:

```bash
curl http://127.0.0.1:8000/v1/sessions/primary/events \
  -H "Authorization: Bearer $JARVIS_API_TOKEN"
```

A reconnecting phone or desktop that already has a last-seen event should recover **forward** from that event instead of asking only for the latest page:

```bash
curl 'http://127.0.0.1:8000/v1/sessions/primary/events?after_event_id=LAST_EVENT_ID&limit=100' \
  -H "Authorization: Bearer $JARVIS_API_TOKEN"
```

Cursor recovery returns events in chronological order plus `next_event_id` and `has_more`. The client should process/dedupe each event by `event_id`, persist `next_event_id`, and repeat the request with that value while `has_more` is true. The backend reads from the bounded durable Valkey event window, so this catches up through more than one API page after a longer mobile disconnect without replaying the AI command itself.

A safe reconnect sequence for a future application client is:

1. Re-establish the authenticated live telemetry WebSocket.
2. Request history with `after_event_id` equal to the last event the client had durably processed before disconnecting.
3. Merge replayed and live events by stable `event_id`; duplicates are harmless and should be discarded client-side.
4. Continue paging with `next_event_id` while `has_more` is true.
5. Keep the latest processed `event_id` as the next reconnect cursor.

This is deliberately an application-side contract only; this workstream does not edit Android/application/frontend code.

Live telemetry requires an explicit session subscription:

```text
ws://127.0.0.1:8000/v1/events?session_id=primary&token=YOUR_TOKEN
```

## Automated evidence

Normal Orchestrator CI has three independent layers:

1. Python behavior tests covering routing, validation, principal/session isolation, recovery/history, distributed locking, command retry deduplication, Agent Zero protocol behavior, lifecycle operations, readiness, bootstrap behavior, and packaging contracts.
2. A Docker standalone smoke test that boots the packaged Orchestrator + Valkey path and sends real HTTP commands through two distinct principals using the same public session name.
3. An Agent Zero adapter Docker harness that uses the real `AgentZeroRuntime` and Valkey mapping against a deterministic Agent Zero wire-protocol service. It proves Docker networking, worker-aware readiness, HTTP dispatch, and persisted Agent Zero context reuse without downloading a multi-gigabyte model on every commit.

A separate gated workflow, **Orchestrator Live Local AI Proof**, exists specifically for expensive full-stack verification. It is not triggered for normal commits. When deliberately triggered, it runs `start-local.sh`, downloads the real Qwen3-4B model, boots the official Agent Zero and Ollama containers, and sends an authenticated JARVIS command that must produce a non-echo model response. This keeps everyday CI deterministic while retaining a real end-to-end inference proof path. Maintainers may deliberately request the same proof from a branch push by including `[live-ai]` in that commit message.

## Persistence

Docker volumes:

- `valkey-data` — JARVIS event/session infrastructure, locks, and 24-hour command retry results.
- `agent-zero-data` — Agent Zero `/a0/usr` state and model presets.
- `ollama-data` — downloaded local model files.

Back up the stateful volumes before destructive upgrades or moving a deployment. Ollama model files can be downloaded again if needed.

## Network exposure

The development package intentionally exposes JARVIS, Agent Zero, and Ollama only on `127.0.0.1`; Valkey is internal-only. Do not expose Valkey, Agent Zero's UI/API, or Ollama directly to the public internet.

For a later phone-primary deployment, keep those services private and expose only the authenticated JARVIS HTTP/WebSocket interface behind TLS or a private network layer. That is a deployment concern, not an Android/application code change.
