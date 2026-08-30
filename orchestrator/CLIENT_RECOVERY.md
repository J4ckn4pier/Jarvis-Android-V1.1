# JARVIS client reconnect/recovery contract

This document is the application-side handoff for future phone and desktop clients. It defines the JARVIS Orchestrator interface only; the Orchestrator workstream does not modify Android, application, UI, or frontend code.

## Stable event identity

Every telemetry event returned by session history and every live telemetry event sent over the WebSocket has the same stable `event_id`. Clients must treat that value as opaque and persist the latest event they have durably processed for each public JARVIS session.

## Normal reconnect

1. Re-open the authenticated `/v1/events` WebSocket for the public session.
2. Request `GET /v1/sessions/{session_id}/events?after_event_id={last_event_id}&limit=100`.
3. Merge replayed history and live WebSocket events by `event_id`; discard duplicates.
4. Process events in chronological order and persist `next_event_id`.
5. Repeat the history request with `next_event_id` while `has_more` is `true`.
6. Continue from the live WebSocket once caught up.

Opening the live stream before replay intentionally permits overlap. Stable `event_id` deduplication makes overlap safe and avoids a gap between history retrieval and live subscription.

## Expired or stale cursor

Valkey telemetry history is deliberately bounded and expires. A device may therefore reconnect with a remembered `after_event_id` that is no longer present in retained history.

When that happens, the Orchestrator returns:

```text
HTTP 410 Gone
{"detail":"Recovery cursor is no longer available"}
```

A client receiving this response must **not** assume it is caught up and must **not** retry the AI command that originally produced the missing telemetry. Instead it should:

1. Discard the stale telemetry cursor for that session.
2. Request the ordinary no-cursor history endpoint: `GET /v1/sessions/{session_id}/events`.
3. Rebuild its transient telemetry/activity presentation from the returned recent snapshot.
4. Persist the newest returned `event_id`, if any.
5. Continue consuming the already-open live WebSocket and deduplicate by `event_id`.

The `410` response is a telemetry recovery signal, not an authentication failure and not evidence that the JARVIS conversation itself was deleted. Conversation/worker state and command retry state have separate lifecycle contracts.

## Temporary worker outage

A temporary Agent Zero transport failure or server-side `5xx` is exposed as a stable JARVIS worker-unavailable condition rather than as a raw upstream exception.

For REST command submission the contract is:

```text
HTTP 503 Service Unavailable
{"detail":"Worker runtime unavailable"}
```

For `/v1/input`, the WebSocket remains open and the command receives:

```json
{
  "error": "Worker runtime unavailable",
  "code": "worker_unavailable",
  "request_id": "the-client-request-id",
  "session_id": "the-public-session-id",
  "retryable": true
}
```

A client may retry the same logical command after the worker becomes available, but it must reuse the same `request_id`. That preserves JARVIS's duplicate-command protection if the original worker call actually completed near a network failure boundary. Do not generate a new request ID merely because a `worker_unavailable` response was received.

The WebSocket is deliberately kept alive for this condition. A worker outage is not an authentication failure and does not mean the phone/desktop has lost its JARVIS session.

## Command retry is separate

Network retries of user commands use `request_id`, not `event_id`. Reuse one stable `request_id` only for retries of the same logical command. JARVIS stores successful results in shared Valkey state so a phone retry does not execute the same worker action twice.

Do not use telemetry `event_id` as a command `request_id`, and do not resubmit a command merely because telemetry recovery returned `410`.

## Ownership

All session history and recovery cursors are evaluated inside the authenticated principal's scoped session. Two users may both have a public session named `primary` without sharing telemetry or cursor validity.
