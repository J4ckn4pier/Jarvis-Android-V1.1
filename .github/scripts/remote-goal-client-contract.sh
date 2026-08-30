#!/bin/sh
set -eu

CLIENT="android/app/src/main/java/com/jarvis/mobile/remote/RemoteGoalClient.java"
STATE="android/app/src/main/java/com/jarvis/mobile/remote/RemoteGoalStateStore.java"
RUNTIME="android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"

for file in "$CLIENT" "$STATE" "$RUNTIME"; do [ -f "$file" ] || { echo "RED: missing $file" >&2; exit 1; }; done
require() { needle="$1"; file="$2"; grep -F "$needle" "$file" >/dev/null || { echo "Missing contract token '$needle' in $file" >&2; exit 1; }; }

for token in '"/v1/goals"' '"/v1/projects/"' '"/events"' '"/approvals/"' '"/cancel"' '"/result"' Authorization 'Bearer '; do require "$token" "$CLIENT"; done
for field in goal session_id constraints acceptance_criteria deadline approved response; do require "\"$field\"" "$CLIENT"; done
require 'after_event_id=' "$CLIENT"
require 'sessionId == null ? "primary" : sessionId' "$CLIENT"
if grep -Fq 'sessionId.isBlank() ? "primary"' "$CLIENT"; then echo 'Supplied session_id must not be normalized to primary' >&2; exit 1; fi

require 'AndroidKeyStore' "$STATE"
require 'AES/GCM/NoPadding' "$STATE"
require 'saveConnection' "$STATE"
require 'loadConnection' "$STATE"
if grep -Eq 'putString\([^,]*TOKEN[^,]*,[[:space:]]*token\)' "$STATE"; then echo 'Bearer token must not be stored as plaintext SharedPreferences' >&2; exit 1; fi

# Production wiring: only reasoning-required work reaches the remote router; transport failure falls back locally.
require 'RemoteGoalStateStore' "$RUNTIME"
require 'loadConnection()' "$RUNTIME"
require 'RemoteGoalClient' "$RUNTIME"
require 'submitGoal' "$RUNTIME"
require 'saveProject' "$RUNTIME"
require 'localReasoning' "$RUNTIME"

require 'provider_details_exposed' "$CLIENT"
if grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt' "$CLIENT" "$STATE" "$RUNTIME"; then echo "Provider/worker implementation name leaked into Android remote-goal boundary" >&2; exit 1; fi

echo "Remote goal client contract GREEN"
