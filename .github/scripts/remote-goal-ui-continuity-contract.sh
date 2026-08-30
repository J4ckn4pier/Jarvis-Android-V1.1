#!/bin/sh
set -eu

RUNTIME="android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"
MAIN="android/app/src/main/java/com/jarvis/mobile/MainActivity.java"

require() {
  needle="$1"
  file="$2"
  grep -F "$needle" "$file" >/dev/null || { echo "Missing remote UI continuity token '$needle' in $file" >&2; exit 1; }
}

# Ordinary app resume asks the shared Android runtime for provider-neutral remote progress.
require 'resumeRemoteGoalPresentation' "$RUNTIME"
require 'RemoteGoalCoordinator' "$RUNTIME"
require 'resumeActiveProject()' "$RUNTIME"
require 'onStart()' "$MAIN"
require 'runtime.resumeRemoteGoalPresentation()' "$MAIN"
require 'submitRemoteResumeCheck' "$MAIN"
require 'deliverPresentation' "$MAIN"

# User-facing status must stay JARVIS language, not backend/project plumbing.
if grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt|management plane' "$RUNTIME" "$MAIN"; then
  echo "Backend/provider jargon leaked into ordinary JARVIS UI continuity" >&2
  exit 1
fi

# Approval remains blocked until the backend exposes a real public approval_id.
if grep -F 'respondToApproval' "$RUNTIME" "$MAIN" >/dev/null; then
  echo "Remote approval was wired before public approval_id evidence" >&2
  exit 1
fi

echo "Remote goal UI continuity contract GREEN"
