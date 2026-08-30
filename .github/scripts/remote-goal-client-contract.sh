#!/bin/sh
set -eu

CLIENT="android/app/src/main/java/com/jarvis/mobile/remote/RemoteGoalClient.java"
STATE="android/app/src/main/java/com/jarvis/mobile/remote/RemoteGoalStateStore.java"

[ -f "$CLIENT" ] || { echo "RED: missing $CLIENT" >&2; exit 1; }
[ -f "$STATE" ] || { echo "RED: missing $STATE" >&2; exit 1; }

require() {
  needle="$1"
  file="$2"
  grep -F "$needle" "$file" >/dev/null || { echo "Missing contract token '$needle' in $file" >&2; exit 1; }
}

for token in '"/v1/goals"' '"/v1/projects/"' '"/events"' '"/approvals/"' '"/cancel"' '"/result"' Authorization 'Bearer '; do require "$token" "$CLIENT"; done
for field in goal session_id constraints acceptance_criteria deadline approved response; do require "\"$field\"" "$CLIENT"; done
require 'after_event_id=' "$CLIENT"

# M13 identifiers are opaque exact strings. Default session only when absent; never rewrite a supplied value.
require 'sessionId == null ? "primary" : sessionId' "$CLIENT"
if grep -Fq 'sessionId.isBlank() ? "primary"' "$CLIENT"; then echo 'Supplied session_id must not be normalized to primary' >&2; exit 1; fi

# Credential handoff must use Android protected key material; never persist the bearer token as plaintext.
require 'AndroidKeyStore' "$STATE"
require 'AES/GCM/NoPadding' "$STATE"
require 'saveConnection' "$STATE"
require 'loadConnection' "$STATE"
if grep -Eq 'putString\([^,]*TOKEN[^,]*,[[:space:]]*token\)' "$STATE"; then echo 'Bearer token must not be stored as plaintext SharedPreferences' >&2; exit 1; fi

require 'provider_details_exposed' "$CLIENT"
if grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt' "$CLIENT" "$STATE"; then echo "Provider/worker implementation name leaked into Android remote-goal boundary" >&2; exit 1; fi

echo "Remote goal client contract GREEN"
