#!/bin/sh
set -eu

ROOT="android/app/src/main/java/com/jarvis/mobile/remote"
CLIENT="$ROOT/RemoteGoalClient.java"
HTTP="$ROOT/HttpRemoteGoalClient.java"

# APK-facing contract must exist only in Android/application code and stay provider-neutral.
test -f "$CLIENT"
test -f "$HTTP"

grep -q 'interface RemoteGoalClient' "$CLIENT"
for method in 'submit(' 'project(' 'events(' 'approve(' 'cancel(' 'result('; do
  grep -Fq "$method" "$CLIENT"
done

# Exact M13 management routes/auth boundary. Do not loosen these to provider-specific APIs.
grep -Fq '/v1/goals' "$HTTP"
grep -Fq '/v1/projects/' "$HTTP"
grep -Fq '/events' "$HTTP"
grep -Fq '/approvals/' "$HTTP"
grep -Fq '/cancel' "$HTTP"
grep -Fq '/result' "$HTTP"
grep -Fq 'Authorization' "$HTTP"
grep -Fq 'Bearer ' "$HTTP"

# Stable public vocabulary and reconnect cursor must be represented by the Android client.
for field in project_id session_id state goal task_count task_states last_progress_at event_id kind task_id timestamp next_event_id has_more result approval_id approved response; do
  grep -Fq "$field" "$HTTP"
done

# Public responses that expose the guard field must fail closed if backend internals leak.
grep -Fq 'provider_details_exposed' "$HTTP"
grep -Fq 'Provider details leaked through the JARVIS management contract' "$HTTP"

# The phone client must never bind itself to worker/provider implementation names.
! grep -Eiq 'agent[ -]?zero|ollama|claude|chatgpt|valkey|sandbox' "$CLIENT" "$HTTP"

echo 'RemoteGoalClientContract: PASS'
