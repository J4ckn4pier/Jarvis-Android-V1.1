# APK INTEGRATION CONTRACT GREEN

This document is the stable provider-neutral boundary between the Android APK and the JARVIS Orchestrator management plane. The APK talks to **JARVIS**, never directly to Agent Zero, Ollama, or any future worker/provider. Worker and provider details are never part of the APK contract.

## Authentication

Every HTTP request uses the existing owner identity boundary:

```http
Authorization: Bearer <token>
```

The Bearer scheme is case-insensitive. The credential itself is exact and must not be trimmed or normalized.

## 1. Submit a goal

`POST /v1/goals`

Request JSON:

```json
{
  "goal": "Plan a birthday dinner and verify the recommendation",
  "session_id": "primary",
  "constraints": ["under $150", "within 20 minutes"],
  "acceptance_criteria": ["recommendation independently verified"],
  "deadline": "2026-08-31T01:00:00Z"
}
```

Fields:
- `goal`: required non-empty natural-language goal.
- `session_id`: shared phone/desktop session identifier; defaults to `primary`.
- `constraints`: zero or more user constraints.
- `acceptance_criteria`: zero or more conditions that trusted verification must satisfy before completion.
- `deadline`: optional ISO-8601 timestamp.

Success response JSON:

```json
{
  "project_id": "project-123",
  "session_id": "primary",
  "state": "active",
  "goal": "Plan a birthday dinner and verify the recommendation",
  "provider_details_exposed": false
}
```

The APK persists `project_id` and may disconnect immediately after submission.

## 2. Read project status

`GET /v1/projects/{project_id}`

Success response JSON:

```json
{
  "project_id": "project-123",
  "session_id": "primary",
  "goal": "Plan a birthday dinner and verify the recommendation",
  "state": "active",
  "task_count": 4,
  "task_states": {
    "complete": 2,
    "running": 1,
    "pending": 1
  },
  "last_progress_at": "2026-08-30T20:00:00+00:00",
  "provider_details_exposed": false
}
```

`state` is a JARVIS project lifecycle value: `pending`, `active`, `blocked`, `verifying`, `complete`, `failed`, or `cancelled`.

## 3. Read/reconnect project events

`GET /v1/projects/{project_id}/events`

Optional query parameters:
- `after_event_id`: resume strictly after the last event the APK successfully processed.
- `limit`: 1-1000; defaults to 100.

Response JSON:

```json
{
  "project_id": "project-123",
  "events": [
    {
      "event_id": "000000000007",
      "project_id": "project-123",
      "kind": "task.complete",
      "task_id": "task-2",
      "timestamp": "2026-08-30T20:00:00+00:00"
    }
  ],
  "next_event_id": "000000000007",
  "has_more": false
}
```

APK reconnect rule: store `next_event_id` after successfully consuming a page and send it back as `after_event_id` on the next request. Event IDs are opaque cursors to the APK; do not parse or generate them client-side.

Current management event `kind` values include project compilation/activation, task compilation/running/completion/reassignment/blocking/cancellation, project verification/completion, approval decisions, and project cancellation. The APK must tolerate additional `kind` values so the brain graph can grow without replacing the client contract.

## 4. Respond to an approval request

`POST /v1/projects/{project_id}/approvals/{approval_id}`

Request JSON:

```json
{
  "approved": true,
  "response": "Proceed"
}
```

Response JSON:

```json
{
  "project_id": "project-123",
  "approval_id": "approval-9",
  "approved": true,
  "response": "Proceed"
}
```

`response` is optional and may be `null`.

## 5. Cancel a project

`POST /v1/projects/{project_id}/cancel`

Response JSON:

```json
{
  "project_id": "project-123",
  "state": "cancelled"
}
```

Cancellation is durable and prevents future managed dispatch for unfinished work.

## 6. Read the synthesized result

`GET /v1/projects/{project_id}/result`

Response JSON:

```json
{
  "project_id": "project-123",
  "state": "complete",
  "result": "JARVIS synthesized result text",
  "provider_details_exposed": false
}
```

The APK displays/speaks `result` as JARVIS output. It must not depend on which worker produced any underlying task output.

## Shared client rules

- All identifiers (`project_id`, `session_id`, `approval_id`, `after_event_id`) are opaque exact strings. Do not trim or normalize them.
- A phone and desktop may use the same `session_id`, but authentication ownership still isolates different users.
- Long-running work is server-owned after submission. Client disconnect does not cancel the project.
- On reconnect, query status and resume events from the last persisted `after_event_id`; fetch `/result` when `state` is `complete`.
- Treat `blocked` as a JARVIS-managed state that may later recover or require an approval/user response; do not infer a provider failure.
- Never surface or require provider/worker IDs. `provider_details_exposed` must remain `false` on public management responses that include it.

## Proven contract boundary

M9 proved the authenticated APK-facing endpoint boundary without provider leakage. M10 proved the deterministic management path across at least three logical worker roles, parallel and dependent work, induced stall/reassignment, independent verification, persisted completion, backend restart, and event-cursor reconnect. This contract is the handoff boundary for APK integration; Android/application implementation remains a separate workstream.
