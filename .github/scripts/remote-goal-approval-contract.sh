#!/bin/sh
set -eu

CLIENT="android/app/src/main/java/com/jarvis/mobile/remote/RemoteGoalClient.java"
COORDINATOR="android/app/src/main/java/com/jarvis/mobile/remote/RemoteGoalCoordinator.java"
RUNTIME="android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"
MAIN="android/app/src/main/java/com/jarvis/mobile/MainActivity.java"

require() {
  needle="$1"
  file="$2"
  grep -F "$needle" "$file" >/dev/null || { echo "Missing remote approval token '$needle' in $file" >&2; exit 1; }
}

# Android must discover the opaque backend-issued approval_id; task_id is never an approval substitute.
require 'pending_approvals' "$CLIENT"
require 'approval_id' "$CLIENT"
require 'PendingApproval' "$CLIENT"
require 'approvalId' "$CLIENT"
require 'pendingApprovals' "$CLIENT"

# The reconnect coordinator owns exact-ID approval/decline and refuses ambiguity rather than guessing.
require 'respondToActiveApproval' "$COORDINATOR"
require 'pendingApprovals().size() != 1' "$COORDINATOR"
require 'respondToApproval' "$COORDINATOR"

# Normal JARVIS UI presents and acts on remote approval/cancel without exposing backend/provider plumbing.
require 'AWAITING_APPROVAL' "$RUNTIME"
require 'RemoteGoalCoordinator' "$RUNTIME"
require 'approveRemoteGoalPresentation' "$RUNTIME"
require 'declineRemoteGoalPresentation' "$RUNTIME"
require 'cancelRemoteGoalPresentation' "$RUNTIME"
require 'RuntimeSurfaceAction.APPROVE' "$RUNTIME"
require 'RuntimeSurfaceAction.CANCEL' "$RUNTIME"
require 'runtime::approvePresentation' "$MAIN"
require 'runtime::cancelPresentation' "$MAIN"

if grep -Eiq 'taskId\(\).*respondToApproval|respondToApproval\([^,]+,[[:space:]]*[^,]*task' "$COORDINATOR" "$RUNTIME"; then
  echo 'Android must not substitute task_id for approval_id' >&2
  exit 1
fi
if grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt|management plane' "$CLIENT" "$COORDINATOR" "$RUNTIME" "$MAIN"; then
  echo 'Backend/provider jargon leaked into JARVIS approval surface' >&2
  exit 1
fi

echo 'Remote goal approval discovery contract GREEN'
