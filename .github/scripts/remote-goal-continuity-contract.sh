#!/bin/sh
set -eu

COORD="android/app/src/main/java/com/jarvis/mobile/remote/RemoteGoalCoordinator.java"

[ -f "$COORD" ] || { echo "RED: missing $COORD" >&2; exit 1; }

require() {
  needle="$1"
  grep -F "$needle" "$COORD" >/dev/null || { echo "Missing continuity contract token '$needle' in $COORD" >&2; exit 1; }
}

# Reconnect must use the exact durable public project/cursor state and safely no-op when nothing is active.
require 'resumeActiveProject'
require 'state.load()'
require 'saved.hasProject()'
require 'saved.projectId()'
require 'saved.eventId()'
require 'client.getProject(projectId)'
require 'client.getEvents(projectId, saved.eventId())'
require 'page.nextEventId()'
require 'state.saveCursor(projectId, page.nextEventId())'

# A server-expired recovery cursor is an explicit resync instruction, not a permanent reconnect failure.
# Android must forget only the stale cursor, preserve the project, and retry the event stream from current history.
require 'expired.statusCode() != 410'
require 'state.saveCursor(projectId, null)'
require 'client.getEvents(projectId, null)'

# A public 404 means the saved active project is no longer available to this credential. Forget only the
# phone-side active-project bookmark so every future app launch does not repeat the same dead reconnect.
require 'missing.statusCode() != 404'
require 'state.clearProject()'

# Terminal completion and explicit cancellation stay provider-neutral. Local state clears only after confirmed cancel.
require 'client.getResult(projectId)'
require 'cancelActiveProject'
require 'RemoteGoalClient.Cancellation cancelled = client.cancel(projectId)'
require '"cancelled".equalsIgnoreCase(cancelled.state())'
require 'state.clearProject()'

# Post-handoff approval must discover exactly one server-issued approval and return its opaque ID; never infer from task_id.
require 'respondToActiveApproval'
require 'client.getProject(projectId)'
require 'project.pendingApprovals().size() != 1'
require 'RemoteGoalClient.PendingApproval pending = project.pendingApprovals().get(0)'
require 'pending.approvalId()'
require 'client.respondToApproval(projectId, pending.approvalId(), approved, response)'

if grep -Eiq 'taskId\(\).*respondToApproval|respondToApproval\([^,]+,[[:space:]]*[^,]*task' "$COORD"; then
  echo "task_id was substituted for approval_id" >&2
  exit 1
fi
if grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt' "$COORD"; then
  echo "Provider/worker implementation name leaked into remote continuity" >&2
  exit 1
fi

echo "Remote goal continuity contract GREEN"
