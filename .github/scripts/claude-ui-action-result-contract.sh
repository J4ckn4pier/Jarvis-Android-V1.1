#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"

fail() {
  echo "Claude UI action-result contract FAILED: $1" >&2
  exit 1
}

grep -Fq '@JavascriptInterface' "$ROUTER" || fail "router must expose JavaScript interfaces"
grep -Fq 'public String actionWithResult(String action)' "$ROUTER" || fail "missing structured actionWithResult bridge"
grep -Fq '"accepted":false' "$ROUTER" || fail "unsupported actions must return an explicit rejected result"
grep -Fq '"accepted":true' "$ROUTER" || fail "supported actions must return an explicit accepted result"
grep -Fq '"action":"' "$ROUTER" || fail "result must identify the requested action"
grep -Fq 'activity.runOnUiThread(() -> dispatch(action));' "$ROUTER" || fail "accepted actions must still dispatch through the production UI thread"

echo "Claude UI action-result contract GREEN"
