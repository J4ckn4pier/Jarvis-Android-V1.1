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

# Exact M13 provider-neutral routes and auth boundary.
require '"/v1/goals"' "$CLIENT"
require '"/v1/projects/"' "$CLIENT"
require '"/events"' "$CLIENT"
require '"/approvals/"' "$CLIENT"
require '"/cancel"' "$CLIENT"
require '"/result"' "$CLIENT"
require 'Authorization' "$CLIENT"
require 'Bearer ' "$CLIENT"

# Exact request/reconnect fields. Cursor is a query parameter, so its source literal includes '='.
for field in goal session_id constraints acceptance_criteria deadline approved response; do
  require "\"$field\"" "$CLIENT"
done
require 'after_event_id=' "$CLIENT"

# Public responses must remain provider-neutral and explicitly reject accidental provider exposure.
require 'provider_details_exposed' "$CLIENT"
if grep -Eiq 'agent[ _-]?zero|valkey|ollama|anthropic|openai|claude|chatgpt' "$CLIENT" "$STATE"; then
  echo "Provider/worker implementation name leaked into Android remote-goal boundary" >&2
  exit 1
fi

echo "Remote goal client contract GREEN"
