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

# Terminal completion and explicit cancellation stay provider-neutral. Local state clears only after confirmed cancel.
require 'client.getResult(projectId)'
require 'cancelActiveProject'
require 'RemoteGoalClient.Cancellation cancelled = client.cancel(projectId)'
require '"cancelled".equalsIgnoreCase(cancelled.state())'
require 'state.clearProject()'

# Approval remains intentionally unwired until the public contract exposes a real approval_id.
if grep -F 'respondToApproval' "$COORD" >/dev/null || grep -F '/approvals/' "$COORD" >/dev/null; then
  echo "Approval was guessed before a public approval_id handoff" >&2
  exit 1
fi

if grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt' "$COORD"; then
  echo "Provider/worker implementation name leaked into remote continuity" >&2
  exit 1
fi

echo "Remote goal continuity contract GREEN"
