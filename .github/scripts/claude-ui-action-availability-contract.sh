#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"

fail() {
  echo "Claude UI action-availability contract FAILED: $1" >&2
  exit 1
}

grep -Fq 'public String actionAvailability(String action)' "$ROUTER" || fail "missing structured availability bridge for visible controls"
grep -Fq '\"enabled\":false' "$ROUTER" || fail "unavailable controls must be explicitly disabled"
grep -Fq '\"enabled\":true' "$ROUTER" || fail "available controls must be explicitly enabled"
grep -Fq '\"reason\":\"unsupported\"' "$ROUTER" || fail "unsupported controls must explain why"
grep -Fq '\"reason\":\"audio_service_unavailable\"' "$ROUTER" || fail "media controls must truthfully report missing AudioManager"
grep -Fq '\"reason\":\"assistant_role_unavailable\"' "$ROUTER" || fail "default-assistant control must report unavailable assistant role"
grep -Fq '\"reason\":\"already_default_assistant\"' "$ROUTER" || fail "default-assistant control must report when no action is needed"
grep -Fq 'String availability = actionAvailability(action);' "$ROUTER" || fail "actionWithResult must consult availability before queueing"
grep -Fq 'if (!availability.contains(' "$ROUTER" || fail "disabled controls must not be queued"
grep -Fq '\"reason\":\"unavailable\"' "$ROUTER" || fail "rejected environment-dependent actions must be reported as unavailable"

echo "Claude UI action-availability contract GREEN"
