# JARVIS Orchestrator operations and recovery

This runbook applies only to the standalone JARVIS Orchestrator backend. It does not require or modify the Android/application/frontend project.

## Health versus readiness

Use `/health` to answer one question: **is the JARVIS API process alive?** It deliberately does not contact Valkey or Agent Zero. Container/process supervisors should use this endpoint for liveness so a temporary dependency outage does not cause a restart loop.

Use `/ready` to answer: **can JARVIS safely accept work right now?** It checks the configured shared-state backend and, when supported by the selected runtime, the worker. A 503 from `/ready` means traffic should remain connected or retry with backoff, not that the API process itself must be restarted.

When `VALKEY_URL` is configured, Valkey remains authoritative even if it is unavailable during startup. JARVIS does not silently downgrade to process-local memory. The redis-py client reconnects when Valkey returns, so readiness can recover without restarting JARVIS.

## Shared-state outage

Expected behavior while Valkey is unavailable:

- `/health` remains `200` with `state_backend: valkey` and `checks_dependencies: false`.
- `/ready` returns `503` with `State backend unavailable`.
- HTTP command, event-history, reset, and terminate operations return `503 State backend unavailable` rather than leaking Redis/network exceptions.
- `/v1/input` returns a structured `state_backend_unavailable` error with the original `session_id` and `request_id` and `retryable: true`.
- Existing Valkey-backed history, idempotency records, and Agent Zero context mappings are preserved when the same Valkey data volume returns.

Do not switch a packaged phone-primary deployment to in-memory state merely to keep serving commands. Doing so would break shared phone/desktop ordering, retry deduplication, context mapping, and cross-process session ownership guarantees.

### Recovery check

For the packaged Compose prototype:

```bash
curl -fsS http://127.0.0.1:8000/health
curl -i http://127.0.0.1:8000/ready

docker compose ps valkey
docker compose logs --no-color --tail=100 valkey

# After Valkey is healthy again, JARVIS should recover without a restart.
curl -fsS http://127.0.0.1:8000/ready
```

Normal CI explicitly proves the stronger case: Valkey is stopped, JARVIS is restarted while Valkey is still offline, liveness remains available, readiness is 503, Valkey is started again, and the same JARVIS process becomes ready without another restart.

## Worker outage

A worker outage is distinct from a state outage. When Agent Zero is unreachable or returns a server-side failure:

- `/ready` returns `503 Worker runtime unavailable`.
- HTTP command and supported lifecycle operations return `503 Worker runtime unavailable`.
- `/v1/input` returns `code: worker_unavailable` with `retryable: true`.

The client should preserve its logical `request_id` when retrying the same command after a transient outage. Successful results are deduplicated in Valkey, preventing a mobile-network retry from intentionally executing the same logical command twice.

## Client retry guidance

A future phone/desktop client should classify retryable failures before retrying:

1. `state_backend_unavailable`: shared JARVIS state is temporarily unavailable. Keep the same logical `request_id` and retry after readiness recovers.
2. `worker_unavailable`: shared state is healthy enough to respond, but the AI/agent worker is temporarily unavailable. Keep the same logical `request_id` and retry after readiness recovers.
3. `request_id_conflict`: do **not** retry different command text under the same request ID. Generate a new logical request ID.
4. HTTP 410 from cursor-based event recovery: the saved telemetry cursor has aged out of the bounded history window. Rebuild recent telemetry state using the documented recovery contract instead of replaying the command.

This is an application-side interface handoff only; no application code is changed here.

## Evidence maintained in CI

The normal Orchestrator workflow independently verifies:

- Python behavior contracts.
- The packaged standalone Docker + Valkey path.
- Restart-surviving request deduplication and principal isolation.
- Valkey-down startup/liveness/readiness/recovery behavior.
- The Agent Zero adapter, worker-aware readiness, dispatch, and persisted context reuse.

The real Agent Zero -> Ollama -> Qwen3-4B inference proof remains a separately gated workflow because downloading and running the model on every commit would make ordinary CI slow and nondeterministic. A skipped gated job is not counted as live-model evidence.
